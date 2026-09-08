package app.votify.mobile.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.votify.mobile.R
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.Lyrics as LyricsModel
import app.votify.mobile.player.PlayerUiState
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.formatDuration
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.AsyncImage

/**
 * Full-screen "Сейчас играет": header, artwork (vinyl / square / blur per settings) that swaps
 * to synced lyrics, title/artist with ♥ and "add to playlist", scrubber, transport panel and a
 * secondary row (lyrics / queue).
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    artworkStyle: ArtworkStyle,
    isFavorite: Boolean,
    lyricsVisible: Boolean,
    lyrics: LyricsState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val track = state.current

    Box(Modifier.fillMaxSize().background(VotifyColors.SurfaceBase)) {
        if (artworkStyle == ArtworkStyle.Blur && !track?.cover.isNullOrBlank()) {
            AsyncImage(
                model = track?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp),
                alpha = 0.35f,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, VotifyColors.SurfaceBase.copy(alpha = 0.85f), VotifyColors.SurfaceBase)),
                ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.player_collapse), tint = VotifyColors.TextPrimary, modifier = Modifier.size(28.dp))
                }
                Text(
                    stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = {}) { Icon(Icons.Outlined.Share, null, tint = VotifyColors.TextSecondary) }
                IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, null, tint = VotifyColors.TextSecondary) }
            }

            // Artwork / lyrics area
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = lyricsVisible,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "artwork-lyrics",
                ) { showLyrics ->
                    if (showLyrics) {
                        LyricsPanel(state = lyrics, positionMs = state.positionMs, onSeekToMs = onSeekToMs, onClose = onToggleLyrics)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            when (artworkStyle) {
                                ArtworkStyle.Vinyl -> Vinyl(coverUrl = track?.cover.orEmpty(), spinning = state.isPlaying)
                                ArtworkStyle.Square, ArtworkStyle.Blur -> SquareArtwork(coverUrl = track?.cover.orEmpty())
                            }
                            if (state.isBuffering) {
                                CircularProgressIndicator(color = VotifyColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(280.dp))
                            }
                        }
                    }
                }
            }

            // Title / artist with ♥ and ⊕ on the sides
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.player_favorite),
                        tint = VotifyColors.TextPrimary,
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track?.title ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        track?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VotifyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = !track?.artist.isNullOrBlank()) { track?.artist?.let(onOpenArtist) },
                    )
                }
                IconButton(onClick = onAddToPlaylist) { Icon(Icons.Outlined.PlaylistAdd, stringResource(R.string.action_add_to_playlist), tint = VotifyColors.TextPrimary) }
            }

            Spacer(Modifier.height(4.dp))

            Scrubber(state = state, onSeek = onSeek)

            Spacer(Modifier.height(10.dp))

            // Transport panel
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VotifyColors.SurfaceContainerLow.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            stringResource(R.string.player_repeat),
                            tint = if (repeatOn) VotifyColors.TextPrimary else VotifyColors.TextMuted,
                        )
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, stringResource(R.string.player_previous), tint = VotifyColors.TextPrimary, modifier = Modifier.size(32.dp))
                    }
                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = VotifyColors.Primary,
                        contentColor = VotifyColors.OnPrimary,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp), enabled = state.hasNext) {
                        Icon(Icons.Filled.SkipNext, stringResource(R.string.player_next), tint = VotifyColors.TextPrimary, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onToggleShuffle) {
                        Icon(Icons.Filled.Shuffle, stringResource(R.string.player_shuffle), tint = if (state.shuffle) VotifyColors.TextPrimary else VotifyColors.TextMuted)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Secondary row: lyrics / queue
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VotifyColors.SurfaceContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleLyrics) {
                        Icon(Icons.Outlined.Lyrics, stringResource(R.string.player_lyrics), tint = if (lyricsVisible) VotifyColors.TextPrimary else VotifyColors.TextSecondary)
                    }
                    Box {
                        IconButton(onClick = onOpenQueue) { Icon(Icons.Outlined.FormatListBulleted, stringResource(R.string.player_queue), tint = VotifyColors.TextSecondary) }
                        if (state.queue.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = VotifyColors.Primary,
                                contentColor = VotifyColors.OnPrimary,
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp),
                            ) {
                                Text(
                                    state.queue.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Artwork inside a spinning vinyl disc: dark gradient platter, cover, centre spindle. */
@Composable
private fun Vinyl(coverUrl: String, spinning: Boolean) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    if (spinning) frozenAngle = angle

    Box(
        Modifier
            .size(280.dp)
            .rotate(if (spinning) angle else frozenAngle)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(VotifyColors.SurfaceContainerLowest, VotifyColors.SurfaceContainerHigh, VotifyColors.SurfaceContainerLowest),
                ),
            )
            .border(1.dp, VotifyColors.BorderProminent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        for (r in listOf(250, 220, 190)) {
            Box(Modifier.size(r.dp).border(1.dp, Color.White.copy(alpha = 0.04f), CircleShape))
        }
        Artwork(coverUrl, size = 176.dp, shape = RoundedCornerShape(50))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(VotifyColors.SurfaceContainerHigh)
                .border(1.dp, VotifyColors.BorderProminent, CircleShape),
        )
    }
}

@Composable
private fun SquareArtwork(coverUrl: String) {
    Box(
        Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, VotifyColors.BorderSubtle, RoundedCornerShape(24.dp)),
    ) {
        Artwork(coverUrl, size = 400.dp, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxSize())
    }
}

/** Synced lyrics: active line in white and bold, auto-scrolls to keep it in view, tap to seek. */
@Composable
private fun LyricsPanel(state: LyricsState, positionMs: Long, onSeekToMs: (Long) -> Unit, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(VotifyColors.SurfaceContainerLow.copy(alpha = 0.6f)),
    ) {
        when (state) {
            LyricsState.Hidden, LyricsState.Loading -> CircularProgressIndicator(
                color = VotifyColors.TextMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center),
            )

            LyricsState.NotFound -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.lyrics_not_found), color = VotifyColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lyrics_back_to_cover),
                    color = VotifyColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }

            is LyricsState.Loaded -> LyricsList(lyrics = state.lyrics, positionMs = positionMs, onSeekToMs = onSeekToMs)
        }
    }
}

@Composable
private fun LyricsList(lyrics: LyricsModel, positionMs: Long, onSeekToMs: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val active = lyrics.activeIndex(positionMs)

    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(index = active, scrollOffset = -220)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines) { i, line ->
            val isActive = lyrics.synced && i == active
            val isPast = lyrics.synced && i < active
            Text(
                text = line.text.ifBlank { "♪" },
                style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isActive -> VotifyColors.TextPrimary
                    isPast -> VotifyColors.TextMuted.copy(alpha = 0.6f)
                    lyrics.synced -> VotifyColors.TextMuted
                    else -> VotifyColors.TextSecondary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = lyrics.synced) { onSeekToMs(line.timeMs) },
            )
        }
    }
}

@Composable
private fun Scrubber(state: PlayerUiState, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: state.progress
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onSeek)
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = VotifyColors.Primary,
                activeTrackColor = VotifyColors.Primary,
                inactiveTrackColor = VotifyColors.SurfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging != null) (state.durationMs * value).toLong() else state.positionMs
            Text(formatDuration(shown), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
        }
    }
}
