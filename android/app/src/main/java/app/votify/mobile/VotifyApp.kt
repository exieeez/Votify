package app.votify.mobile

import android.app.Application
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.player.PlayerController

/** Poor-man's DI: app-scoped singletons reachable from any screen. */
class VotifyApp : Application() {

    lateinit var api: VotifyApi
        private set

    lateinit var player: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        api = VotifyApi()
        player = PlayerController(this, api)
        instance = this
    }

    companion object {
        lateinit var instance: VotifyApp
            private set
    }
}
