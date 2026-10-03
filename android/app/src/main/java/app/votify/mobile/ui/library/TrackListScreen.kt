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
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
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
import app.votify.mobile.data.DownloadProgress
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
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, DownloadProgress> = emptyMap(),
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
                downloaded = t.id in downloadedIds,
                downloadFraction = downloadProgress[t.id]?.fraction,
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
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    TrackList(
        tracks = favorites,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_favorites_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
        downloadedIds = downloadedIds,
        downloadProgress = downloadProgress,
        header = {
            CollectionHeader(
                title = stringResource(R.string.library_favorites),
                subtitle = pluralTracks(favorites.size),
                cover = favorites.firstOrNull()?.cover,
                icon = Icons.Filled.Favorite,
                tracks = favorites,
                onBack = onBack,
                onPlay = onPlay,
            )
        },
    )
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
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    TrackList(
        tracks = recent,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_history_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
        downloadedIds = downloadedIds,
        downloadProgress = downloadProgress,
        header = {
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
        },
    )

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
fun PlaylistVinylHeader(
    title: String,
    tracks: List<Track>,
    coverUrl: String?,
    isPlaying: Boolean,
    isCurrentPlaylist: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    trailingActions: @Composable () -> Unit,
) {
    val totalSeconds = remember(tracks) {
        tracks.sumOf { it.duration.toLong().coerceAtLeast(0L) }
    }
    val formattedTotalTime = remember(totalSeconds) {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        if (h > 0) {
            "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        } else {
            "$m:${s.toString().padStart(2, '0')}"
        }
    }

    val spinning = isPlaying && isCurrentPlaylist

    val transition = rememberInfiniteTransition(label = "vinyl_spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    if (spinning) frozenAngle = angle

    val vinylOffset by animateDpAsState(
        targetValue = if (spinning) 48.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "vinyl_offset",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding + 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top App Bar: Back arrow, "Playlist", trailing menu actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.library_playlist),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            trailingActions()
        }

        Spacer(Modifier.height(18.dp))

        // Center Vinyl + Album Cover
        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 200.dp)
                .clickable { onPlayAll() },
            contentAlignment = Alignment.Center,
        ) {
            // Spinning vinyl disc behind the cover
            Box(
                modifier = Modifier
                    .offset(x = vinylOffset)
                    .size(185.dp)
                    .rotate(if (spinning) angle else frozenAngle)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF262626),
                                Color(0xFF141414),
                                Color(0xFF0a0a0a),
                                Color(0xFF1c1c1c),
                                Color(0xFF080808),
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF444444).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Concentric vinyl grooves
                for (r in listOf(168, 148, 128, 108, 88)) {
                    Box(
                        Modifier
                            .size(r.dp)
                            .border(0.6.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                }
                // Center circular label
                if (!coverUrl.isNullOrBlank()) {
                    Artwork(
                        coverUrl,
                        size = 62.dp,
                        shape = RoundedCornerShape(50),
                    )
                } else {
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF202020))
                    )
                }
                // Center metallic spindle hole
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF888888), CircleShape)
                )
            }

            // Front Album Cover with shadow
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(14.dp), clip = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VotifyColors.SurfaceContainer)
                    .border(1.dp, VotifyColors.BorderSubtle, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        tint = VotifyColors.TextSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Artwork(
                        coverUrl,
                        size = 190.dp,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Playlist Title (bold, centered)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(6.dp))

        // Subtitle: Playlist • 75 Tracks • 3:24:29
        val tracksCountText = pluralTracks(tracks.size)
        val subtitleText = if (totalSeconds > 0) {
            "Playlist • $tracksCountText • $formattedTotalTime"
        } else {
            "Playlist • $tracksCountText"
        }
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(18.dp))

        // Action Buttons Row (Heart, 3-dots, Shuffle, Big Play Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Favorite icon
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
            )

            Spacer(Modifier.weight(1f))

            // Shuffle Button
            IconButton(
                onClick = onShuffle,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.action_shuffle_all),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Big Round Play Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPlayAll() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (spinning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (spinning) R.string.player_pause else R.string.player_play),
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    viewModel: LibraryViewModel,
    playlistId: Long,
    currentTrackId: String?,
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LaunchedEffect(playlistId) { viewModel.openPlaylist(playlistId) }

    val playlist by viewModel.openedPlaylist.collectAsStateWithLifecycle()
    val tracks by viewModel.openedPlaylistTracks.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val name = playlist?.name ?: ""
    val coverUrl = tracks.firstOrNull { !it.cover.isNullOrBlank() }?.cover

    val isCurrentPlaylist = tracks.any { it.id == currentTrackId }

    Box(Modifier.fillMaxSize()) {
        // Heavily blurred background cover image
        if (!coverUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
                    .alpha(0.45f),
            )
        }

        // Gradient overlay fading into deep black surface
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.8f),
                            Color(0xFF0c0d10).copy(alpha = 0.96f),
                            Color(0xFF0c0d10),
                        )
                    )
                )
        )

        // Tracklist with the vinyl header
        TrackList(
            tracks = tracks,
            currentTrackId = currentTrackId,
            contentPadding = contentPadding,
            emptyText = stringResource(R.string.library_playlist_empty),
            onPlay = onPlay,
            onMore = { viewModel.openMenu(it, playlistId = playlistId) },
            downloadedIds = downloadedIds,
            downloadProgress = downloadProgress,
            header = {
                PlaylistVinylHeader(
                    title = name,
                    tracks = tracks,
                    coverUrl = coverUrl,
                    isPlaying = isPlaying,
                    isCurrentPlaylist = isCurrentPlaylist,
                    topPadding = contentPadding.calculateTopPadding(),
                    onBack = onBack,
                    onPlayAll = {
                        if (tracks.isNotEmpty()) {
                            if (isCurrentPlaylist) {
                                onTogglePlay()
                            } else {
                                onPlay(tracks, 0)
                            }
                        }
                    },
                    onShuffle = {
                        if (tracks.isNotEmpty()) onPlay(tracks.shuffled(), 0)
                    },
                    trailingActions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_download_playlist)) },
                                    leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.downloadPlaylist(tracks)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        renaming = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    },
                )
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
