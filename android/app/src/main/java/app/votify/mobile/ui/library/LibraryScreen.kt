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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
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
    onImport: () -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

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
                Spacer(Modifier.height(12.dp))
                ImportServiceCard(onImport = onImport, modifier = Modifier.padding(horizontal = 16.dp))
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
                        downloaded = t.id in downloadedIds,
                        downloadFraction = downloadProgress[t.id]?.fraction,
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

/** «Импортировать из сервиса» card (design/screens/library.png): opens the import screen. */
@Composable
private fun ImportServiceCard(onImport: () -> Unit, modifier: Modifier = Modifier) {
    VotifyCard(modifier.fillMaxWidth(), onClick = onImport, contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VotifyColors.SurfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Download, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.library_import),
                    style = MaterialTheme.typography.titleSmall,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.library_import_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
        }
    }
}

@Composable
private fun FavoritesHero(favorites: List<Track>, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    VotifyCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 2x2 cover mosaic (or a heart when empty)
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(VotifyColors.SurfaceContainerHigh),
            ) {
                if (favorites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(28.dp))
                    }
                } else {
                    val cells = listOf(
                        Alignment.TopStart, Alignment.TopEnd,
                        Alignment.BottomStart, Alignment.BottomEnd,
                    )
                    cells.forEachIndexed { i, align ->
                        Box(Modifier.align(align).size(37.dp).padding(1.dp)) {
                            val cover = favorites.getOrNull(i)?.cover.orEmpty()
                            if (i == 3 && favorites.size > 4) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VotifyColors.SurfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "+${favorites.size - 3}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VotifyColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Artwork(cover, size = 37.dp, shape = RoundedCornerShape(8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.library_favorites),
                    style = MaterialTheme.typography.titleLarge,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (favorites.isEmpty()) stringResource(R.string.library_favorites_empty)
                    else pluralTracks(favorites.size) + " · " + stringResource(R.string.library_favorites_play),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircleIconButton(onClick = onPlay, size = 48.dp, filled = true) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play), modifier = Modifier.size(26.dp))
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
