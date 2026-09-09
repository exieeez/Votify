package app.votify.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val recent: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when the failure was a network/timeout error → show a connection hint. */
    val offline: Boolean = false,
    /** Which hint to show for [offline]: server address (server mode) or plain "no internet". */
    val serverMode: Boolean = true,
    val searched: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val music: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val queryFlow = MutableStateFlow("")
    private var job: Job? = null

    init {
        // Search-as-you-type with a debounce so we don't hammer YouTube on every keystroke.
        queryFlow
            .debounce(450)
            .map { it.trim() }
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        if (q.isBlank()) {
            job?.cancel()
            _state.update { it.copy(results = emptyList(), isLoading = false, error = null, offline = false, searched = false) }
        }
        queryFlow.value = q
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isNotEmpty()) runSearch(q)
    }

    fun retry() = submit()

    fun clear() = onQueryChange("")

    fun pickRecent(q: String) {
        _state.update { it.copy(query = q) }
        runSearch(q)
    }

    fun clearRecent() = _state.update { it.copy(recent = emptyList()) }

    private fun runSearch(q: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, offline = false, serverMode = music.isServerMode) }
            runCatching { music.search(q) }
                .onSuccess { tracks ->
                    _state.update {
                        it.copy(
                            results = tracks,
                            isLoading = false,
                            searched = true,
                            recent = (listOf(q) + it.recent.filterNot { r -> r.equals(q, ignoreCase = true) }).take(8),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    // Connection failures get a dedicated localized hint instead of a raw
                    // stack string: "check the server address" in server mode, "check
                    // internet" in standalone mode.
                    val netFail = e is java.io.IOException && e !is app.votify.mobile.data.ApiException
                    _state.update {
                        it.copy(
                            isLoading = false,
                            searched = true,
                            offline = netFail,
                            serverMode = music.isServerMode,
                            error = if (netFail) null else (e.message ?: "error"),
                        )
                    }
                }
        }
    }
}
