package app.votify.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.player.PlayerUiState
import app.votify.mobile.ui.theme.VotifyColors

/** Visual style of the mini-player (Плеер → Мини-плеер settings). */
data class MiniStyle(
    val pillShape: Boolean = true,
    val roundCover: Boolean = false,
    val ringProgress: Boolean = false,
    val barProgress: Boolean = true,
    val showLike: Boolean = true,
    val filledPlay: Boolean = true,
    val artworkTint: Boolean = false,
)

/**
 * Docked mini-player from the design: 64dp tall, 8dp side margins, #1E1E1E @ 90% with a
 * #383838 hairline, 44dp artwork, title/artist, ♥ and a 36dp white play/pause disc,
 * 2px progress track along the bottom edge.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    style: MiniStyle = MiniStyle(),
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val shape = if (style.pillShape) RoundedCornerShape(24.dp) else RoundedCornerShape(14.dp)
    val tint = if (style.artworkTint) rememberDominantTint(track.cover) else null

    Box(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(tint?.copy(alpha = 0.92f) ?: VotifyColors.SurfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, VotifyColors.SurfaceContainerHighest, shape)
            .clickable(onClick = onClick)
            .pointerInput(onSwipeLeft, onSwipeRight) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -120f -> onSwipeLeft()
                            total > 120f -> onSwipeRight()
                        }
                    },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (style.ringProgress) {
                    val ringTrack = VotifyColors.SurfaceContainerHighest
                    val ringActive = VotifyColors.Primary
                    val progress = state.progress
                    Canvas(Modifier.size(50.dp)) {
                        drawCircle(color = ringTrack, style = Stroke(width = 3.dp.toPx()))
                        drawArc(
                            color = ringActive,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
                Artwork(track.cover, size = 44.dp, shape = if (style.roundCover) CircleShape else RoundedCornerShape(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VotifyColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                onClick = onPlayPause,
                size = 36.dp,
                filled = style.filledPlay,
                contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
            ) {
                if (state.isBuffering && !state.isPlaying) {
                    CircularProgressIndicator(
                        color = VotifyColors.OnPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (style.showLike) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_favorite),
                        tint = VotifyColors.TextPrimary,
                    )
                }
            }
        }

        // 2px progress line along the bottom edge
        if (style.barProgress) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(VotifyColors.SurfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress)
                        .fillMaxHeight()
                        .background(VotifyColors.Primary),
                )
            }
        }
    }
}

/** Dominant cover color for the tinted mini-player background. */
@Composable
private fun rememberDominantTint(cover: String?): Color? {
    if (cover.isNullOrBlank()) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    var tint by androidx.compose.runtime.remember(cover) { androidx.compose.runtime.mutableStateOf<Color?>(null) }
    androidx.compose.runtime.LaunchedEffect(cover) {
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            val request = coil.request.ImageRequest.Builder(context).data(cover).size(64).allowHardware(false).build()
            val bitmap = loader.execute(request).drawable?.let { d ->
                (d as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: d.toBitmap()
            }
            tint = bitmap?.let {
                androidx.palette.graphics.Palette.from(it).clearFilters().generate()
                    .dominantSwatch?.let { sw -> Color(sw.rgb).copy(alpha = 0.45f) }
            }
        }
    }
    return tint
}

