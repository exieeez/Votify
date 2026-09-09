package app.votify.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
) {

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
    ): List<Track> =
        if (isServerMode) api.customWave(artistSeeds, trackSeeds, exclude, limit)
        else io { EmbeddedMusicSource.wave(artistSeeds, trackSeeds, exclude.toSet(), limit) }

    suspend fun recommendations(limit: Int = 16): List<Track> =
        if (isServerMode) api.recommendations(limit)
        else io { EmbeddedMusicSource.recommendations(limit) }

    /** «В тренде»: the real CIS chart artists' current hits (standalone) / server recs. */
    suspend fun trending(limit: Int = 50): List<Track> =
        if (isServerMode) api.recommendations(limit)
        else io { EmbeddedMusicSource.trendingCis(limit) }

    suspend fun lyrics(track: String, artist: String): LyricsResponse =
        if (isServerMode) api.lyrics(track, artist)
        else io { EmbeddedMusicSource.lyrics(track, artist) }

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

    fun downloadStats(): Pair<Int, Long> =
        if (isServerMode) 0 to 0L else EmbeddedMusicSource.downloadStats()

    fun deleteDownload(trackId: String) {
        if (!isServerMode) EmbeddedMusicSource.deleteDownload(trackId)
    }

    /** Blocking download of one track (true = stored / already present). */
    fun downloadTrackSync(trackId: String): Boolean =
        runCatching { !isServerMode && EmbeddedMusicSource.downloadTrack(trackId, quality).length() > 0 }
            .getOrDefault(false)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
