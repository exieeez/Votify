package app.votify.mobile.ui.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.library.CollectionHeader
import app.votify.mobile.ui.library.EmptyCollection
import app.votify.mobile.ui.theme.VotifyColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistUiState(
    val name: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Loads `/api/artist?name=` for the artist currently opened; caches the last result. */
class ArtistViewModel(private val api: VotifyApi) : ViewModel() {

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state
    private var job: Job? = null

    fun load(name: String, force: Boolean = false) {
        if (!force && name == _state.value.name && (_state.value.tracks.isNotEmpty() || _state.value.isLoading)) return
        job?.cancel()
        _state.value = ArtistUiState(name = name, isLoading = true)
        job = viewModelScope.launch {
            runCatching { api.artist(name, limit = 50) }
                .onSuccess { tracks -> _state.update { it.copy(tracks = tracks, isLoading = false) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update { it.copy(isLoading = false, error = e.message ?: "error") }
                }
        }
    }
}

@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    name: String,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    LaunchedEffect(name) { viewModel.load(name) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            CollectionHeader(
                title = name,
                subtitle = when {
                    state.isLoading -> stringResource(R.string.artist_loading)
                    state.tracks.isEmpty() -> stringResource(R.string.artist_title)
                    else -> stringResource(R.string.artist_title) + " · " + pluralTracks(state.tracks.size)
                },
                cover = state.tracks.firstOrNull()?.cover,
                icon = Icons.Outlined.Person,
                tracks = state.tracks,
                onBack = onBack,
                onPlay = onPlay,
            )
        }

        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VotifyColors.TextMuted, strokeWidth = 2.dp)
                }
            }

            state.error != null -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error), color = VotifyColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(state.error ?: "", color = VotifyColors.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = { viewModel.load(name, force = true) })
                }
            }

            state.tracks.isEmpty() -> item { EmptyCollection(stringResource(R.string.artist_empty)) }

            else -> {
                item { SectionHeader(title = stringResource(R.string.artist_tracks), modifier = Modifier.padding(horizontal = 12.dp)) }
                itemsIndexed(state.tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                    TrackRow(
                        track = t,
                        isCurrent = t.id == currentTrackId,
                        onClick = { onPlay(state.tracks, i) },
                        onMore = { onMore(t) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
