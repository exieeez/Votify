package app.votify.mobile.ui.trending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendingUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** «В тренде»: fresh popular tracks (recommendations), refreshable. */
class TrendingViewModel(private val music: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(TrendingUiState())
    val state: StateFlow<TrendingUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { music.trending(limit = 50) }
                .onSuccess { tracks -> _state.update { it.copy(tracks = tracks, isLoading = false) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(isLoading = false, error = e.message ?: "error") }
                }
        }
    }
}

@Composable
fun TrendingScreen(
    viewModel: TrendingViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.trending_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    pluralTracks(state.tracks.size) + " · " + stringResource(R.string.trending_live),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                )
            }
            IconButton(onClick = { if (state.tracks.isNotEmpty()) onPlay(state.tracks.shuffled(), 0) }) {
                Icon(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle_all), tint = VotifyColors.TextPrimary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillChip(
                text = stringResource(R.string.action_play_all),
                selected = true,
                onClick = { if (state.tracks.isNotEmpty()) onPlay(state.tracks, 0) },
                leading = { Icon(Icons.Filled.PlayArrow, null, tint = VotifyColors.OnPrimary, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
            )
            PillChip(
                text = stringResource(R.string.search_retry),
                selected = false,
                onClick = viewModel::refresh,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
            }

            state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(state.error ?: "", style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = viewModel::refresh)
                }
            }

            state.tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextSecondary)
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item {
                    VotifyCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.tracks.forEachIndexed { index, track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = track.id == currentTrackId,
                                    onClick = { onPlay(state.tracks, index) },
                                    onMore = { onMore(track) },
                                )
                                if (index != state.tracks.lastIndex) {
                                    HorizontalDivider(color = VotifyColors.BorderSubtle, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
