package app.votify.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.votify.mobile.data.Track
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.SubcomposeAsyncImage

/** Square artwork with a graceful placeholder. */
@Composable
fun Artwork(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(VotifyColors.SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Icon(Icons.Default.MusicNote, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(size * 0.45f))
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Icon(Icons.Default.MusicNote, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(size * 0.45f))
                },
            )
        }
    }
}

/**
 * Track row from the design: 56dp, 44dp thumbnail, title (#E5E5EA) + artist (#8E8E93),
 * trailing overflow button. Highlights the currently playing track.
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.cover, size = 44.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) VotifyColors.TextPrimary else VotifyColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(track.artist)
                    if (track.duration > 0) append(" • ").append(formatDuration(track.duration * 1000L))
                },
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, null, tint = VotifyColors.TextMuted)
            }
        }
    }
}

/** Pill filter chip: white fill when selected, #1E1E1E + hairline border otherwise. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainer,
        contentColor = if (selected) VotifyColors.OnPrimary else VotifyColors.TextMuted,
        border = if (selected) null else BorderStroke(1.dp, VotifyColors.BorderSubtle),
        modifier = modifier.height(36.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

/** Level-2 card: #1E1E1E surface with a 1px #2A2A2A border and 16dp corners. */
@Composable
fun VotifyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    color: Color = VotifyColors.SurfaceContainer,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(color)
        .border(1.dp, VotifyColors.BorderSubtle, shape)
    Box(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(contentPadding),
    ) { content() }
}

/** Circular 40dp icon action button on a #1E1E1E disc. */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    filled: Boolean = false,
    contentDescription: String? = null,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) VotifyColors.Primary else VotifyColors.SurfaceContainer,
        contentColor = if (filled) VotifyColors.OnPrimary else VotifyColors.TextPrimary,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

/** Section header row: "СЛУШАЙТЕ ПРЯМО СЕЙЧАС ......... Все". */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = VotifyColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
