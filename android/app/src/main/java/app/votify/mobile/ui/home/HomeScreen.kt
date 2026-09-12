package app.votify.mobile.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.data.WaveMode
import app.votify.mobile.data.WaveStyle
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val orbColor by viewModel.orbColor.collectAsStateWithLifecycle()
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

        // --- «Моя волна»: орбита с обложками или огненный шар (стиль из настроек) ---
        when (state.waveStyle) {
            WaveStyle.Orbit -> OrbitHero(
                state = state,
                onPlayWave = { onPlay(state.wave, 0) },
                onPlayTrack = { index -> onPlay(state.wave, index) },
                onRetry = viewModel::refresh,
            )
            WaveStyle.Sun -> SolidOrb(
                state = state,
                orbBase = orbColor,
                onPlayWave = { onPlay(state.wave, 0) },
                onRetry = viewModel::refresh,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Таблеточки режимов под карточкой.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PillChip(
                    text = stringResource(R.string.wave_for_you),
                    selected = state.waveMode == WaveMode.ForYou,
                    onClick = { viewModel.setMode(WaveMode.ForYou) },
                )
            }
            item {
                PillChip(
                    text = stringResource(R.string.wave_popular),
                    selected = state.waveMode == WaveMode.Popular,
                    onClick = { viewModel.setMode(WaveMode.Popular) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        WaveStrip(
            tracks = state.wave,
            isLoading = state.isLoading,
            error = state.error,
            emptyText = stringResource(
                if (state.waveMode == WaveMode.Popular) R.string.wave_popular_empty
                else R.string.home_wave_empty,
            ),
            onPlayTrack = { index -> onPlay(state.wave, index) },
            onRetry = viewModel::refresh,
        )

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

        Spacer(Modifier.height(12.dp))

        // --- Recent (listening history) ---
        VotifyCard(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenSite,
            shape = CircleShape,
            color = VotifyColors.SurfaceContainer,
            border = BorderStroke(1.dp, VotifyColors.BorderSubtle),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_votify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Votify", style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
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
        CircleIconButton(onClick = onOpenSettings, size = 36.dp, contentDescription = stringResource(R.string.nav_settings)) {
            Icon(Icons.Outlined.Settings, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(18.dp))
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

/** Стиль «Орбита»: вокруг кнопки плея — обложки треков волны (как раньше). */
@Composable
private fun OrbitHero(
    state: HomeUiState,
    onPlayWave: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onRetry: () -> Unit,
) {
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
                PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = onRetry)
            }
            state.wave.isEmpty() -> Text(
                if (state.waveMode == WaveMode.Popular) stringResource(R.string.wave_popular_empty)
                else stringResource(R.string.home_wave_empty),
                color = VotifyColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            else -> WaveOrbit(
                tracks = state.wave,
                onPlayWave = onPlayWave,
                onPlayTrack = onPlayTrack,
            )
        }
    }
    if (!state.isLoading && state.error == null) {
        Text(
            text = when {
                state.waveSource == WaveSource.Popular -> stringResource(R.string.wave_popular_sub)
                state.waveSource == WaveSource.Personal ->
                    stringResource(R.string.home_wave_personal, state.seeds.take(3).joinToString(", "))
                else -> stringResource(R.string.home_wave_generic)
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
}

/**
 * The hero of the home screen: a 72dp Play disc in the centre with up to 8 artwork
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
            // One round orbit for every bubble (user request); sizes still vary a little.
            val radius: Dp = 118.dp
            val size: Dp = if (i % 2 == 0) 56.dp else 48.dp
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

        // При смене акцента кнопка перекрашивается плавно, а не «щёлкает» цветом.
        val heroFill by animateColorAsState(VotifyColors.AccentFill, tween(260), label = "heroFill")
        val heroContent by animateColorAsState(VotifyColors.AccentContent, tween(260), label = "heroContent")

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onPlayWave,
                shape = CircleShape,
                color = heroFill,
                contentColor = heroContent,
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

/**
 * Стиль «Шар»: огненный баннер «Моя волна» как на ПК (Stitch-дизайн) —
 * слоистое закатное свечение, световые лучи, стеклянная пилюля Play.
 * Ядро подкрашивается матугеном под цвет фона, как на ПК.
 */
@Composable
private fun SolidOrb(
    state: HomeUiState,
    orbBase: Color?,
    onPlayWave: () -> Unit,
    onRetry: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val hasMusic = !state.isLoading && state.error == null && state.wave.isNotEmpty()
    val textShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 24f)

    // Матуген-ядро как на ПК: яркие тона оттенка фона; без цвета — огненный дефолт.
    val hue = orbBase?.let(::colorHue)
    val coreLight = hue?.let { Color.hsl(it, 0.95f, 0.78f) } ?: Color(0xFFFFF176)
    val coreMid = hue?.let { Color.hsl(it, 0.9f, 0.6f) } ?: Color(0xFFFFB300)
    val coreDeep = hue?.let { Color.hsl(it, 0.85f, 0.48f) } ?: Color(0xFFFF5722)
    val glow1 = hue?.let { coreLight } ?: Color(0xFFFFAA00)
    val glow2 = hue?.let { coreMid } ?: Color(0xFFFF4B00)
    val glow3 = hue?.let { coreDeep } ?: Color(0xFFE10085)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0B0B0E))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .clickable(
                enabled = hasMusic || state.error != null,
                onClick = { if (state.error != null) onRetry() else onPlayWave() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Внешняя маджента-вуаль.
        Box(
            Modifier
                .size(400.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFFFF0077).copy(alpha = 0.5f),
                        0.45f to Color(0xFFA800E0).copy(alpha = 0.35f),
                        0.75f to Color.Transparent,
                    ),
                ),
        )
        // Среднее огненное свечение.
        Box(
            Modifier
                .size(320.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to glow1.copy(alpha = 0.9f),
                        0.45f to glow2.copy(alpha = 0.7f),
                        0.7f to glow3.copy(alpha = 0.5f),
                        0.85f to Color.Transparent,
                    ),
                ),
        )
        // Внутренний жар.
        Box(
            Modifier
                .size(200.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to coreLight.copy(alpha = 0.95f),
                        0.4f to coreMid.copy(alpha = 0.9f),
                        0.8f to coreDeep.copy(alpha = 0.8f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )
        // Ядро шара — сплошное.
        Box(
            Modifier
                .size(110.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(coreMid),
        )
        // Световые лучи и дуга, как на ПК.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val d1s = Offset(w * 0.12f, -h * 0.07f)
            val d1e = Offset(w * 0.91f, h * 1.05f)
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color(0xFFFFF9C4).copy(alpha = 0.8f),
                    1.0f to Color.Transparent,
                    start = d1s,
                    end = d1e,
                ),
                start = d1s,
                end = d1e,
                strokeWidth = 2.dp.toPx(),
                alpha = 0.5f,
                blendMode = BlendMode.Screen,
            )
            val d2s = Offset(w * 0.85f, -h * 0.1f)
            val d2e = Offset(w * 0.15f, h * 1.07f)
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color(0xFFFFE082).copy(alpha = 0.9f),
                    1.0f to Color.Transparent,
                    start = d2s,
                    end = d2e,
                ),
                start = d2s,
                end = d2e,
                strokeWidth = 1.8.dp.toPx(),
                alpha = 0.6f,
                blendMode = BlendMode.Screen,
            )
            val arc = Path().apply {
                moveTo(-w * 0.03f, h * 0.43f)
                quadraticBezierTo(w * 0.28f, h * 0.33f, w * 0.5f, h * 0.5f)
                quadraticBezierTo(w * 0.72f, h * 0.67f, w * 1.03f, h * 0.48f)
            }
            drawPath(
                path = arc,
                brush = Brush.linearGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color(0xFFFFE082).copy(alpha = 0.9f),
                    1.0f to Color.Transparent,
                ),
                style = Stroke(width = 2.5.dp.toPx()),
                alpha = 0.7f,
                blendMode = BlendMode.Screen,
            )
        }
        // Контент поверх шара.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp),
                )
                state.error != null -> Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.search_retry),
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
                else -> Text(
                    stringResource(R.string.home_my_wave),
                    style = MaterialTheme.typography.headlineLarge.copy(shadow = textShadow),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = { if (state.error != null) onRetry() else if (hasMusic) onPlayWave() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Play",
                        style = MaterialTheme.typography.labelLarge.copy(shadow = textShadow),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.isLoading -> stringResource(R.string.wave_loading)
                    state.error != null -> stringResource(R.string.search_error)
                    state.waveSource == WaveSource.Popular -> stringResource(R.string.wave_popular_sub)
                    state.waveSource == WaveSource.Personal ->
                        stringResource(R.string.home_wave_personal, state.seeds.take(3).joinToString(", "))
                    else -> stringResource(R.string.home_wave_generic)
                },
                style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}

/** Hue (0..360) of a color for the matugen core. */
private fun colorHue(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    if (max == min) return 0f
    val d = max - min
    return when (max) {
        r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60f
        g -> ((b - r) / d + 2) * 60f
        else -> ((r - g) / d + 4) * 60f
    }
}

/** Лента очереди волны: обложки под таблеточками, тап — играть с этого места. */
@Composable
private fun WaveStrip(
    tracks: List<Track>,
    isLoading: Boolean,
    error: String?,
    emptyText: String,
    onPlayTrack: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        isLoading -> Box(
            Modifier.fillMaxWidth().height(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = VotifyColors.TextMuted, strokeWidth = 2.dp)
        }
        error != null -> Box(
            Modifier.fillMaxWidth().height(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = onRetry)
        }
        tracks.isEmpty() -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Text(
                emptyText,
                color = VotifyColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = onRetry)
        }
        else -> LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(tracks.take(12)) { index, track ->
                Column(Modifier.width(104.dp).clickable { onPlayTrack(index) }) {
                    Artwork(
                        track.cover,
                        size = 104.dp,
                        shape = RoundedCornerShape(12.dp),
                        contentDescription = track.title,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.labelLarge,
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
            }
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
