package app.votify.mobile

import android.app.Application
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.local.VotifyDatabase
import app.votify.mobile.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Poor-man's DI: app-scoped singletons reachable from any screen. */
class VotifyApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var api: VotifyApi
        private set

    lateinit var database: VotifyDatabase
        private set

    lateinit var library: LibraryRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var player: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        api = VotifyApi()
        database = VotifyDatabase.create(this)
        library = LibraryRepository(database)
        settings = SettingsRepository(this)
        player = PlayerController(
            context = this,
            api = api,
            scope = appScope,
            onTrackStarted = { track -> library.recordPlay(track) },
        )
    }

    companion object {
        lateinit var instance: VotifyApp
            private set
    }
}
