package app.votify.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.Image
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Standalone (serverless) music source built on NewPipeExtractor — the same engine that powers
 * the NewPipe app. Search, stream URLs and playlist imports are fetched directly from
 * YouTube / YouTube Music / SoundCloud, and lyrics come straight from lrclib.net.
 *
 * All functions are **synchronous** (blocking network); callers wrap them in Dispatchers.IO.
 * [resolveAudioUrl] is additionally used from ExoPlayer's loading thread via ResolvingDataSource.
 */
object EmbeddedMusicSource {

    /** Offline downloads directory (set from VotifyApp). */
    @Volatile
    var downloadsDir: java.io.File? = null

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** YouTube stream URLs live ~6h — cache 4h to be safe. */
    private const val STREAM_TTL_MS = 4 * 60 * 60 * 1000L

    /** Tracks shorter/longer than this are noise: intros, livestreams, hour-long mixes. */
    private const val MIN_TRACK_SECONDS = 30L
    private const val MAX_TRACK_SECONDS = 15 * 60L
    private const val MAX_WAVE_SECONDS = 10 * 60L

    /** Import caps: no intros in playlists, and never fetch more than this many items. */
    private const val MIN_IMPORT_SECONDS = 15L
    private const val MAX_IMPORT_ITEMS = 500

    private val streamCache = ConcurrentHashMap<String, CachedUrl>()

@Volatile
private var trendingCache: Pair<Long, List<Track>>? = null

private const val TRENDING_TTL_MS = 10 * 60 * 1000L

    private data class CachedUrl(val url: String, val at: Long)

    @Volatile
    private var initialized = false

    fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader(http), Localization("ru", "UA"), ContentCountry("UA"))
            initialized = true
        }
    }

    // ------------------------------------------------------------------ search

    /**
     * YouTube search. `music = true` merges the YouTube Music catalog (clean "artist"
     * metadata, songs only) with a regular YouTube video search: amateur releases that
     * never made it into the Music catalog (e.g. «Отчим» — KSB muzic) exist only as
     * plain uploads, so without the merge they are unfindable.
     */
    fun search(query: String, limit: Int = 24, music: Boolean = true): List<Track> {
        ensureInit()
        if (!music) return runCatching { rawSearch(query, music = false, limit) }.getOrDefault(emptyList())
        val songs = runCatching { rawSearch(query, music = true, limit) }.getOrDefault(emptyList())
        val videos = runCatching { rawSearch(query, music = false, limit) }.getOrDefault(emptyList())
        return (songs + videos)
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .take(limit)
    }

    /** Popular tracks of an artist (YouTube Music songs search). */
    fun artistTracks(artist: String, limit: Int = 50): List<Track> {
        ensureInit()
        return runCatching { rawSearch(artist, music = true, limit) }.getOrDefault(emptyList())
    }

    private fun rawSearch(query: String, music: Boolean, limit: Int): List<Track> {
        val service = ServiceList.YouTube
        val handler = if (music) {
            service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        } else {
            service.searchQHFactory.fromQuery(query)
        }
        return SearchInfo.getInfo(service, handler)
            .relatedItems
            .filterIsInstance<StreamInfoItem>()
            // Keep real songs only: drop livestreams, mixes and podcasts (30 s … 15 min).
            .filter { it.duration in MIN_TRACK_SECONDS..MAX_TRACK_SECONDS }
            .mapNotNull { it.toTrack() }
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .take(limit)
    }

    // ------------------------------------------------------------------ wave / recommendations

    /**
     * «Моя волна» without a server: search a few songs of every seed artist, anchor with
     * track seeds, drop everything the user heard recently.
     */
    fun wave(artistSeeds: List<String>, trackSeeds: List<String>, exclude: Set<String>, limit: Int): List<Track> {
        ensureInit()
        val excludeSet = exclude.toSet()
        val collected = mutableListOf<Track>()
        // «Максимально похожие треки» = songs of the artists the user actually plays,
        // collects and playlists. Deep seeds, a handful of tracks per artist.
        artistSeeds.take(6).filter { it.isNotBlank() && !it.equals("Unknown", true) }.forEach { artist ->
            runCatching { rawSearch(artist, music = true, 6) }.getOrNull()?.let { collected += it }
        }
        trackSeeds.take(3).filter { it.isNotBlank() }.forEach { seed ->
            runCatching { rawSearch(seed, music = true, 3) }.getOrNull()?.let { collected += it }
        }
        if (collected.isEmpty()) return recommendations(limit)
        return collected
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .filter { it.id !in excludeSet && it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
            .shuffled()
            .take(limit)
    }

    /**
     * «В тренде — СНГ»: current top tracks of the artists actually charting in the CIS
     * (each artist's top songs on YouTube Music are demand-ranked, i.e. what is really
     * listened to). Interleaved round-robin so the list stays varied; cached 10 min.
     */
    fun trendingCis(limit: Int = 50): List<Track> {
        ensureInit()
        val cached = trendingCache
        if (cached != null && System.currentTimeMillis() - cached.first < TRENDING_TTL_MS) {
            return cached.second.take(limit)
        }
        val artists = listOf(
            "INSTASAMKA", "ANNA ASTI", "Мари Краймбрери", "Тима Белорусских",
            "Miyagi & Andy Panda", "Скриптонит", "GONE.Fludd", "Мот",
            "JONY", "NILE", "Люся Чеботина", "A.V.G",
            "ЛСП", "Дора", "Элджей", "Пошлая Молли",
        )
        val perArtist = artists.map { artist ->
            runCatching { rawSearch(artist, music = true, 5).take(4) }.getOrDefault(emptyList())
        }
        // Round-robin interleave: no artist hogs the top of the chart.
        val interleaved = mutableListOf<Track>()
        (0 until 4).forEach { i -> perArtist.forEach { page -> page.getOrNull(i)?.let { interleaved += it } } }
        val result = interleaved
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .filter { it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
        if (result.isNotEmpty()) trendingCache = System.currentTimeMillis() to result
        return if (result.size >= 6) result.take(limit) else result + recommendations(limit)
    }

    fun recommendations(limit: Int): List<Track> {
        ensureInit()
        val queries = listOf("top hits", "pop hits 2026", "best songs")
        return queries.asSequence()
            .map { q -> runCatching { rawSearch(q, music = true, limit + 6) }.getOrDefault(emptyList()) }
            .flatten()
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .shuffled()
            .take(limit)
            .toList()
    }

    // ------------------------------------------------------------------ offline downloads

    /** File the track is downloaded to, or null when storage is not set up. */
    fun downloadFileFor(trackId: String): java.io.File? =
        downloadsDir?.let { java.io.File(it, downloadName(trackId) + ".audio") }

    private fun downloadName(trackId: String): String =
        if (trackId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) trackId
        else "u_" + Integer.toHexString(trackId.hashCode()) + "_" + trackId.length

    fun isDownloaded(trackId: String): Boolean =
        downloadFileFor(trackId)?.let { it.exists() && it.length() > 0 } == true

    /** Count + total size of offline copies (for the storage screen). */
    fun downloadStats(): Pair<Int, Long> {
        val dir = downloadsDir ?: return 0 to 0L
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".audio") } ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }

    fun deleteDownload(trackId: String) {
        downloadFileFor(trackId)?.takeIf { it.exists() }?.delete()
    }

    /**
     * Download a track for offline listening: resolve the stream URL, fetch the bytes into
     * a temp file, then atomically move it into place. Existing copies are reused as-is.
     */
    fun downloadTrack(trackId: String, quality: AudioQuality): java.io.File {
        ensureInit()
        val dir = downloadsDir ?: throw IOException("Хранилище недоступно")
        dir.mkdirs()
        val target = java.io.File(dir, downloadName(trackId) + ".audio")
        if (target.exists() && target.length() > 0) return target
        val url = resolveAudioUrl(trackId, quality)
        val request = OkHttpRequest.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val tmp = java.io.File(dir, downloadName(trackId) + ".tmp")
            runCatching {
                resp.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Пустой ответ")
            }.onFailure { tmp.delete(); throw IOException("Не удалось сохранить файл") }
            if (tmp.length() == 0L) {
                tmp.delete()
                throw IOException("Пустой файл")
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Не удалось сохранить файл")
            }
        }
        return target
    }

    // ------------------------------------------------------------------ streaming

    /**
     * Direct audio URL for a track id. YouTube ids are 11-char video ids; SoundCloud tracks
     * use their page URL as the id. Blocking; cached with a TTL because googlevideo links expire.
     */
    fun resolveAudioUrl(trackId: String, quality: AudioQuality): String {
        ensureInit()
        if (trackId.startsWith("sc_")) {
            // Legacy id format produced by the PC server — resolvable only through that server.
            throw IOException("SoundCloud id «$trackId» требует сервер Votify")
        }
        val cacheKey = "$trackId|${quality.key}"
        val cached = streamCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.at < STREAM_TTL_MS) return cached.url

        val pageUrl = if (trackId.startsWith("http")) trackId else "https://www.youtube.com/watch?v=$trackId"
        val info = StreamInfo.getInfo(NewPipe.getServiceByUrl(pageUrl), pageUrl)
        val url = pickAudioStream(info.audioStreams, quality)?.getContent()
            ?: throw IOException("Не удалось получить аудиопоток")
        streamCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
        return url
    }

    /** Warm the cache for upcoming tracks (called from a background coroutine). */
    fun preload(ids: List<String>, quality: AudioQuality) {
        ids.take(3).forEach { id -> runCatching { resolveAudioUrl(id, quality) } }
    }

    private fun pickAudioStream(streams: List<AudioStream>, quality: AudioQuality): AudioStream? {
        val progressive = streams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val byBitrate = progressive.sortedBy { it.averageBitrate }
        val known = byBitrate.filter { it.averageBitrate > 0 }.ifEmpty { byBitrate }
        return when (quality) {
            AudioQuality.Low -> known.firstOrNull { it.averageBitrate >= 48_000 } ?: known.firstOrNull()
            AudioQuality.Medium -> known.firstOrNull { it.averageBitrate >= 96_000 } ?: known.lastOrNull()
            AudioQuality.High -> known.lastOrNull()
        } ?: progressive.firstOrNull()
    }

    // ------------------------------------------------------------------ lyrics

    /** lrclib.net — the same source the server used behind /api/lyrics. */
    fun lyrics(track: String, artist: String): LyricsResponse {
        val get = "https://lrclib.net/api/get?track_name=${encode(track)}&artist_name=${encode(artist)}"
        runCatching { httpGetString(get) }.getOrNull()?.let { body ->
            runCatching { json.decodeFromString<LrcLibTrack>(body) }.getOrNull()
                ?.takeIf { it.syncedLyrics != null || it.plainLyrics != null }
                ?.let { return it.toResponse(track, artist) }
        }
        val search = "https://lrclib.net/api/search?track_name=${encode(track)}&artist_name=${encode(artist)}"
        runCatching { httpGetString(search) }.getOrNull()?.let { body ->
            runCatching { json.decodeFromString<List<LrcLibTrack>>(body) }.getOrNull()
                ?.firstOrNull()
                ?.let { return it.toResponse(track, artist) }
        }
        return LyricsResponse(syncedLyrics = null, plainLyrics = null, track = track, artist = artist)
    }

    @kotlinx.serialization.Serializable
    private data class LrcLibTrack(
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
    )

    private fun LrcLibTrack.toResponse(track: String, artist: String) = LyricsResponse(
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        track = trackName ?: track,
        artist = artistName ?: artist,
    )

    // ------------------------------------------------------------------ playlist import

    /** YouTube / SoundCloud playlists via NewPipe, Spotify playlists via its embed page. */
    suspend fun importPlaylist(url: String): ImportedTracks {
        ensureInit()
        return when {
            "soundcloud.com" in url -> withContext(Dispatchers.IO) { importSoundcloud(url) }
            "spotify.com" in url -> importSpotify(url)
            else -> withContext(Dispatchers.IO) { importYoutube(url) }
        }
    }

    private fun importYoutube(url: String): ImportedTracks {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = allPlaylistPages(ServiceList.YouTube, url, info.relatedItems, info.nextPage)
        val tracks = items.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) throw IOException("Плейлист пуст или недоступен")
        return ImportedTracks(name = info.name, tracks = tracks)
    }

    private fun importSoundcloud(url: String): ImportedTracks {
        val info = PlaylistInfo.getInfo(ServiceList.SoundCloud, url)
        val items = allPlaylistPages(ServiceList.SoundCloud, url, info.relatedItems, info.nextPage)
        val tracks = items.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) throw IOException("Плейлист пуст или недоступен")
        return ImportedTracks(name = info.name, tracks = tracks)
    }

    /** The first page holds ~100 items — walk every next page so long playlists import fully. */
    private fun allPlaylistPages(
        service: org.schabi.newpipe.extractor.StreamingService,
        url: String,
        first: List<StreamInfoItem>,
        firstNextPage: org.schabi.newpipe.extractor.Page?,
    ): List<StreamInfoItem> {
        val items = first.toMutableList()
        var page = firstNextPage
        var guarded = 0
        while (page != null && items.size < MAX_IMPORT_ITEMS && guarded++ < 60) {
            val more = runCatching { PlaylistInfo.getMoreItems(service, url, page) }.getOrNull() ?: break
            items += more.items.filterIsInstance<StreamInfoItem>()
            page = more.nextPage
        }
        return items
            .distinctBy { it.url }
            .filter { it.duration in MIN_IMPORT_SECONDS..MAX_TRACK_SECONDS }
    }

    /**
     * Spotify has no public API for playlist tracks, so (like the PC server) we read the
     * playlist's embed page and resolve each title on YouTube Music.
     */
    suspend fun importSpotify(url: String): ImportedTracks = withContext(Dispatchers.IO) {
        val playlistId = Regex("playlist/([a-zA-Z0-9]+)").find(url)?.groupValues?.get(1)
            ?: throw IOException("Некорректная ссылка Spotify")
        val html = httpGetString("https://open.spotify.com/embed/playlist/$playlistId")
            ?: throw IOException("Не удалось загрузить страницу плейлиста Spotify")
        val data = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""")
            .find(html)?.groupValues?.get(1)
            ?: throw IOException("Не удалось извлечь треки из Spotify")
        val entries = parseSpotifyTrackList(data)
        if (entries.isEmpty()) throw IOException("Не удалось извлечь треки из Spotify")

        // Resolve in small sequential batches so we don't hammer YouTube with 100 parallel calls.
        val resolved = mutableListOf<Track>()
        entries.take(MAX_IMPORT_ITEMS).chunked(5).forEach { batch ->
            coroutineScope {
                batch.map { (title, artist) ->
                    async(Dispatchers.IO) {
                        // YT Music first; fall back to a regular YouTube search (videos play fine too).
                        runCatching { rawSearch("$artist $title", music = true, 1).firstOrNull() }
                            .getOrNull()
                            ?: runCatching { rawSearch("$artist $title", music = false, 1).firstOrNull() }
                                .getOrNull()
                    }
                }.awaitAll().forEach { track ->
                    if (track != null) {
                        synchronized(resolved) { resolved += track }
                    }
                }
            }
        }
        if (resolved.isEmpty()) throw IOException("По трекам Spotify ничего не найдено")
        ImportedTracks(
            name = parseSpotifyName(data) ?: "Spotify плейлист",
            tracks = resolved.distinctBy { it.id },
        )
    }

    /** (title, artist) pairs from the embed page's __NEXT_DATA__ JSON. */
    private fun parseSpotifyTrackList(nextData: String): List<Pair<String, String>> {
        val root = runCatching { json.parseToJsonElement(nextData).jsonObject }.getOrNull() ?: return emptyList()
        val candidates = listOf(
            listOf("props", "pageProps", "state", "data", "entity", "trackList"),
            listOf("props", "pageProps", "state", "data", "playlist", "trackList"),
        )
        for (path in candidates) {
            var node: kotlinx.serialization.json.JsonElement? = root
            for (key in path) {
                node = (node as? JsonObject)?.get(key) ?: break
            }
            val list = (node as? kotlinx.serialization.json.JsonElement)?.let { el ->
                runCatching { el.jsonArray }.getOrNull()
            } ?: continue
            val entries = list.mapNotNull { item ->
                val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val artist = obj["subtitle"]?.jsonPrimitive?.content ?: ""
                title to artist
            }
            if (entries.isNotEmpty()) return entries
        }
        return emptyList()
    }

    private fun parseSpotifyName(nextData: String): String? = runCatching {
        json.parseToJsonElement(nextData).jsonObject
            .get("props")!!.jsonObject["pageProps"]!!.jsonObject
            .get("state")!!.jsonObject["data"]!!.jsonObject
            .get("entity")!!.jsonObject["name"]!!.jsonPrimitive.content
    }.getOrNull()

    // ------------------------------------------------------------------ helpers

    private fun StreamInfoItem.toTrack(): Track? {
        val id = extractId(url) ?: return null
        return Track(
            id = id,
            title = name ?: "",
            artist = uploaderName?.trim().orEmpty().ifBlank { "Unknown" },
            cover = bestImage(thumbnails),
            url = url,
            duration = duration.toInt(),
        )
    }

    /** YouTube page URL → 11-char id; SoundCloud URL is its own id. */
    private fun extractId(pageUrl: String): String? = when {
        pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be") ->
            Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(pageUrl)?.groupValues?.get(1)
                ?: Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(pageUrl)?.groupValues?.get(1)
        else -> pageUrl
    }

    private fun bestImage(images: List<Image>): String =
        images.maxByOrNull { (it.width.toLong()) * (it.height.toLong()) }?.url ?: ""

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun httpGetString(url: String): String? {
        val request = OkHttpRequest.Builder()
            .url(url)
            .header("User-Agent", "Votify Android (https://github.com/exieeez/Votify)")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    // ------------------------------------------------------------------ NewPipe downloader

    /** Bridges NewPipeExtractor to OkHttp (the library itself ships no HTTP client). */
    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

        override fun execute(request: Request): Response {
            val builder = OkHttpRequest.Builder().url(request.url())
            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }
            when (request.httpMethod()) {
                "HEAD" -> builder.head()
                "POST" -> builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody(null))
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 429) {
                    throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                }
                return Response(
                    resp.code,
                    resp.message,
                    resp.headers.toMultimap(),
                    resp.body?.string().orEmpty(),
                    request.url(),
                )
            }
        }
    }
}
