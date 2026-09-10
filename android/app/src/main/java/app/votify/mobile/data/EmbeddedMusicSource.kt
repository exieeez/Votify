package app.votify.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    /**
     * Отдельный клиент для скачивания: пул побольше (качаем несколькими соединениями
     * сразу) и более длинный таймаут чтения — долгий трек на слабой сети не должен
     * обрываться на середине.
     */
    private val downloadHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .build()

    /** YouTube stream URLs live ~6h — cache 4h to be safe. */
    private const val STREAM_TTL_MS = 4 * 60 * 60 * 1000L

    /** Сколько параллельных соединений качает один трек (HTTP Range). */
    private const val MAX_CHUNKS = 4
    /** Мелкие файлы нарезать бессмысленно — только лишние запросы. */
    private const val CHUNK_MIN_SIZE = 320L * 1024L

    /** Живой чарт Apple Music (Россия): что слушают прямо сейчас, без ключа и регистрации. */
    private const val CHART_URL = "https://rss.marketingtools.apple.com/api/v2/ru/music/most-played/50/songs.json"

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

private const val TRENDING_TTL_MS = 30 * 60 * 1000L

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
        val blocked = exclude.toMutableSet()

        // 1. Якоря — недавние и любимые треки: по одному поиску на трек, параллельно.
        val anchors = fanOut(trackSeeds.take(6).filter { it.isNotBlank() }, limit = 3) { seed ->
            rawSearch(seed, music = true, 1)
        }.distinctBy { it.id }
        blocked += anchors.map { it.id }

        // 2. Похожее: блок «Далее» у каждого якоря отдельно. Трек, который всплыл у
        //    нескольких якорей, правда похож — считаем, сколько раз он встретился.
        val anchorIds = anchors.map { it.id }.distinct().take(4)
        val perAnchor: List<List<Track>> = if (anchorIds.isEmpty()) emptyList() else runBlocking {
            coroutineScope {
                anchorIds.map { id ->
                    async(Dispatchers.IO) { runCatching { relatedTracks(id, 12) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }
        }
        val hits = mutableMapOf<String, Int>()
        perAnchor.forEach { list -> list.distinctBy { it.id }.forEach { t -> hits[t.id] = (hits[t.id] ?: 0) + 1 } }
        val related = perAnchor.flatten()

        // 3. Свежие треки артистов из истории — только чтобы добить хвост, если
        //    «похожего» набралось мало.
        val fromArtists = fanOut(
            artistSeeds.take(4).filter { it.isNotBlank() && !it.equals("Unknown", true) },
            limit = 4,
        ) { artist -> rawSearch(artist, music = true, 6) }

        if (related.isEmpty() && fromArtists.isEmpty()) return recommendations(limit)

        val seedArtists = artistSeeds.map { normalizeArtist(it) }.filter { it.isNotBlank() }.toSet()
        val anchorArtists = anchors.map { normalizeArtist(it.artist) }.filter { it.isNotBlank() }.toSet()
        val anchorWords = anchors.flatMap { significantWords(it.title) }.toSet()

        val ranked = (related + fromArtists)
            .asSequence()
            .distinctBy { it.id }
            .distinctBy { dedupeKey(it) }
            .filter { it.id !in blocked && it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
            .map { track -> track to relevance(track, anchorArtists, seedArtists, anchorWords, hits[track.id] ?: 0) }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
            .take(limit)

        if (ranked.isEmpty()) return recommendations(limit)
        // Подряд идущие треки одного артиста разводим, порядок по похожести сохраняем.
        return spreadByArtist(ranked)
    }

    /** Насколько трек близок к тому, что человек реально слушает (больше — лучше). */
    private fun relevance(
        track: Track,
        anchorArtists: Set<String>,
        seedArtists: Set<String>,
        anchorWords: Set<String>,
        hits: Int,
    ): Double {
        val artist = normalizeArtist(track.artist)
        var score = 0.0
        if (artist in anchorArtists) score += 6.0
        else if (anchorArtists.any { artist.contains(it) || it.contains(artist) }) score += 4.0
        if (artist in seedArtists) score += 2.5
        else if (seedArtists.any { artist.contains(it) || it.contains(artist) }) score += 1.5
        if (significantWords(track.title).any { it in anchorWords }) score += 0.75
        score += hits * 1.5
        // Немного случайности, чтобы волна не залипала на одном и том же наборе.
        return score + Math.random()
    }

    /** Round-robin по артистам: подряд не идут пять треков одного исполнителя. */
    private fun spreadByArtist(tracks: List<Track>): List<Track> {
        val groups = linkedMapOf<String, MutableList<Track>>()
        tracks.forEach { track -> groups.getOrPut(normalizeArtist(track.artist)) { mutableListOf() } += track }
        val queues = groups.values.toList()
        val out = mutableListOf<Track>()
        var index = 0
        while (out.size < tracks.size) {
            var added = false
            for (queue in queues) {
                if (index < queue.size) {
                    out += queue[index]
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return out
    }

    private fun dedupeKey(track: Track): String = "${normalizeArtist(track.artist)}|${track.title.lowercase()}"

    /** «Artist Name», «artist-name», «ARTIST» — один и тот же артист. */
    private fun normalizeArtist(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}& ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun significantWords(title: String): Set<String> =
        title.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()

    /**
     * Выполняет [block] для каждого элемента на пуле IO (не более [limit] одновременно)
     * и склеивает результаты. Ошибки проглатываются: волна и чарт должны жить, даже
     * если один из запросов не прошёл.
     */
    private fun <T> fanOut(items: List<T>, limit: Int = 6, block: (T) -> List<Track>): List<Track> {
        if (items.isEmpty()) return emptyList()
        return runBlocking {
            coroutineScope {
                items.chunked(limit).flatMap { batch ->
                    batch.map { item -> async(Dispatchers.IO) { runCatching { block(item) }.getOrDefault(emptyList()) } }
                        .awaitAll()
                        .flatten()
                }
            }
        }
    }

    /** То же, что [fanOut], но результат каждого элемента остаётся отдельным списком. */
    private fun <T, R : Any> mapParallel(items: List<T>, limit: Int = 6, block: (T) -> R): List<R> {
        if (items.isEmpty()) return emptyList()
        return runBlocking {
            coroutineScope {
                items.chunked(limit).flatMap { batch ->
                    batch.map { item -> async(Dispatchers.IO) { runCatching { block(item) }.getOrNull() } }.awaitAll()
                }
            }
        }.filterNotNull()
    }

    /**
     * По-настоящему похожие треки: блок «Далее» у видео-якоря (YouTube related).
     * Поиск по имени артиста даёт случайную популярщину, а related — близкую по звуку музыку.
     * Пусто, если related-блок недоступен (тогда волна работает по-старому).
     */
    fun relatedTracks(trackId: String, limit: Int = 6): List<Track> {
        if (trackId.isBlank() || trackId.startsWith("sc_") || trackId.startsWith("http")) return emptyList()
        ensureInit()
        return runCatching {
            val pageUrl = "https://www.youtube.com/watch?v=$trackId"
            val extractor = NewPipe.getServiceByUrl(pageUrl).getStreamExtractor(pageUrl)
            extractor.fetchPage()
            extractor.getRelatedItems()?.items
                ?.filterIsInstance<StreamInfoItem>()
                .orEmpty()
                .filter { it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
                .mapNotNull { it.toTrack() }
                .distinctBy { it.id }
                .distinctBy { "${it.artist}|${it.title}".lowercase() }
                .take(limit)
        }.getOrDefault(emptyList())
    }

    /**
     * «В тренде»: живой чарт Apple Music (Россия) — то, что действительно слушают прямо
     * сейчас. Позиции чарта ищем на YouTube Music параллельно и сохраняем порядок:
     * первая строчка чарта остаётся первой в списке. Кэш 30 минут.
     *
     * Если чарт недоступен — запасной путь: свежие запросы и хиты актуальных артистов.
     */
    fun trendingCis(limit: Int = 50): List<Track> {
        ensureInit()
        val cached = trendingCache
        if (cached != null && System.currentTimeMillis() - cached.first < TRENDING_TTL_MS) {
            return cached.second.take(limit)
        }

        // 1. Чарт: сверху вниз по реальной популярности.
        val chart = chartEntries(60)
        val chartTracks = fanOut(chart, limit = 6) { (artist, title) ->
            rawSearch(if (artist.isBlank()) title else "$artist $title", music = true, 1)
        }
            .distinctBy { it.id }
            .distinctBy { dedupeKey(it) }
            .filter { it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
            .take(limit)

        if (chartTracks.size >= 10) {
            trendingCache = System.currentTimeMillis() to chartTracks
            return chartTracks
        }

        // 2. Запасной путь: свежие запросы + хиты артистов, которые сейчас в ротации
        //    (round-robin, чтобы один артист не забивал весь верх списка).
        val artists = listOf(
            "ONDA ANDAR", "XOLIDAYBOY", "Nasty Babe", "Jakone", "ICEGERGERT", "Kamazz",
            "Три дня дождя", "ANNA ASTI", "Zivert", "Artik & Asti", "VERBEE", "Клава Кока",
            "JONY", "Ay Yola", "Баста", "Мари Краймбрери",
        )
        val freshQueries = listOf("хиты 2026", "популярное сейчас 2026", "новинки музыки 2026", "тренды музыки 2026")
        val fresh = fanOut(freshQueries, limit = 4) { q -> rawSearch(q, music = true, 12) }
        val perArtist = mapParallel(artists, limit = 4) { artist -> rawSearch(artist, music = true, 5).take(4) }
        val interleaved = mutableListOf<Track>()
        (0 until 4).forEach { i -> perArtist.forEach { page -> page.getOrNull(i)?.let { interleaved += it } } }
        val result = (chartTracks + fresh + interleaved)
            .distinctBy { it.id }
            .distinctBy { dedupeKey(it) }
            .filter { it.duration in MIN_TRACK_SECONDS..MAX_WAVE_SECONDS }
        if (result.isNotEmpty()) trendingCache = System.currentTimeMillis() to result
        return if (result.size >= 6) result.take(limit) else result + recommendations(limit)
    }

    /** Позиции живого чарта Apple Music: (артист, название). Без ключа и без регистрации. */
    private fun chartEntries(limit: Int): List<Pair<String, String>> {
        val body = runCatching { httpGetString(CHART_URL) }.getOrNull() ?: return emptyList()
        return runCatching {
            val results = json.parseToJsonElement(body)
                .jsonObject["feed"]?.jsonObject
                ?.get("results")?.jsonArray
                .orEmpty()
            results.mapNotNull { item ->
                val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
                val title = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = obj["artistName"]?.jsonPrimitive?.content.orEmpty()
                artist to title
            }.take(limit)
        }.getOrDefault(emptyList())
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
     * [onProgress] reports (receivedBytes, totalBytes); total is -1 when the server does
     * not send Content-Length.
     */
    fun downloadTrack(
        trackId: String,
        quality: AudioQuality,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): java.io.File {
        ensureInit()
        val dir = downloadsDir ?: throw IOException("Хранилище недоступно")
        dir.mkdirs()
        val target = java.io.File(dir, downloadName(trackId) + ".audio")
        if (target.exists() && target.length() > 0) return target
        val url = stripRangeParam(resolveAudioUrl(trackId, quality))
        val tmp = java.io.File(dir, downloadName(trackId) + ".tmp")
        try {
            downloadInChunks(url, tmp, onProgress)
        } catch (e: Throwable) {
            if (e is InterruptedException) throw e
            // Диапазоны не поддерживаются или поток оборвался — страхуем обычной загрузкой.
            tmp.delete()
            downloadWhole(url, tmp, onProgress)
        }
        if (tmp.length() == 0L) {
            tmp.delete()
            throw IOException("Пустой файл")
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Не удалось сохранить файл")
        }
        return target
    }

    /**
     * Качает трек кусками в несколько соединений (HTTP Range). Одно соединение с
     * googlevideo упирается в собственный потолок скорости, поэтому плейлист из
     * сотни треков уходил в часы; 4 потока на трек дают кратный прирост.
     * Если размер неизвестен или файл мелкий — качаем в один поток.
     */
    private fun downloadInChunks(url: String, tmp: java.io.File, onProgress: (Long, Long) -> Unit) {
        val total = contentLength(url)
        val chunks = when {
            total <= 0L -> throw IOException("Диапазоны не поддерживаются")
            total < CHUNK_MIN_SIZE -> 1
            total < 1_500_000L -> 2
            else -> MAX_CHUNKS
        }
        if (chunks == 1) {
            downloadWhole(url, tmp, onProgress, total)
            return
        }

        val received = java.util.concurrent.atomic.AtomicLong(0)
        val lock = Any()
        var reported = 0L
        val report: (Long) -> Unit = { value ->
            synchronized(lock) {
                if (value - reported >= 256 * 1024 || value >= total) {
                    reported = value
                    onProgress(value.coerceAtMost(total), total)
                }
            }
        }

        java.io.RandomAccessFile(tmp, "rw").use { file ->
            file.setLength(total)
            val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val workers = (0 until chunks).map { index ->
                val start = total * index / chunks
                val end = if (index == chunks - 1) total - 1 else (total * (index + 1) / chunks) - 1
                Thread {
                    runCatching {
                        val request = OkHttpRequest.Builder()
                            .url(url)
                            .header("Range", "bytes=$start-$end")
                            .build()
                        downloadHttp.newCall(request).execute().use { resp ->
                            // 200 вместо 206 — сервер Range не умеет: пишем в один поток.
                            if (resp.code != 206) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Пустой ответ")
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var offset = start
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    synchronized(file) {
                                        file.seek(offset)
                                        file.write(buffer, 0, read)
                                    }
                                    offset += read
                                    report(received.addAndGet(read.toLong()))
                                }
                            }
                        }
                    }.onFailure { errors += it }
                }.apply { isDaemon = true }
            }
            workers.forEach { it.start() }
            workers.forEach { it.join() }
            if (errors.isNotEmpty()) throw errors.first()
        }
        onProgress(total, total)
        if (tmp.length() != total) throw IOException("Файл докачан не полностью")
    }

    /** Обычная последовательная загрузка — страховка и вариант для мелких файлов. */
    private fun downloadWhole(
        url: String,
        tmp: java.io.File,
        onProgress: (Long, Long) -> Unit,
        knownTotal: Long = -1L,
    ) {
        val request = OkHttpRequest.Builder().url(url).build()
        downloadHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            runCatching {
                val body = resp.body ?: throw IOException("Пустой ответ")
                val total = if (knownTotal > 0) knownTotal else body.contentLength()
                onProgress(0L, total)
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        var reported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read
                            // Throttle UI updates: report at most every 256 KiB.
                            if (received - reported >= 256 * 1024) {
                                reported = received
                                onProgress(received, total)
                            }
                        }
                        onProgress(received, if (total > 0) total else received)
                    }
                }
            }.onFailure { tmp.delete(); throw IOException("Не удалось сохранить файл") }
        }
    }

    /** Размер файла и поддержка Range — без этого загрузку не нарезать на части. */
    private fun contentLength(url: String): Long {
        val request = OkHttpRequest.Builder().url(url).head().build()
        downloadHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return -1L
            val ranges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
            val length = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            return if (ranges && length > 0) length else -1L
        }
    }

    /**
     * Ссылки YouTube бывают с параметром «range=0-N»: он ограничивает отдачу, поэтому
     * для своей нарезки на куски этот параметр убираем.
     */
    private fun stripRangeParam(url: String): String {
        if (!url.contains("range=")) return url
        return runCatching {
            url.toHttpUrl().newBuilder().removeAllQueryParameters("range").build().toString()
        }.getOrDefault(url)
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

    /**
     * Warm the cache for upcoming tracks (called from a background coroutine).
     * Параллельно и с запасом: разбор страницы — самое долгое в загрузке трека,
     * поэтому ссылки для следующей пачки готовим заранее.
     */
    fun preload(ids: List<String>, quality: AudioQuality) {
        if (ids.isEmpty()) return
        runBlocking {
            coroutineScope {
                ids.take(8).map { id ->
                    async(Dispatchers.IO) { runCatching { resolveAudioUrl(id, quality) } }
                }.awaitAll()
            }
        }
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
