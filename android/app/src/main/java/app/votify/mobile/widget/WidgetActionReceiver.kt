package app.votify.mobile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.votify.mobile.VotifyApp
import kotlinx.coroutines.launch

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = runCatching { VotifyApp.instance }.getOrNull() ?: return
        val player = app.player

        when (intent.action) {
            WidgetActions.ACTION_PLAY_PAUSE -> {
                player.togglePlayPause()
            }
            WidgetActions.ACTION_NEXT -> {
                player.next()
            }
            WidgetActions.ACTION_PREV -> {
                player.previous()
            }
            WidgetActions.ACTION_TOGGLE_FAVORITE -> {
                val currentTrack = player.state.value.current
                if (currentTrack != null) {
                    app.appScope.launch {
                        app.library.toggleFavorite(currentTrack)
                        WidgetUpdateHelper.updateAll(context)
                    }
                }
            }
            WidgetActions.ACTION_PLAY_INDEX -> {
                val idx = intent.getIntExtra(WidgetActions.EXTRA_INDEX, -1)
                val queue = player.state.value.queue
                if (idx in queue.indices) {
                    player.play(queue, idx)
                }
            }
            WidgetActions.ACTION_SHUFFLE -> {
                player.toggleShuffle()
            }
            WidgetActions.ACTION_SET_REMIX -> {
                when (intent.getStringExtra(WidgetActions.EXTRA_REMIX_PRESET)) {
                    "slowed" -> {
                        player.setPlaybackRemix(speed = 0.85f, pitch = 0.85f, pitchLinked = true)
                        player.setReverb("hall")
                        player.setBassBoost(true)
                    }
                    "speedup" -> {
                        player.setPlaybackRemix(speed = 1.25f, pitch = 1.25f, pitchLinked = true)
                        player.setReverb("none")
                        player.setBassBoost(false)
                    }
                    else -> {
                        player.setPlaybackRemix(speed = 1.0f, pitch = 1.0f, pitchLinked = true)
                        player.setReverb("none")
                        player.setBassBoost(false)
                    }
                }
            }
        }

        WidgetUpdateHelper.updateAll(context)
    }
}
