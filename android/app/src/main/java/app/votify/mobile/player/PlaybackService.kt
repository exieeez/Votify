package app.votify.mobile.player

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.graphics.drawable.toBitmap
import app.votify.mobile.MainActivity
import app.votify.mobile.VotifyApp
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.launch

/**
 * Background playback: keeps ExoPlayer alive in a foreground service and exposes it through
 * a MediaSession so the notification, lock screen, headset buttons and Android Auto all work.
 *
 * Queue items use the `votify://stream/<id>` scheme. A [ResolvingDataSource] maps that to the
 * real audio URL just before every connection: the PC server's /api/stream proxy when a server
 * is configured, or a direct YouTube/SoundCloud audio URL extracted on-device otherwise.
 */
class PlaybackService : MediaSessionService() {

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)

        val baseFactory = DefaultDataSource.Factory(this, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            resolveVotifyUri(dataSpec)
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            // Without a BitmapLoader the notification (and OEM «islands»: OriginOS, HyperOS,
            // Honor capsule, Android 16 Live Updates) shows no album art — the default loader
            // cannot fetch http URLs. Coil gives us caching for free.
            .setBitmapLoader(CoilBitmapLoader())
            .build()
    }

    /**
     * Loads artwork for the MediaStyle notification via the app's Coil ImageLoader
     * (HTTP covers, local files; software bitmaps only — hardware bitmaps crash RemoteViews).
     */
    @UnstableApi
    private class CoilBitmapLoader : androidx.media3.common.util.BitmapLoader {

        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            runCatching {
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }.onSuccess { bmp ->
                if (bmp != null) future.set(bmp) else future.setException(IllegalArgumentException("bad bitmap"))
            }.onFailure { future.setException(it as Exception) }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            VotifyApp.instance.appScope.launch {
                runCatching {
                    val request = coil.request.ImageRequest.Builder(VotifyApp.instance)
                        .data(uri)
                        .size(512)
                        .allowHardware(false)
                        .build()
                    coil.Coil.imageLoader(VotifyApp.instance).execute(request)
                        .drawable?.toBitmap()
                }.onSuccess { bmp ->
                    if (bmp != null) future.set(bmp) else future.setException(IllegalStateException("no drawable"))
                }.onFailure { future.setException(it as Exception) }
            }
            return future
        }
    }

    /**
     * `votify://stream/<encoded id>` → the real URL. Runs on ExoPlayer's loading thread and is
     * allowed to block (embedded mode performs a YouTube extraction here, cached by the repo).
     */
    @UnstableApi
    private fun resolveVotifyUri(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != "votify") return dataSpec
        val trackId = Uri.decode(uri.pathSegments.firstOrNull().orEmpty())
        if (trackId.isEmpty()) return dataSpec
        val resolved = VotifyApp.instance.music.resolveStreamUrlSync(trackId)
        return dataSpec.withUri(Uri.parse(resolved))
    }

    private var session: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents: stop if nothing is playing.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
            session = null
        }
        super.onDestroy()
    }
}
