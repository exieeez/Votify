package app.votify.mobile.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.theme.VotifyColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
    ) {
        HomeHeader(onOpenSearch = onOpenSearch)

        // --- "Моя волна": big white Play with an orbit of artwork bubbles ---
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(color = VotifyColors.TextMuted, strokeWidth = 2.dp)
                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error), color = VotifyColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(state.error ?: "", color = VotifyColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = viewModel::refresh)
                }
                else -> WaveOrbit(
                    tracks = state.wave,
                    onPlayWave = { onPlay(state.wave, 0) },
                    onPlayTrack = { index -> onPlay(state.wave, index) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- Recent ---
        VotifyCard(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = { /* history: next iteration */ },
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                            .graphicsLayer { rotationZ = 6f }
                            .clip(RoundedCornerShape(8.dp))
                            .background(VotifyColors.SurfaceContainerHighest),
                    )
                    Artwork(
                        url = state.wave.firstOrNull()?.cover.orEmpty(),
                        size = 40.dp,
                        modifier = Modifier.align(Alignment.TopStart).border(1.dp, VotifyColors.BorderProminent, RoundedCornerShape(8.dp)),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.home_tracks_count, state.wave.size), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = VotifyColors.TextMuted)
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Quick tiles: Favorites / Trending ---
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(
                title = stringResource(R.string.home_favorites),
                subtitle = stringResource(R.string.home_playlist),
                icon = Icons.Filled.Favorite,
                modifier = Modifier.weight(1f),
                onClick = { /* favorites: next iteration */ },
            )
            QuickTile(
                title = stringResource(R.string.home_trending),
                subtitle = stringResource(R.string.home_weekly_chart),
                icon = Icons.Filled.TrendingUp,
                modifier = Modifier.weight(1f),
                onClick = { if (state.wave.isNotEmpty()) onPlay(state.wave, 0) },
            )
        }
    }
}

/** Header: "Votify" brand pill on the left, bell / search / avatar on the right. */
@Composable
private fun HomeHeader(onOpenSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = VotifyColors.SurfaceContainer,
            border = BorderStroke(1.dp, VotifyColors.BorderSubtle),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_votify_mark), null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Votify", style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(onClick = {}, size = 36.dp) {
            Icon(Icons.Outlined.Notifications, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = onOpenSearch, size = 36.dp, contentDescription = stringResource(R.string.nav_search)) {
            Icon(Icons.Outlined.Search, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = {}, size = 36.dp) {
            Icon(Icons.Outlined.Person, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The hero of the home screen: a 72dp white Play disc in the centre with up to 8 artwork
 * bubbles scattered on an orbit around it. The orbit slowly drifts; tapping a bubble plays
 * that track, tapping Play starts the whole wave.
 */
@Composable
private fun WaveOrbit(
    tracks: List<Track>,
    onPlayWave: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    val bubbles = tracks.take(8)
    val drift by rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )

    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        bubbles.forEachIndexed { i, track ->
            // Alternate two radii and two sizes so the layout reads as a loose constellation.
            val radius: Dp = if (i % 2 == 0) 118.dp else 92.dp
            val size: Dp = if (i % 3 == 0) 56.dp else 48.dp
            val angle = Math.toRadians((i * (360.0 / bubbles.size)) - 90 + drift)
            val dx = (radius.value * cos(angle)).toFloat().dp
            val dy = (radius.value * sin(angle)).toFloat().dp
            Box(
                Modifier
                    .offset(x = dx, y = dy)
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, VotifyColors.BorderProminent.copy(alpha = 0.6f), CircleShape)
                    .clickable { onPlayTrack(i) },
            ) {
                Artwork(track.cover, size = size, shape = RoundedCornerShape(50), contentDescription = track.title)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onPlayWave,
                shape = CircleShape,
                color = VotifyColors.Primary,
                contentColor = VotifyColors.OnPrimary,
                shadowElevation = 0.dp,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.home_play_wave),
                        modifier = Modifier.size(34.dp).offset(x = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.home_my_wave),
                style = MaterialTheme.typography.titleMedium,
                color = VotifyColors.TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    VotifyCard(modifier.aspectRatio(1.55f), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Box(Modifier.fillMaxSize()) {
            Icon(icon, null, tint = VotifyColors.TextMuted.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopEnd).size(28.dp))
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
            }
        }
    }
}
