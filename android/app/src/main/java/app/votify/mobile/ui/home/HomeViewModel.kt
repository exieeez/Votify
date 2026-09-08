package app.votify.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.Track
import app.votify.mobile.data.VotifyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** Tracks shown as orbit bubbles around the big Play and used as the wave queue. */
    val wave: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val api: VotifyApi) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.recommendations(limit = 20) }
                .onSuccess { tracks ->
                    _state.update { it.copy(wave = tracks.shuffled(), isLoading = false) }
                    api.preload(tracks.take(2).map { t -> t.id })
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "error") }
                }
        }
    }
}
