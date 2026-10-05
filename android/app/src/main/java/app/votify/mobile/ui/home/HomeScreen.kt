package app.votify.mobile.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.votify.mobile.ui.components.LocalLiquidGlass
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.liquidGlass
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    playerState: app.votify.mobile.player.PlayerUiState,
    onSeek: (Float) -> Unit,
    iosSlider: Boolean,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTrending: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
    ) {
        HomeHeader(
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenAccount = onOpenAccount,
            onOpenSite = onOpenSite,
        )

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
                state.wave.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.home_wave_empty), color = VotifyColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
                }
                else -> WaveOrbit(
                    tracks = state.wave,
                    onPlayWave = { onPlay(state.wave, 0) },
                    onPlayTrack = { index -> onPlay(state.wave, index) },
                )
            }
        }

        // Под «Моей волной» — одна строка текста текущей песни (как в Spotify под обложкой).
        val lyricLine = homeLyricLine(viewModel, playerState)
        if (!lyricLine.isNullOrBlank()) {
            // Одна строка, которая влезает целиком: длинную строку печатаем мельче.
            val lyricSize = when {
                lyricLine.length > 64 -> 11.sp
                lyricLine.length > 44 -> 12.sp
                else -> 14.sp
            }
            Text(
                lyricLine,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = lyricSize, lineHeight = lyricSize * 1.25f),
                color = VotifyColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }

        if (!state.isLoading && state.error == null) {
            Text(
                text = when (state.waveSource) {
                    WaveSource.Personal -> stringResource(R.string.home_wave_personal, state.seeds.take(3).joinToString(", "))
                    WaveSource.Generic -> stringResource(R.string.home_wave_generic)
                },
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // --- "Недавние" card (original compact stacked layout) ---
        VotifyCard(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            onClick = onOpenHistory,
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp)) {
                    val second = recent.getOrNull(1)?.cover.orEmpty()
                    if (second.isNotBlank()) {
                        Artwork(
                            url = second,
                            size = 40.dp,
                            modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { rotationZ = 6f },
                        )
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                                .graphicsLayer { rotationZ = 6f }
                                .clip(RoundedCornerShape(8.dp))
                                .background(VotifyColors.SurfaceContainerHighest),
                        )
                    }
                    Artwork(
                        url = recent.firstOrNull()?.cover.orEmpty(),
                        size = 40.dp,
                        modifier = Modifier.align(Alignment.TopStart).border(1.dp, VotifyColors.BorderProminent, RoundedCornerShape(8.dp)),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (recent.isEmpty()) stringResource(R.string.home_recent_empty)
                        else recent.first().title + " · " + recent.first().artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (recent.isNotEmpty()) {
                    CircleIconButton(onClick = { onPlay(recent, 0) }, size = 36.dp, contentDescription = stringResource(R.string.player_play)) {
                        Icon(Icons.Filled.PlayArrow, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = VotifyColors.TextMuted)
            }
        }

        // --- Main Section: "Для вас" (replacing "Популярное") ---
        Text(
            text = "Для вас",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
        )

        // 1. "Треки"
        Text(
            text = "Треки",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = VotifyColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        val forYouTracks = state.forYouTracks.ifEmpty { state.wave }
        if (forYouTracks.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(forYouTracks) { index, track ->
                    TrackCard(
                        track = track,
                        onClick = { onPlay(forYouTracks, index) },
                    )
                }
            }
        }

        // 2. "Плейлисты"
        Text(
            text = "Плейлисты",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = VotifyColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        )
        if (state.forYouPlaylists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.forYouPlaylists) { pl ->
                    PlaylistCard(
                        playlist = pl,
                        onClick = {
                            if (pl.isLocal) {
                                onOpenPlaylist(pl.id)
                            } else {
                                viewModel.playSearch("${pl.name} ${pl.subtitle}", onPlay)
                            }
                        },
                    )
                }
            }
        }

        // 3. "Артисты"
        Text(
            text = "Артисты",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = VotifyColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        )
        if (state.forYouArtists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.forYouArtists) { artist ->
                    ArtistCard(
                        artist = artist,
                        onClick = { onOpenArtist(artist.name) },
                    )
                }
            }
        }

        // 4. "Релизы"
        Text(
            text = "Релизы",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        )
        if (state.forYouReleases.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.forYouReleases) { release ->
                    ReleaseCard(
                        release = release,
                        onClick = {
                            if (release.track != null) {
                                onPlay(listOf(release.track), 0)
                            } else {
                                viewModel.playSearch("${release.artist} ${release.title}", onPlay)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Quick tiles: Favorites / Trending
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(
                title = stringResource(R.string.home_favorites),
                subtitle = if (favoriteCount > 0) pluralTracks(favoriteCount) else stringResource(R.string.home_playlist),
                icon = Icons.Filled.Favorite,
                modifier = Modifier.weight(1f),
                onClick = onOpenFavorites,
            )
            QuickTile(
                title = stringResource(R.string.home_trending),
                subtitle = stringResource(R.string.trending_live),
                icon = Icons.Filled.TrendingUp,
                modifier = Modifier.weight(1f),
                onClick = onOpenTrending,
            )
        }
    }
}

/** Header: "Votify" brand pill on the left, account / search / settings on the right. */
@Composable
private fun HomeHeader(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSite: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenSite,
            shape = CircleShape,
            color = if (isGlass) Color.White.copy(alpha = 0.08f) else VotifyColors.SurfaceContainer,
            border = BorderStroke(
                1.dp,
                if (isGlass) Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                ) else SolidColor(VotifyColors.BorderSubtle),
            ),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_votify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Votify", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(onClick = onOpenAccount, size = 36.dp, contentDescription = stringResource(R.string.settings_section_account)) {
            Icon(Icons.Outlined.AccountCircle, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = onOpenSearch, size = 36.dp, contentDescription = stringResource(R.string.nav_search)) {
            Icon(Icons.Outlined.Search, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        var settingsAngle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val animatedRotation by androidx.compose.animation.core.animateFloatAsState(
            targetValue = settingsAngle,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "settingsSpin",
        )
        CircleIconButton(
            onClick = {
                settingsAngle += 360f
                onOpenSettings()
            },
            size = 36.dp,
            contentDescription = stringResource(R.string.nav_settings),
        ) {
            Icon(
                Icons.Outlined.Settings,
                null,
                tint = VotifyColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = animatedRotation },
            )
        }
    }
}

