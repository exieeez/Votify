package app.votify.mobile.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Lyrics
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LyricsState {
    data object Hidden : LyricsState
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
}

/**
 * Player-adjacent state that isn't playback itself: lyrics for the current track,
 * favorite flag, artwork style preference.
 */
class PlayerViewModel(
    private val music: MusicRepository,
    private val player: PlayerController,
    private val library: LibraryRepository,
) : ViewModel() {

    val favoriteIds: StateFlow<Set<String>> =
        library.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _lyricsVisible = MutableStateFlow(false)
    val lyricsVisible: StateFlow<Boolean> = _lyricsVisible

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Hidden)
    val lyrics: StateFlow<LyricsState> = _lyrics

    private var lyricsJob: Job? = null
    private var lyricsForId: String? = null

    init {
        // Refetch lyrics whenever the current track changes while the panel is open.
        viewModelScope.launch {
            player.state.map { it.current?.id }.distinctUntilChanged().collect { id ->
                if (_lyricsVisible.value) loadLyrics(id)
            }
        }
    }

    fun toggleLyrics() {
        val show = !_lyricsVisible.value
        _lyricsVisible.value = show
        // Closing keeps the cache: the one-line snippet under the artwork reuses it.
        if (show) loadLyrics(player.state.value.current?.id)
    }

    /** Preloads lyrics for the snippet under the artwork (without opening the panel). */
    fun ensureLyrics() {
        if (_lyricsVisible.value) return
        loadLyrics(player.state.value.current?.id)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { library.toggleFavorite(track) }
    }

    private fun loadLyrics(id: String?) {
        val track = player.state.value.current
        if (id == null || track == null) {
            _lyrics.value = LyricsState.NotFound
            return
        }
        if (id == lyricsForId && _lyrics.value is LyricsState.Loaded) return
        lyricsJob?.cancel()
        lyricsForId = id
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            val result = runCatching { music.lyrics(track = track.title, artist = track.artist) }
                .getOrNull()
                ?.let(Lyrics::from)
            // Ignore late results for a track that is no longer current.
            if (player.state.value.current?.id != id) return@launch
            _lyrics.update { if (result != null) LyricsState.Loaded(result) else LyricsState.NotFound }
        }
    }
}
