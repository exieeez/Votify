package app.votify.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WaveSource { Personal, Generic }

data class HomeUiState(
    /** Tracks shown as orbit bubbles around the big Play and used as the wave queue. */
    val wave: List<Track> = emptyList(),
    /** Whether the wave was built from the user's history or from generic recommendations. */
    val waveSource: WaveSource = WaveSource.Generic,
    /** The artists the wave was seeded with (for the caption under the hero). */
    val seeds: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Distinct recently played tracks — drives the «Недавние» card. */
    val recent: StateFlow<List<Track>> =
        library.recent(limit = 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteCount: StateFlow<Int> =
        library.favoriteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refresh()
    }

    /**
     * «Моя волна»: seeded by what the user actually listens to.
     *  - artist seeds  = top artists from the play history (by play count)
     *  - track seeds   = "artist title" of the most recent listens (keeps the wave anchored)
     *  - exclude       = the recent tracks themselves, so the wave brings *new* music
     * Falls back to the generic /api/recommendations when there is no history yet.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val recentTracks = runCatching { library.recent(limit = 30).first() }.getOrDefault(emptyList())
            val playlistArtists = runCatching { library.playlistArtists(limit = 8) }.getOrDefault(emptyList())
            // Wave seeds: what the user PLAYS (history) + what they COLLECT (playlists, favorites).
            val artistSeeds = (runCatching { library.topArtists(limit = 6) }.getOrDefault(emptyList()) + playlistArtists)
                .distinct()
                .take(6)
            val favorites = runCatching { library.favorites.first() }.getOrDefault(emptyList())

            val trackSeeds = (favorites.take(3) + recentTracks.take(6))
                .distinctBy { it.id }
                .map { "${it.artist} ${it.title}".trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)

            val personal = artistSeeds.isNotEmpty() || trackSeeds.isNotEmpty()

            val result = if (personal) {
                runCatching {
                    music.customWave(
                        artistSeeds = artistSeeds,
                        trackSeeds = trackSeeds,
                        exclude = recentTracks.map { it.id },
                        limit = 24,
                    )
                }.recoverCatching { music.recommendations(limit = 20) }
            } else {
                runCatching { music.recommendations(limit = 20) }
            }

            result
                .onSuccess { tracks ->
                    val wave = if (tracks.isEmpty() && personal) {
                        runCatching { music.recommendations(limit = 20) }.getOrDefault(emptyList())
                    } else {
                        tracks
                    }
                    _state.update {
                        it.copy(
                            wave = wave.shuffled(),
                            waveSource = if (personal && wave.isNotEmpty()) WaveSource.Personal else WaveSource.Generic,
                            seeds = artistSeeds,
                            isLoading = false,
                        )
                    }
                    music.preload(wave.take(2).map { t -> t.id })
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "error") }
                }
        }
    }
}
