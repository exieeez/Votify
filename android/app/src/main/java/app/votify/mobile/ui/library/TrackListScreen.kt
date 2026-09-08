package app.votify.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.ConfirmDialog
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.PlaylistNameDialog
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors

/** Header shared by Favorites / History / Playlist / Artist screens. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    cover: String?,
    icon: ImageVector,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VotifyColors.SurfaceContainer)
                    .border(1.dp, VotifyColors.BorderSubtle, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cover.isNullOrBlank()) {
                    Icon(icon, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(40.dp))
                } else {
                    Artwork(cover, size = 96.dp, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }, size = 48.dp, filled = true, contentDescription = stringResource(R.string.action_play_all)) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(28.dp))
            }
            PillChip(
                text = stringResource(R.string.action_shuffle_all),
                selected = false,
                onClick = { if (tracks.isNotEmpty()) onPlay(tracks.shuffled(), 0) },
                leading = { Icon(Icons.Filled.Shuffle, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(16.dp)) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun EmptyCollection(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VotifyColors.TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    emptyText: String,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    header: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item { header() }
        if (tracks.isEmpty()) {
            item { EmptyCollection(emptyText) }
        }
        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(
                track = t,
                isCurrent = t.id == currentTrackId,
                onClick = { onPlay(tracks, i) },
                onMore = { onMore(t) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: LibraryViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    TrackList(
        tracks = favorites,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_favorites_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
    ) {
        CollectionHeader(
            title = stringResource(R.string.library_favorites),
            subtitle = pluralTracks(favorites.size),
            cover = favorites.firstOrNull()?.cover,
            icon = Icons.Filled.Favorite,
            tracks = favorites,
            onBack = onBack,
            onPlay = onPlay,
        )
    }
}

@Composable
fun HistoryScreen(
    viewModel: LibraryViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    TrackList(
        tracks = recent,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_history_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
    ) {
        CollectionHeader(
            title = stringResource(R.string.library_history),
            subtitle = pluralTracks(recent.size),
            cover = recent.firstOrNull()?.cover,
            icon = Icons.Outlined.History,
            tracks = recent,
            onBack = onBack,
            onPlay = onPlay,
            trailing = {
                if (recent.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.library_history_clear), tint = VotifyColors.TextSecondary)
                    }
                }
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.dialog_clear_history),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
        )
    }
}

@Composable
fun PlaylistScreen(
    viewModel: LibraryViewModel,
    playlistId: Long,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LaunchedEffect(playlistId) { viewModel.openPlaylist(playlistId) }

    val playlist by viewModel.openedPlaylist.collectAsStateWithLifecycle()
    val tracks by viewModel.openedPlaylistTracks.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val name = playlist?.name ?: ""

    TrackList(
        tracks = tracks,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_playlist_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it, playlistId = playlistId) },
    ) {
        CollectionHeader(
            title = name,
            subtitle = pluralTracks(tracks.size),
            cover = tracks.firstOrNull()?.cover,
            icon = Icons.Outlined.QueueMusic,
            tracks = tracks,
            onBack = onBack,
            onPlay = onPlay,
            trailing = {
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename), tint = VotifyColors.TextSecondary)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete), tint = VotifyColors.TextSecondary)
                }
            },
        )
    }

    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename),
            confirm = stringResource(R.string.action_save),
            initial = name,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                viewModel.renamePlaylist(playlistId, it)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = stringResource(R.string.dialog_delete_playlist, name),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deletePlaylist(playlistId)
                onBack()
            },
        )
    }
}
