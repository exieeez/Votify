package app.votify.mobile

import android.app.Application
import android.os.Build
import app.votify.mobile.data.Account
import app.votify.mobile.data.CloudSyncAuto
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.local.VotifyDatabase
import app.votify.mobile.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Poor-man's DI: app-scoped singletons reachable from any screen. */
class VotifyApp : Application(), coil.ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var api: VotifyApi
        private set

    lateinit var database: VotifyDatabase
        private set

    lateinit var library: LibraryRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    /** Standalone (embedded) or server-backed music access — used by every screen. */
    lateinit var music: MusicRepository
        private set

    lateinit var player: PlayerController
        private set

    /** Animated GIF / WebP support for backgrounds (ImageDecoder on 28+, GifDecoder below). */
    override fun newImageLoader(): coil.ImageLoader =
        coil.ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                else add(coil.decode.GifDecoder.Factory())
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)

        // Read the saved server address + auth token before anything touches the network:
        // the first API call (home screen) must already point at the user's PC, not 10.0.2.2.
        // An empty address means standalone mode (no server needed at all).
        val boot = bootstrap()
        api = VotifyApi(boot.serverUrl.ifBlank { BuildConfig.API_BASE_URL })
        api.authToken = boot.account?.token

        // Offline listening: downloaded tracks land here and play without network.
        app.votify.mobile.data.EmbeddedMusicSource.downloadsDir = java.io.File(filesDir, "downloads")

        database = VotifyDatabase.create(this)
        library = LibraryRepository(database)
        music = MusicRepository(api, settings, appScope)
        // Automatic account cloud backup: every change (favorites, playlists, settings)
        // is pushed to the signed-in user's private Firestore document a few seconds later.
        CloudSyncAuto.start(this)
        player = PlayerController(
            context = this,
            scope = appScope,
            onTrackStarted = { track -> library.recordPlay(track) },
        )
    }

    /**
     * DataStore's first read is a tiny file — blocking briefly at startup is fine and removes
     * any race between the UI and the saved backend address. The timeout guards against a
     * corrupted store ever hanging the launch.
     */
    private fun bootstrap(): BootConfig = runBlocking {
        withTimeoutOrNull(2_000) {
            BootConfig(
                serverUrl = settings.settings.first().serverUrl,
                account = settings.account.first(),
            )
        } ?: BootConfig(serverUrl = "", account = null)
    }

    private data class BootConfig(val serverUrl: String, val account: Account?)

    companion object {
        lateinit var instance: VotifyApp
            private set
    }
}
