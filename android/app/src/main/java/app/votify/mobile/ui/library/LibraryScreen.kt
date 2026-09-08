package app.votify.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.PlaylistNameDialog
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors

private enum class LibraryFilter(val label: Int) {
    All(R.string.library_filter_all),
    Playlists(R.string.library_filter_playlists),
    History(R.string.library_filter_history),
}

/**
 * «Моя Медиатека»: Favourites hero (live count + play), playlists grid with "new playlist",
 * recent history preview. All data comes from Room via [LibraryViewModel].
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var creating by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(onClick = onOpenSettings, size = 36.dp, contentDescription = stringResource(R.string.nav_settings)) {
                    Icon(Icons.Outlined.Settings, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(LibraryFilter.entries) { f ->
                    PillChip(text = stringResource(f.label), selected = f == filter, onClick = { filter = f })
                }
            }
        }

        if (filter == LibraryFilter.All) {
            item {
                FavoritesHero(
                    favorites = favorites,
                    onOpen = onOpenFavorites,
                    onPlay = { if (favorites.isNotEmpty()) onPlay(favorites, 0) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (filter == LibraryFilter.All || filter == LibraryFilter.Playlists) {
            item {
                SectionHeader(
                    title = stringResource(R.string.library_playlists),
                    action = stringResource(R.string.library_new_playlist),
                    onAction = { creating = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyHint(
                        text = stringResource(R.string.library_playlists_empty),
                        icon = Icons.Outlined.QueueMusic,
                        actionLabel = stringResource(R.string.action_create),
                        onAction = { creating = true },
                    )
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(playlists, key = { it.id }) { p ->
                            PlaylistCard(p, onClick = { onOpenPlaylist(p.id) })
                        }
                        item {
                            NewPlaylistCard(onClick = { creating = true })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (filter == LibraryFilter.All || filter == LibraryFilter.History) {
            item {
                SectionHeader(
                    title = stringResource(R.string.library_history),
                    action = if (recent.isNotEmpty()) stringResource(R.string.action_more) else null,
                    onAction = onOpenHistory,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (recent.isEmpty()) {
                item { EmptyHint(text = stringResource(R.string.library_history_empty), icon = Icons.Outlined.History) }
            } else {
                val shown = if (filter == LibraryFilter.History) recent else recent.take(6)
                items(shown, key = { it.id }) { t ->
                    TrackRow(
                        track = t,
                        isCurrent = t.id == currentTrackId,
                        onClick = { onPlay(shown, shown.indexOf(t)) },
                        onMore = { viewModel.openMenu(t) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.library_new_playlist),
            confirm = stringResource(R.string.action_create),
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                viewModel.createPlaylist(name)
            },
        )
    }
}

@Composable
private fun FavoritesHero(favorites: List<Track>, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    VotifyCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(20.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(listOf(VotifyColors.SurfaceContainerHigh.copy(alpha = 0.6f), VotifyColors.SurfaceContainer)),
                ),
        ) {
            // Fan of the 3 latest covers behind the heart, if any.
            Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                favorites.take(3).forEach { t ->
                    Artwork(
                        t.cover,
                        size = 48.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, VotifyColors.BorderProminent, RoundedCornerShape(12.dp)),
                    )
                }
                if (favorites.isEmpty()) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(VotifyColors.SurfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Favorite, null, tint = VotifyColors.TextPrimary) }
                }
            }

            CircleIconButton(onClick = onPlay, size = 48.dp, filled = true, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play), modifier = Modifier.size(26.dp))
            }

            Column(Modifier.align(Alignment.BottomStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_favorites), style = MaterialTheme.typography.headlineMedium, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (favorites.isEmpty()) stringResource(R.string.library_favorites_empty) else pluralTracks(favorites.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(p: PlaylistSummary, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(VotifyColors.SurfaceContainer)
                .border(1.dp, VotifyColors.BorderSubtle, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (p.cover.isNullOrBlank()) {
                Icon(Icons.Outlined.QueueMusic, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(40.dp))
            } else {
                Artwork(p.cover.orEmpty(), size = 140.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pluralTracks(p.trackCount), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, VotifyColors.BorderProminent, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.library_new_playlist), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun EmptyHint(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = VotifyColors.TextMuted, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                PillChip(text = actionLabel, selected = true, onClick = onAction)
            }
        }
    }
}
