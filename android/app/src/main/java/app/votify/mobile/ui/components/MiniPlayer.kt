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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
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

private enum class MiniDragDirection { NONE, HORIZONTAL, VERTICAL_DOWN, VERTICAL_UP }

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
    onDismiss: () -> Unit = {},
    style: MiniStyle = MiniStyle(),
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val shape = if (style.pillShape) RoundedCornerShape(24.dp) else RoundedCornerShape(14.dp)
    val tint = if (style.artworkTint) rememberDominantTint(track.cover) else null

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 44.dp.toPx() }

    Box(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                translationY = offsetY.value
                alpha = (1f - (offsetY.value / 180f)).coerceIn(0.1f, 1f)
            }
            .clip(shape)
            .background(tint?.copy(alpha = 0.92f) ?: VotifyColors.SurfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, VotifyColors.SurfaceContainerHighest, shape)
            .clickable(onClick = onClick)
            .pointerInput(onSwipeLeft, onSwipeRight, onDismiss) {
                var totalX = 0f
                var totalY = 0f
                var direction = MiniDragDirection.NONE

                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        direction = MiniDragDirection.NONE
                    },
                    onDragEnd = {
                        if (direction == MiniDragDirection.VERTICAL_DOWN && totalY > dismissThresholdPx) {
                            scope.launch {
                                offsetY.animateTo(120f, tween(120))
                                onDismiss()
                            }
                        } else {
                            if (offsetY.value > 0f) {
                                scope.launch {
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            }
                            if (direction == MiniDragDirection.HORIZONTAL || (abs(totalX) > abs(totalY) && abs(totalX) > 100f)) {
                                when {
                                    totalX < -110f -> onSwipeLeft()
                                    totalX > 110f -> onSwipeRight()
                                }
                            } else if (direction == MiniDragDirection.VERTICAL_UP && totalY < -70f) {
                                onClick()
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y

                        if (direction == MiniDragDirection.NONE) {
                            if (totalY > 12f && totalY > abs(totalX) * 1.1f) {
                                direction = MiniDragDirection.VERTICAL_DOWN
                            } else if (totalY < -15f && abs(totalY) > abs(totalX) * 1.1f) {
                                direction = MiniDragDirection.VERTICAL_UP
                            } else if (abs(totalX) > 15f && abs(totalX) > abs(totalY)) {
                                direction = MiniDragDirection.HORIZONTAL
                            }
                        }

                        if (direction == MiniDragDirection.VERTICAL_DOWN) {
                            val clamped = totalY.coerceAtLeast(0f)
                            scope.launch { offsetY.snapTo(clamped) }
                        }
                    },
                )
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
                // Та же приглушённая акцентная пара, что у большой кнопки «Плей» на главной.
                fillColor = if (style.filledPlay) VotifyColors.AccentFill else null,
                tintColor = if (style.filledPlay) VotifyColors.AccentContent else null,
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

