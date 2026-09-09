package app.votify.mobile.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Non-empty after a successful import: tracks + editable playlist name. */
    val tracks: List<Track> = emptyList(),
    val name: String = "",
    val saving: Boolean = false,
)

sealed interface ImportEvent {
    data class Imported(val playlistId: Long, val name: String, val count: Int, val total: Int) : ImportEvent
}

/**
 * «Импортировать из сервиса»: paste a YouTube / Spotify / SoundCloud playlist URL, the
 * backend resolves it to tracks (GET /api/playlist or /api/soundcloud/import), then the
 * result is saved as a local playlist in Room.
 */
class ImportViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ImportEvent> = _events

    fun onUrlChange(v: String) = _state.update { it.copy(url = v) }

    fun onNameChange(v: String) = _state.update { it.copy(name = v.take(80)) }

    fun import() {
        val url = _state.value.url.trim()
        if (url.isEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, tracks = emptyList(), name = "") }
            runCatching { music.importPlaylist(url) }
                .onSuccess { imported ->
                    _state.update {
                        it.copy(
                            loading = false,
                            tracks = imported.tracks,
                            name = imported.name?.takeIf { n -> n.isNotBlank() } ?: defaultName(),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "Import failed") }
                }
        }
    }

    /** Create the local playlist and add every imported track to it. */
    fun saveToLibrary() {
        val s = _state.value
        if (s.tracks.isEmpty() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val name = s.name.trim().ifEmpty { defaultName() }
            val id = library.createPlaylist(name)
            var added = 0
            s.tracks.forEach { t -> if (library.addToPlaylist(id, t)) added++ }
            _state.update { it.copy(saving = false, url = "", tracks = emptyList(), name = "") }
            _events.tryEmit(ImportEvent.Imported(id, name, added, s.tracks.size))
        }
    }

    private fun defaultName(): String =
        SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date())
}
