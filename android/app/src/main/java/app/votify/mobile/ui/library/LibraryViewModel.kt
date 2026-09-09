package app.votify.mobile.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.R
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.local.PlaylistEntity
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.player.PlayerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A snackbar message that is resolved to text inside composition. */
data class UiMessage(@StringRes val id: Int, val args: List<Any> = emptyList())

/** Which "⋮" menu is open and where it was opened from (affects the available actions). */
data class TrackMenu(val track: Track, val playlistId: Long? = null, val downloaded: Boolean = false)

/**
 * Shared by every screen that shows tracks: favorites / history / playlists state plus the
 * global bottom sheets (track menu, playlist picker, queue) and snackbar messages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val library: LibraryRepository,
    private val player: PlayerController,
) : ViewModel() {

    private fun <T> stream(flow: Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val favorites: StateFlow<List<Track>> = stream(library.favorites, emptyList())
    val favoriteIds: StateFlow<Set<String>> = stream(library.favoriteIds, emptySet())
    val recent: StateFlow<List<Track>> = stream(library.recent(limit = 200), emptyList())
    val historyCount: StateFlow<Int> = stream(library.historyCount, 0)
    val playlists: StateFlow<List<PlaylistSummary>> = stream(library.playlists, emptyList())

    // ---- Playlist detail ----

    private val openedPlaylistId = MutableStateFlow<Long?>(null)

    val openedPlaylist: StateFlow<PlaylistEntity?> = stream(
        openedPlaylistId.flatMapLatest { id -> if (id == null) flowOf<PlaylistEntity?>(null) else library.playlist(id) },
        null,
    )
    val openedPlaylistTracks: StateFlow<List<Track>> = stream(
        openedPlaylistId.flatMapLatest { id -> if (id == null) flowOf<List<Track>>(emptyList()) else library.playlistTracks(id) },
        emptyList(),
    )

    fun openPlaylist(id: Long) {
        openedPlaylistId.value = id
    }

    // ---- Sheets ----

    private val _menu = MutableStateFlow<TrackMenu?>(null)
    val menu: StateFlow<TrackMenu?> = _menu

    private val _playlistPicker = MutableStateFlow<Track?>(null)
    val playlistPicker: StateFlow<Track?> = _playlistPicker

    private val _queueOpen = MutableStateFlow(false)
    val queueOpen: StateFlow<Boolean> = _queueOpen

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    fun openMenu(track: Track, playlistId: Long? = null) {
        _menu.value = TrackMenu(track, playlistId, music.isDownloaded(track.id))
    }

    fun closeMenu() {
        _menu.value = null
    }

    /** Download one track for offline listening (menu action). */
    fun downloadTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            if (music.isServerMode) {
                _messages.tryEmit(UiMessage(R.string.toast_download_server))
                return@launch
            }
            _messages.tryEmit(UiMessage(R.string.toast_download_started))
            val ok = music.downloadTrackSync(track.id)
            _messages.tryEmit(
                UiMessage(if (ok) R.string.toast_download_track_done else R.string.toast_download_track_failed, listOf(track.title)),
            )
        }
    }

    /** Remove a track's offline copy. */
    fun removeDownload(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            music.deleteDownload(track.id)
            _messages.tryEmit(UiMessage(R.string.toast_download_removed))
        }
    }

    /** Download a whole playlist sequentially, reporting progress every few tracks. */
    fun downloadPlaylist(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (music.isServerMode) {
                _messages.tryEmit(UiMessage(R.string.toast_download_server))
                return@launch
            }
            _messages.tryEmit(UiMessage(R.string.toast_download_started))
            var ok = 0
            tracks.forEachIndexed { i, t ->
                if (music.downloadTrackSync(t.id)) ok++
                if ((i + 1) % 10 == 0 || i == tracks.lastIndex) {
                    _messages.tryEmit(UiMessage(R.string.toast_download_progress, listOf(i + 1, tracks.size)))
                }
            }
            _messages.tryEmit(UiMessage(R.string.toast_download_done, listOf(ok, tracks.size)))
        }
    }

    fun openPlaylistPicker(track: Track) {
        _menu.value = null
        _playlistPicker.value = track
    }

    fun closePlaylistPicker() {
        _playlistPicker.value = null
    }

    fun openQueue() {
        _queueOpen.value = true
    }

    fun closeQueue() {
        _queueOpen.value = false
    }

    // ---- Actions ----

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val nowFavorite = library.toggleFavorite(track)
            _messages.tryEmit(UiMessage(if (nowFavorite) R.string.toast_favorite_added else R.string.toast_favorite_removed))
        }
    }

    fun playNext(track: Track) {
        player.playNext(track)
        _messages.tryEmit(UiMessage(R.string.toast_play_next))
    }

    fun addToQueue(tracks: List<Track>) {
        player.enqueue(tracks)
        _messages.tryEmit(UiMessage(R.string.toast_added_to_queue))
    }

    fun createPlaylist(name: String, thenAdd: Track? = null) {
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            if (thenAdd != null) addToPlaylist(id, thenAdd)
        }
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val added = library.addToPlaylist(playlistId, track)
            val name = library.playlists.first().firstOrNull { it.id == playlistId }?.name ?: ""
            _messages.tryEmit(UiMessage(if (added) R.string.toast_added_to_playlist else R.string.toast_already_in_playlist, listOf(name)))
            _playlistPicker.value = null
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch { library.removeFromPlaylist(playlistId, trackId) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    fun skipToQueueItem(index: Int) = player.skipToQueueItem(index)
}
