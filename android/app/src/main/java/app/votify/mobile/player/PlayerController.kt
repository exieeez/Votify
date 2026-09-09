package app.votify.mobile.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.votify.mobile.data.Track
import com.google.common.util.concurrent.MoreExecutors
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val hasNext: Boolean get() = queue.isNotEmpty()
}

/**
 * Single app-wide bridge between Compose UI and the MediaSession in [PlaybackService].
 * Tracks are turned into MediaItems that point at the backend's /api/stream proxy.
 */
/** Media items use this scheme; the ResolvingDataSource swaps it for the real stream URL. */
const val RESOLVE_URI_SCHEME = "votify://stream/"

class PlayerController(
    context: Context,
    private val scope: CoroutineScope,
    /** Called once per started media item — used to record listening history. */
    private val onTrackStarted: suspend (Track) -> Unit = {},
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportedId: String? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var queueTracks: List<Track> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) {
                reportStartedIfNeeded(player)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.localizedMessage ?: "Playback error", isBuffering = false) }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            controller?.let { p ->
                if (p.isPlaying) _state.update { it.copy(positionMs = p.currentPosition, durationMs = p.duration.coerceAtLeast(0)) }
            }
            handler.postDelayed(this, 500)
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                syncFromPlayer(c)
                handler.post(ticker)
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    // ---- commands ----

    /** Replace the queue with [tracks] and start from [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        queueTracks = tracks
        lastReportedId = null // a fresh start always counts as a new listen
        _state.update { it.copy(error = null, isBuffering = true) }
        c.setMediaItems(tracks.map(::toMediaItem), startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playTrack(track: Track) = play(listOf(track))

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        // Standard behaviour: restart the track if we're past 3s, otherwise go back.
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(fraction: Float) {
        val c = controller ?: return
        val d = c.duration
        if (d > 0) c.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekToMs(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs.coerceAtLeast(0))
        if (!c.isPlaying) c.play()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Append tracks to the end of the current queue (or start playing if idle). */
    fun enqueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) {
            play(tracks)
            return
        }
        val existing = queueTracks.map { it.id }.toSet()
        val fresh = tracks.filterNot { it.id in existing }
        if (fresh.isEmpty()) return
        queueTracks = queueTracks + fresh
        c.addMediaItems(fresh.map(::toMediaItem))
        _state.update { it.copy(queue = queueTracks) }
    }

    /** Insert a track right after the current one. */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(track))
            return
        }
        val at = c.currentMediaItemIndex + 1
        val mutable = queueTracks.toMutableList()
        mutable.add(at.coerceAtMost(mutable.size), track)
        queueTracks = mutable
        c.addMediaItem(at, toMediaItem(track))
        _state.update { it.copy(queue = queueTracks) }
    }

    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    private fun reportStartedIfNeeded(p: Player) {
        if (!p.isPlaying) return
        val id = p.currentMediaItem?.mediaId ?: return
        if (id == lastReportedId) return
        lastReportedId = id
        val track = queueTracks.firstOrNull { it.id == id } ?: return
        scope.launch { runCatching { onTrackStarted(track) } }
    }

    // ---- internals ----

    private fun toMediaItem(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(RESOLVE_URI_SCHEME + Uri.encode(t.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setArtworkUri(t.cover.takeIf { it.isNotBlank() }?.toUri())
                .build(),
        )
        .build()

    private fun syncFromPlayer(p: Player) {
        val id = p.currentMediaItem?.mediaId
        val current = queueTracks.firstOrNull { it.id == id }
            ?: p.currentMediaItem?.let { mi ->
                Track(
                    id = mi.mediaId,
                    title = mi.mediaMetadata.title?.toString() ?: "",
                    artist = mi.mediaMetadata.artist?.toString() ?: "",
                    cover = mi.mediaMetadata.artworkUri?.toString() ?: "",
                )
            }
        _state.update {
            it.copy(
                current = current,
                queue = queueTracks,
                currentIndex = p.currentMediaItemIndex,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = p.duration.coerceAtLeast(0),
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
            )
        }
    }
}
