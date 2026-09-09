package app.votify.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.ui.theme.VotifyColors

/** Shared chrome for our bottom sheets: #1E1E1E, 24dp top corners, drag handle in muted grey. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotifySheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VotifyColors.SurfaceContainer,
        contentColor = VotifyColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(VotifyColors.BorderProminent),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding()) { content() }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) VotifyColors.Error else VotifyColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) VotifyColors.Error else VotifyColors.TextPrimary)
    }
}

/** Track header used at the top of the "⋮" menu. */
@Composable
private fun SheetTrackHeader(track: Track) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.cover, size = 48.dp, shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(VotifyColors.BorderSubtle))
}

/** The "⋮" menu for a track. [playlistId] != null adds "remove from this playlist". */
@Composable
fun TrackMenuSheet(
    track: Track,
    isFavorite: Boolean,
    playlistId: Long?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onOpenArtist: () -> Unit,
    downloaded: Boolean = false,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
) {
    VotifySheet(onDismiss = onDismiss) {
        SheetTrackHeader(track)
        if (downloaded) {
            SheetAction(Icons.Outlined.DownloadDone, stringResource(R.string.action_delete_download), onRemoveDownload)
        } else {
            SheetAction(Icons.Outlined.Download, stringResource(R.string.action_download), onDownload)
        }
        SheetAction(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
            onClick = onToggleFavorite,
        )
        SheetAction(Icons.Outlined.SkipNext, stringResource(R.string.action_play_next), onPlayNext)
        SheetAction(Icons.Outlined.QueueMusic, stringResource(R.string.action_add_to_queue), onAddToQueue)
        SheetAction(Icons.Outlined.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist)
        if (playlistId != null) {
            SheetAction(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.action_remove_from_playlist), onRemoveFromPlaylist, destructive = true)
        }
        if (track.artist.isNotBlank() && track.artist != "Unknown") {
            SheetAction(Icons.Outlined.Person, stringResource(R.string.action_go_to_artist), onOpenArtist)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Picker: existing playlists + "create new" (which also adds the track). */
@Composable
fun PlaylistPickerSheet(
    track: Track,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onPick: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }

    VotifySheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.action_add_to_playlist),
            style = MaterialTheme.typography.titleMedium,
            color = VotifyColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SheetAction(Icons.Outlined.Add, stringResource(R.string.library_new_playlist), onClick = { creating = true })
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(p) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.cover.orEmpty(), size = 44.dp, shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralTracks(p.trackCount), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.library_new_playlist),
            confirm = stringResource(R.string.action_create),
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    confirm: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VotifyColors.SurfaceContainerHigh,
        titleContentColor = VotifyColors.TextPrimary,
        textContentColor = VotifyColors.TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_playlist_name)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VotifyColors.TextPrimary,
                    unfocusedTextColor = VotifyColors.TextPrimary,
                    cursorColor = VotifyColors.TextPrimary,
                    focusedBorderColor = VotifyColors.TextPrimary,
                    unfocusedBorderColor = VotifyColors.BorderProminent,
                    focusedLabelColor = VotifyColors.TextSecondary,
                    unfocusedLabelColor = VotifyColors.TextMuted,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirm, color = VotifyColors.TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = VotifyColors.TextMuted) }
        },
    )
}

@Composable
fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VotifyColors.SurfaceContainerHigh,
        textContentColor = VotifyColors.TextPrimary,
        shape = RoundedCornerShape(24.dp),
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = VotifyColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = VotifyColors.TextMuted) }
        },
    )
}

/** Playback queue: current track pinned on top, the rest tappable. */
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    VotifySheet(onDismiss = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleMedium, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(pluralTracks(queue.size), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                if (i == currentIndex) {
                    SectionLabel(stringResource(R.string.queue_now))
                } else if (i == currentIndex + 1) {
                    SectionLabel(stringResource(R.string.queue_next))
                }
                TrackRow(track = t, isCurrent = i == currentIndex, onClick = { onPick(i) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = VotifyColors.TextMuted,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}
