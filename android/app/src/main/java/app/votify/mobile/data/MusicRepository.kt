package app.votify.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** Where music comes from right now. */
enum class SourceMode { Embedded, Server }

/**
 * Single entry point for every music-related call in the UI.
 *
 * - [SourceMode.Embedded] (default, «самостоятельное приложение»): search, streaming, lyrics and
 *   playlist import happen on the phone itself via [EmbeddedMusicSource] — no PC, no server.
 * - [SourceMode.Server]: everything is delegated to the user's own Votify backend
 *   (Settings → Сервер), exactly like before.
 *
 * The mode is derived from the saved server address: an empty address means standalone.
 */
class MusicRepository(
    private val api: VotifyApi,
    settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
    private val lyricsDir: java.io.File? = null,
) {
    private val lyricsJson = Json { ignoreUnknownKeys = true }

    @Volatile
    var mode: SourceMode = SourceMode.Embedded
        private set

    @Volatile
    private var quality: AudioQuality = AudioQuality.Medium

    /** Set when the configured server turned out to be unreachable — stick to standalone. */
    @Volatile
    private var forcedEmbedded = false

    init {
        settingsRepo.settings
            .onEach { s ->
                mode = if (!forcedEmbedded && s.serverUrl.isNotBlank()) SourceMode.Server else SourceMode.Embedded
                quality = s.audioQuality
            }
            .launchIn(scope)

        // A stale saved server address must not break the app: if the server doesn't
        // answer, quietly stay in standalone mode (search/streaming on the phone).
        scope.launch {
            val saved = settingsRepo.settings.first().serverUrl
            if (saved.isNotBlank()) {
                val alive = withTimeoutOrNull(4_000) { runCatching { api.health() }.isSuccess } == true
                if (alive) {
                    // While we're at it, cache the server's Firebase Web Config so
                    // accounts + the theme Workshop also work without the server later.
                    if (FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig) == null) {
                        val raw = runCatching { api.firebaseConfig() }.getOrNull()
                        if (raw != null && FirebaseRest.parseConfig(raw) != null) {
                            settingsRepo.setFirebaseConfig(raw)
                        }
                    }
                } else {
                    forcedEmbedded = true
                    mode = SourceMode.Embedded
                }
            }
        }
    }

    val isServerMode: Boolean get() = mode == SourceMode.Server

    // ---- search / browse ----

    suspend fun search(query: String, limit: Int = 24): List<Track> =
        if (isServerMode) api.search(query, limit)
        else io { EmbeddedMusicSource.search(query, limit) }

    suspend fun artist(name: String, limit: Int = 50): List<Track> =
        if (isServerMode) api.artist(name, limit)
        else io { EmbeddedMusicSource.artistTracks(name, limit) }

    suspend fun customWave(
        artistSeeds: List<String>,
        trackSeeds: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        limit: Int = 20,
        lang: WaveLang = WaveLang.Ukrainian,
    ): List<Track> =
        if (isServerMode) api.customWave(artistSeeds, trackSeeds, exclude, limit)
        else io { EmbeddedMusicSource.wave(artistSeeds, trackSeeds, exclude.toSet(), limit, lang) }

    suspend fun recommendations(limit: Int = 16, lang: WaveLang = WaveLang.Ukrainian): List<Track> =
        if (isServerMode) api.recommendations(limit)
        else io { EmbeddedMusicSource.recommendations(limit, lang) }

    /**
     * Таблетка «Популярные»: на телефоне — чарт региона волны + тикток-тренды
     * (EmbeddedMusicSource.popular); на сервере — его живой /api/charts
     * с регионом языка волны.
     */
    suspend fun popular(lang: WaveLang = WaveLang.Ukrainian, limit: Int = 20): List<Track> =
        if (isServerMode) {
            runCatching { api.charts(limit, lang.chartRegion().lowercase()) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
                ?: api.recommendations(limit)
        } else io {
            EmbeddedMusicSource.popular(lang, limit)
        }

    /**
     * «Чарты»: на телефоне — чарты YouTube Music по странам + топ артистов
     * (InnertubeCharts, подход ytmusicapi), прилетают по мере готовности;
     * на сервере — один блок /api/charts.
     */
    suspend fun chartSections(region: String = "UA", onSection: suspend (ChartSection) -> Unit) {
        if (isServerMode) {
            // Сервер может быть старой версии без /api/charts — тогда берём рекомендации.
            val chart = runCatching { api.charts(30, region.lowercase()) }.getOrNull()
            val tracks = if (!chart.isNullOrEmpty()) chart else api.recommendations(30)
            if (tracks.isNotEmpty()) onSection(ChartSection("chart", app.votify.mobile.R.string.section_chart, tracks))
        } else {
            InnertubeCharts.loadAll(region, onSection)
        }
    }

    suspend fun lyrics(track: String, artist: String): LyricsResponse {
        cachedLyrics(track, artist)?.let { return it }
        val fresh = if (isServerMode) api.lyrics(track, artist)
        else io { EmbeddedMusicSource.lyrics(track, artist) }
        cacheLyrics(track, artist, fresh)
        return fresh
    }

    /** Cached lyric sheets (counted on the storage screen). */
    suspend fun lyricsCacheCount(): Int = withContext(Dispatchers.IO) {
        lyricsDir?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
    }

    suspend fun clearLyricsCache() = withContext(Dispatchers.IO) {
        lyricsDir?.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.forEach { runCatching { it.delete() } }
    }

    private suspend fun cachedLyrics(track: String, artist: String): LyricsResponse? =
        withContext(Dispatchers.IO) {
            val dir = lyricsDir ?: return@withContext null
            val file = java.io.File(dir, lyricsKey(track, artist) + ".json")
            if (!file.isFile) return@withContext null
            runCatching { lyricsJson.decodeFromString(LyricsResponse.serializer(), file.readText()) }
                .getOrNull()?.takeIf { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        }

    private suspend fun cacheLyrics(track: String, artist: String, response: LyricsResponse) {
        val dir = lyricsDir ?: return
        if (response.syncedLyrics.isNullOrBlank() && response.plainLyrics.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                java.io.File(dir, lyricsKey(track, artist) + ".json")
                    .writeText(lyricsJson.encodeToString(LyricsResponse.serializer(), response))
            }
        }
    }

    private fun lyricsKey(track: String, artist: String): String {
        val raw = "${artist.trim().lowercase()}\n${track.trim().lowercase()}".toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.getInstance("SHA-1").digest(raw)
            .joinToString("") { "%02x".format(it) }
    }

    // ---- playlist import ----

    suspend fun importPlaylist(url: String): ImportedTracks =
        if (isServerMode) {
            if ("soundcloud.com" in url) api.importSoundcloud(url) else api.importPlaylist(url)
        } else {
            EmbeddedMusicSource.importPlaylist(url)
        }

    // ---- streaming ----

    /** Fire-and-forget cache warm-up for upcoming tracks. */
    fun preload(ids: List<String>) {
        if (ids.isEmpty()) return
        if (isServerMode) {
            scope.launch { runCatching { api.preload(ids) } }
        } else {
            scope.launch(Dispatchers.IO) { runCatching { EmbeddedMusicSource.preload(ids, quality) } }
        }
    }

    /**
     * Synchronous stream URL for a track — called on ExoPlayer's loading thread by the
     * ResolvingDataSource in PlaybackService. In server mode this is a pure URL rewrite;
     * in embedded mode it extracts the direct audio URL (cached).
     */
    fun resolveStreamUrlSync(trackId: String): String {
        // Offline first: a downloaded copy plays without any network call.
        if (!isServerMode) {
            EmbeddedMusicSource.downloadFileFor(trackId)
                ?.takeIf { it.exists() && it.length() > 0 }
                ?.let { return it.toURI().toString() }
            return EmbeddedMusicSource.resolveAudioUrl(trackId, quality)
        }
        return api.streamUrl(trackId)
    }

    // ---- offline downloads ----

    fun isDownloaded(trackId: String): Boolean =
        !isServerMode && EmbeddedMusicSource.isDownloaded(trackId)

    /** Live download progress (trackId → progress) for the UI progress bars. */
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = DownloadTracker.progress

    /** Ids with a stored offline copy, as observed by the app so far. */
    val downloadedIds: StateFlow<Set<String>> = DownloadTracker.downloaded

    fun downloadStats(): Pair<Int, Long> =
        if (isServerMode) 0 to 0L else EmbeddedMusicSource.downloadStats()

    fun deleteDownload(trackId: String) {
        if (!isServerMode) {
            EmbeddedMusicSource.deleteDownload(trackId)
            DownloadTracker.removeDownloaded(trackId)
        }
    }

    /** Blocking download of one track (true = stored / already present). */
    fun downloadTrackSync(trackId: String): Boolean =
        runCatching { !isServerMode && EmbeddedMusicSource.downloadTrack(trackId, quality).length() > 0 }
            .getOrDefault(false)

    /**
     * Blocking download with live progress reporting into [DownloadTracker] (and via
     * [onProgress] for callers that want the raw numbers). Safe on any thread.
     */
    fun downloadTrackTracked(trackId: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean =
        runCatching {
            if (isServerMode) return@runCatching false
            DownloadTracker.start(trackId)
            val ok = EmbeddedMusicSource.downloadTrack(trackId, quality) { received, total ->
                DownloadTracker.update(trackId, received, total)
                onProgress(received, total)
            }.length() > 0
            DownloadTracker.finish(trackId, ok)
            ok
        }.getOrElse {
            DownloadTracker.finish(trackId, false)
            false
        }

    /** Mark ids as downloaded after scanning the offline storage. */
    fun seedDownloaded(ids: Set<String>) = DownloadTracker.seedDownloaded(ids)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
