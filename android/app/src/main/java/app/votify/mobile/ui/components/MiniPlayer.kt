package app.votify.mobile.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.player.PlayerUiState
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Docked mini-player from the design: 64dp tall, 8dp side margins, #1E1E1E @ 90% with a
 * #383838 hairline, 44dp artwork, title/artist, ♥ and a 36dp white play/pause disc,
 * 2px progress track along the bottom edge.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(VotifyColors.SurfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, VotifyColors.SurfaceContainerHighest, shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.cover, size = 44.dp, shape = RoundedCornerShape(12.dp))
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
                filled = true,
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
            IconButton(onClick = { /* favorites: next iteration */ }) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.player_favorite),
                    tint = VotifyColors.TextPrimary,
                )
            }
        }

        // 2px progress line along the bottom edge
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