/** Строка текста песни для главной: синхронизированная — по позиции, иначе первая строка. */
@Composable
private fun homeLyricLine(
    viewModel: HomeViewModel,
    playerState: app.votify.mobile.player.PlayerUiState,
): String? {
    val track = playerState.current ?: return null
    LaunchedEffect(track.id) { viewModel.loadLyrics(track) }
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val parsed = lyrics ?: return null
    val index = parsed.activeIndex(playerState.positionMs)
    return (parsed.lines.getOrNull(index)?.text ?: parsed.lines.firstOrNull()?.text)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

/**
 * The hero of the home screen: a 72dp white Play disc in the centre with up to 8 artwork
 * squircle bubbles scattered on an orbit around it.
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
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )

    Box(Modifier.size(310.dp), contentAlignment = Alignment.Center) {
        bubbles.forEachIndexed { i, track ->
            val radius: Dp = 120.dp
            val size: Dp = if (i % 2 == 0) 56.dp else 48.dp
            val angle = Math.toRadians((i * (360.0 / bubbles.size)) - 90 + drift)
            val dx = (radius.value * cos(angle)).toFloat().dp
            val dy = (radius.value * sin(angle)).toFloat().dp
            val isGlass = LocalLiquidGlass.current
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .offset(x = dx, y = dy)
                    .size(size)
                    .clip(shape)
                    .border(
                        1.dp,
                        if (isGlass) Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                        ) else SolidColor(VotifyColors.BorderSubtle),
                        shape,
                    )
                    .clickable { onPlayTrack(i) },
            ) {
                Artwork(track.cover, size = size, shape = shape, contentDescription = track.title)
            }
        }

        val heroFill by animateColorAsState(VotifyColors.AccentFill, tween(160), label = "heroFill")
        val heroContent by animateColorAsState(VotifyColors.AccentContent, tween(160), label = "heroContent")

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onPlayWave,
                shape = CircleShape,
                color = heroFill,
                contentColor = heroContent,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
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
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    onClick: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (isGlass) Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))
                    ) else SolidColor(VotifyColors.BorderSubtle),
                    RoundedCornerShape(16.dp),
                ),
        ) {
            Artwork(
                url = track.cover,
                size = 135.dp,
                shape = RoundedCornerShape(16.dp),
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = VotifyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: HomePlaylistItem,
    onClick: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (isGlass) Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))
                    ) else SolidColor(VotifyColors.BorderSubtle),
                    RoundedCornerShape(16.dp),
                ),
        ) {
            Artwork(
                url = playlist.cover,
                size = 135.dp,
                shape = RoundedCornerShape(16.dp),
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playlist.subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = VotifyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCard(
    artist: HomeArtistItem,
    onClick: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (isGlass) Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                    ) else SolidColor(VotifyColors.BorderSubtle),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = artist.avatarUrl,
                size = 112.dp,
                shape = CircleShape,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = artist.followers,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = VotifyColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReleaseCard(
    release: HomeReleaseItem,
    onClick: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (isGlass) Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))
                    ) else SolidColor(VotifyColors.BorderSubtle),
                    RoundedCornerShape(16.dp),
                ),
        ) {
            Artwork(
                url = release.cover,
                size = 135.dp,
                shape = RoundedCornerShape(16.dp),
                contentDescription = release.title,
                modifier = Modifier.fillMaxSize(),
            )
            ReleaseBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = release.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = release.artist,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = VotifyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Spotify green circular badge for tracks. */
@Composable
private fun SpotifyBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0xFF1DB954))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val stroke = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val w = size.width
            val h = size.height
            // Top arc
            val p1 = Path().apply {
                moveTo(w * 0.18f, h * 0.38f)
                quadraticTo(w * 0.52f, h * 0.22f, w * 0.82f, h * 0.32f)
            }
            drawPath(p1, Color.Black, style = stroke)
            // Middle arc
            val p2 = Path().apply {
                moveTo(w * 0.24f, h * 0.56f)
                quadraticTo(w * 0.52f, h * 0.44f, w * 0.76f, h * 0.52f)
            }
            drawPath(p2, Color.Black, style = stroke)
            // Bottom arc
            val p3 = Path().apply {
                moveTo(w * 0.30f, h * 0.74f)
                quadraticTo(w * 0.52f, h * 0.64f, w * 0.70f, h * 0.70f)
            }
            drawPath(p3, Color.Black, style = stroke)
        }
    }
}

/** Release badge (vinyl disc icon). */
@Composable
private fun ReleaseBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0xFF181818))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            val stroke = Stroke(width = 1.2.dp.toPx())
            drawCircle(Color.White, radius = size.minDimension * 0.45f, style = stroke)
            drawCircle(Color.White, radius = size.minDimension * 0.15f)
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
