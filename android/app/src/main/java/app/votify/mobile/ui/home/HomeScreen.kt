package app.votify.mobile.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
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
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors

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
    onOpenWaveSettings: () -> Unit,
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

        // --- «Моя волна» в стиле Яндекс Музыки: карточка + таблеточки + лента ---
        WaveCard(
            state = state,
            onPlayWave = { onPlay(state.wave, 0) },
            onRetry = viewModel::refresh,
            onOpenWaveSettings = onOpenWaveSettings,
        )

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

/**
 * Карточка «Моей волны» в духе Яндекс Музыки: градиент, кнопка плей,
 * подпись вкуса и «Настроить» справа вверху.
 */
@Composable
private fun WaveCard(
    state: HomeUiState,
    onPlayWave: () -> Unit,
    onRetry: () -> Unit,
    onOpenWaveSettings: () -> Unit,
) {
    val hasMusic = !state.isLoading && state.error == null && state.wave.isNotEmpty()
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(VotifyColors.AccentFill, VotifyColors.AccentFill.copy(alpha = 0.55f)),
                ),
            )
            .clickable(enabled = hasMusic, onClick = onPlayWave)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_my_wave),
                    style = MaterialTheme.typography.titleLarge,
                    color = VotifyColors.AccentContent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onOpenWaveSettings,
                    shape = CircleShape,
                    color = VotifyColors.AccentContent.copy(alpha = 0.22f),
                    contentColor = VotifyColors.AccentContent,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.wave_tune),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        color = VotifyColors.AccentContent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(52.dp).padding(10.dp),
                    )
                    state.error != null -> Surface(
                        onClick = onRetry,
                        shape = CircleShape,
                        color = VotifyColors.AccentContent,
                        contentColor = VotifyColors.AccentFill,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.search_retry),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    else -> Surface(
                        onClick = onPlayWave,
                        shape = CircleShape,
                        color = VotifyColors.AccentContent,
                        contentColor = VotifyColors.AccentFill,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.home_play_wave),
                                modifier = Modifier.size(28.dp).offset(x = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = when {
                        state.isLoading -> stringResource(R.string.wave_loading)
                        state.error != null -> stringResource(R.string.search_error)
                        state.waveSource == WaveSource.Popular -> stringResource(R.string.wave_popular_sub)
                        state.waveSource == WaveSource.Personal ->
                            stringResource(R.string.home_wave_personal, state.seeds.take(3).joinToString(", "))
                        else -> stringResource(R.string.home_wave_generic)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.AccentContent.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Лента очереди волны: обложки под таблеточками, тап — играть с этого места. */
@Composable
private fun WaveStrip(
    tracks: List<Track>,
    isLoading: Boolean,
    error: String?,
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
        tracks.isEmpty() -> Text(
            stringResource(R.string.home_wave_empty),
            color = VotifyColors.TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
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
