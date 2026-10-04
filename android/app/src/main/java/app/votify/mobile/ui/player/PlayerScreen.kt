package app.votify.mobile.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import app.votify.mobile.R
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.Lyrics as LyricsModel
import app.votify.mobile.data.PlayerBackground
import app.votify.mobile.player.PlayerUiState
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.formatDuration
import app.votify.mobile.ui.theme.VotifyColors
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen "Сейчас играет": header, artwork (vinyl / square / blur per settings) that swaps
 * to synced lyrics, title/artist with ♥ and "add to playlist", scrubber, transport panel and a
 * secondary row (lyrics / queue). With [PlayerBackground.Artwork] the background is a gradient
 * of the cover's dominant color — the PC-style tinted player.
 */
/** Visual options from the «Плеер»/«Обложка» settings screens. */
data class PlayerVisuals(
    val titleLeft: Boolean = false,
    val pillPlayButton: Boolean = false,
    val infoChip: String = "source", // source | text | none
    val gifArtwork: String = "",
    val artworkAnimation: String = "none", // none | spin | sway | pulse | float
    /** Themed slider: hex color from the applied workshop theme ("" = off). */
    val themedSliderHex: String = "",
    val artBlur: Int = 0,
    val artDim: Int = 0,
    val gifAlways: Boolean = true,
    val artworkEffect: String = "none", // none | grayscale | blur
    val artworkInside: Boolean = false,
    val accentFromArt: Boolean = false,
    val swipeNavigation: Boolean = true,
    val iosSlider: Boolean = true,
    val sliderStyle: String = "ios",
)

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    artworkStyle: ArtworkStyle,
    background: PlayerBackground,
    visuals: PlayerVisuals = PlayerVisuals(),
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
    onShare: () -> Unit,
    onOpenMenu: () -> Unit,
    onSetRemix: (speed: Float, pitch: Float, pitchLinked: Boolean, reverb: String, bass: Boolean) -> Unit = { _, _, _, _, _ -> },
    onSaveSpeedPreset: (speed: Float, pitch: Float, pitchLinked: Boolean, reverb: String, bass: Boolean) -> Unit = { _, _, _, _, _ -> },
) {
    val track = state.current
    var showSpeedSheet by remember { mutableStateOf(false) }

    if (showSpeedSheet) {
        SpeedPresetBottomSheet(
            currentSpeed = state.speed,
            currentPitch = state.pitch,
            currentPitchLinked = state.pitchLinked,
            currentReverb = state.reverbPreset,
            currentBass = state.bassBoost,
            onRemixChange = onSetRemix,
            onSavePreset = { speed, pitch, linked, reverb, bass ->
                onSaveSpeedPreset(speed, pitch, linked, reverb, bass)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    // PC-style tinted background: dominant color of the current cover, fading to black.
    val palette = rememberArtworkPalette(
        track?.cover,
        enabled = background == PlayerBackground.Artwork || background == PlayerBackground.Lava || visuals.accentFromArt,
        isDark = app.votify.mobile.ui.theme.LocalVotifyPalette.current.isDark,
    )
    val topColor by animateColorAsState(palette?.top ?: VotifyColors.SurfaceBase, tween(600), label = "bgTop")
    val bottomColor by animateColorAsState(palette?.bottom ?: VotifyColors.SurfaceBase, tween(600), label = "bgBottom")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .let { m ->
                if (visuals.swipeNavigation) m.pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total < -110f -> onNext()
                                total > 110f -> onPrevious()
                            }
                        },
                    ) { _, dragAmount -> total += dragAmount }
                } else m
            },
    ) {
        if (background == PlayerBackground.Lava) {
            LavaBackground(
                top = palette?.top ?: VotifyColors.SurfaceBase,
                bottom = palette?.bottom ?: VotifyColors.SurfaceBase,
                accent = palette?.accent,
            )
        }

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
                    Brush.verticalGradient(listOf(Color.Transparent, bottomColor.copy(alpha = 0.7f), bottomColor)),
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
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.action_share), tint = VotifyColors.TextSecondary) }
                IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.player_menu), tint = VotifyColors.TextSecondary) }
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
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "artwork-lyrics",
                ) { showLyrics ->
                    if (showLyrics) {
                        LyricsPanel(state = lyrics, positionMs = state.positionMs, onSeekToMs = onSeekToMs, onClose = onToggleLyrics)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            // «Всегда» = заменять обложку трека всегда; иначе только когда у трека нет своей.
                            val useOverride = visuals.gifArtwork.isNotBlank() &&
                                (visuals.gifAlways || track?.cover.isNullOrBlank())
                            val artworkUrl = if (useOverride) visuals.gifArtwork else track?.cover.orEmpty()
                            val grayFilter = if (visuals.artworkEffect == "grayscale") {
                                androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
                            } else null
                            val effect: Modifier = when {
                                visuals.artworkEffect == "blur" -> Modifier.blur(14.dp)
                                visuals.artBlur > 0 -> Modifier.blur(visuals.artBlur.coerceIn(1, 30).dp)
                                else -> Modifier
                            }
                            val artShape = if (artworkStyle == ArtworkStyle.Circle) CircleShape else RoundedCornerShape(24.dp)
                            when (artworkStyle) {
                                ArtworkStyle.Vinyl -> Vinyl(coverUrl = hiRes(artworkUrl), spinning = state.isPlaying, loading = state.isBuffering)
                                ArtworkStyle.Square, ArtworkStyle.Circle, ArtworkStyle.Blur -> Box(
                                    artworkAnimModifier(visuals.artworkAnimation, state.isPlaying),
                                ) {
                                    SquareArtwork(
                                        coverUrl = hiRes(artworkUrl),
                                        modifier = effect,
                                        colorFilter = grayFilter,
                                        shape = artShape,
                                        dim = visuals.artDim.coerceIn(0, 80) / 100f,
                                        loading = state.isBuffering,
                                    )
                                }
                            }
                            if (visuals.artworkInside && visuals.gifArtwork.isNotBlank()) {
                                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                                    Artwork(track?.cover.orEmpty(), size = 72.dp)
                                }
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
                Column(Modifier.weight(1f), horizontalAlignment = if (visuals.titleLeft) Alignment.Start else Alignment.CenterHorizontally) {
                    Text(
                        track?.title ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (visuals.titleLeft) TextAlign.Start else TextAlign.Center,
                        modifier = if (visuals.titleLeft) Modifier.fillMaxWidth() else Modifier,
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

            Scrubber(
                state = state,
                onSeek = onSeek,
                accent = when {
                    visuals.themedSliderHex.isNotBlank() ->
                        runCatching { Color(android.graphics.Color.parseColor(visuals.themedSliderHex)) }.getOrDefault(VotifyColors.Primary)
                    visuals.accentFromArt -> palette?.accent ?: VotifyColors.Primary
                    else -> VotifyColors.Primary
                },
                sliderStyle = visuals.sliderStyle,
                ios = visuals.iosSlider,
            )

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
                        shape = if (visuals.pillPlayButton) RoundedCornerShape(32.dp) else CircleShape,
                        color = if (visuals.accentFromArt) palette?.accent ?: VotifyColors.Primary else VotifyColors.Primary,
                        contentColor = VotifyColors.PitchBlack,
                        modifier = if (visuals.pillPlayButton) Modifier.height(56.dp).width(120.dp) else Modifier.size(64.dp),
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
                        Icon(
                            Icons.Outlined.Description,
                            stringResource(R.string.player_lyrics),
                            tint = if (lyricsVisible) VotifyColors.TextPrimary else VotifyColors.TextSecondary,
                        )
                    }
                    IconButton(onClick = { showSpeedSheet = true }) {
                        val isRemixActive = Math.abs(state.speed - 1.0f) > 0.02f ||
                                Math.abs(state.pitch - 1.0f) > 0.02f ||
                                state.reverbPreset != "none" ||
                                state.bassBoost
                        Icon(
                            Icons.Outlined.Speed,
                            contentDescription = "Ремикс и скорость",
                            tint = if (isRemixActive) (palette?.accent ?: VotifyColors.Primary) else VotifyColors.TextSecondary,
                        )
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
private fun Vinyl(coverUrl: String, spinning: Boolean, loading: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        VinylDisc(coverUrl = coverUrl, spinning = spinning)
        if (loading) {
            CircularProgressIndicator(
                color = VotifyColors.TextPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(326.dp),
            )
        }
    }
}

@Composable
private fun VinylDisc(coverUrl: String, spinning: Boolean) {
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
            Box(Modifier.size(r.dp).border(1.dp, VotifyColors.TextMuted.copy(alpha = 0.15f), CircleShape))
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
private fun SquareArtwork(
    coverUrl: String,
    modifier: Modifier = Modifier,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    dim: Float = 0f,
    loading: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, VotifyColors.BorderSubtle, shape)
            .then(modifier),
    ) {
        Artwork(coverUrl, size = 400.dp, shape = shape, modifier = Modifier.fillMaxSize(), colorFilter = colorFilter)
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dim)))
        }
        // Buffering: a ring exactly around the artwork (was a mismatched 280dp circle outside).
        if (loading) {
            CircularProgressIndicator(
                color = VotifyColors.TextPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
}

/**
 * YouTube search thumbnails come small (173–360 px) — the big player artwork looked
 * blurry. Swap any i.ytimg.com variant for hqdefault (480 px, always exists).
 */
private fun hiRes(url: String): String =
    url.replace(Regex("(i\\.ytimg\\.com/vi/[A-Za-z0-9_-]{11}/)\\w+\\.\\w+"), "$1hqdefault.jpg")

/** Artwork motion: spin (диск), sway (покачивание), pulse (пульсация), float (полёт). */
@Composable
private fun artworkAnimModifier(anim: String, playing: Boolean): Modifier {
    if (anim == "none" || !playing) return Modifier
    val t = rememberInfiniteTransition(label = "art-$anim")
    return when (anim) {
        "spin" -> {
            val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "a")
            Modifier.graphicsLayer { rotationZ = a }
        }
        "sway" -> {
            val a by t.animateFloat(
                -6f, 6f,
                infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "a",
            )
            Modifier.graphicsLayer { rotationZ = a }
        }
        "pulse" -> {
            val a by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1_700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "a",
            )
            Modifier.graphicsLayer { val sc = 1f + 0.05f * a; scaleX = sc; scaleY = sc }
        }
        "float" -> {
            val a by t.animateFloat(
                -8f, 8f,
                infiniteRepeatable(tween(3_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "a",
            )
            Modifier.graphicsLayer { translationY = a }
        }
        else -> Modifier
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
fun Scrubber(
    state: PlayerUiState,
    onSeek: (Float) -> Unit,
    accent: Color,
    sliderStyle: String = "ios",
    ios: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: state.progress
    val style = if (sliderStyle.isNotBlank()) sliderStyle.lowercase() else if (ios) "ios" else "classic"

    Column(Modifier.fillMaxWidth()) {
        when (style) {
            "ios" -> {
                // iOS-style: 10dp pill track (clean Apple Music capsule, no floating knob)
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val width = maxWidth
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures { pos ->
                                    onSeek((pos.x / width.toPx()).coerceIn(0f, 1f))
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset -> dragging = (offset.x / width.toPx()).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        dragging?.let(onSeek)
                                        dragging = null
                                    },
                                    onDragCancel = { dragging = null },
                                    onHorizontalDrag = { change, _ ->
                                        dragging = (change.position.x / width.toPx()).coerceIn(0f, 1f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(VotifyColors.SurfaceContainerHigh),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(value)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(accent),
                            )
                        }
                    }
                }
            }
            "thin" -> {
                // Minimalist thin style: 3dp line with perfectly centered playhead dot
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val width = maxWidth
                    val dotSize = 10.dp
                    val dotRadius = dotSize / 2
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures { pos ->
                                    onSeek((pos.x / width.toPx()).coerceIn(0f, 1f))
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset -> dragging = (offset.x / width.toPx()).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        dragging?.let(onSeek)
                                        dragging = null
                                    },
                                    onDragCancel = { dragging = null },
                                    onHorizontalDrag = { change, _ ->
                                        dragging = (change.position.x / width.toPx()).coerceIn(0f, 1f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(VotifyColors.SurfaceContainerHigh),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(value)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(accent),
                            )
                        }
                        val dotOffset = ((width * value) - dotRadius).coerceIn(0.dp, width - dotSize)
                        Box(
                            Modifier
                                .offset(x = dotOffset)
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(VotifyColors.TextPrimary)
                                .shadow(2.dp, CircleShape),
                        )
                    }
                }
            }
            "wave" -> {
                // Wave style: audio soundwave bars scrubber (pure waveform, no floating knob)
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val width = maxWidth
                    val barCount = 42
                    val activeBars = (barCount * value).toInt()
                    val waveHeights = remember {
                        floatArrayOf(
                            0.25f, 0.4f, 0.65f, 0.85f, 0.55f, 0.35f, 0.6f, 0.8f,
                            0.95f, 0.7f, 0.45f, 0.3f, 0.55f, 0.8f, 1.0f, 0.75f,
                            0.5f, 0.65f, 0.85f, 0.7f, 0.45f, 0.35f, 0.6f, 0.9f,
                            0.8f, 0.55f, 0.35f, 0.5f, 0.75f, 0.95f, 0.7f, 0.45f,
                            0.3f, 0.55f, 0.75f, 0.9f, 0.65f, 0.4f, 0.55f, 0.7f, 0.45f, 0.25f
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { pos ->
                                    onSeek((pos.x / width.toPx()).coerceIn(0f, 1f))
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset -> dragging = (offset.x / width.toPx()).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        dragging?.let(onSeek)
                                        dragging = null
                                    },
                                    onDragCancel = { dragging = null },
                                    onHorizontalDrag = { change, _ ->
                                        dragging = (change.position.x / width.toPx()).coerceIn(0f, 1f)
                                    }
                                )
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0 until barCount) {
                            val h = waveHeights.getOrElse(i) { 0.5f }
                            val isActive = i <= activeBars
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 0.8.dp)
                                    .fillMaxHeight(h)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(if (isActive) accent else VotifyColors.SurfaceContainerHigh),
                            )
                        }
                    }
                }
            }
            else -> { // "classic"
                Slider(
                    value = value,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let(onSeek)
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = VotifyColors.SurfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging != null) (state.durationMs * value).toLong() else state.positionMs
            Text(formatDuration(shown), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
        }
    }
}

/** Colors derived from the current cover for the tinted player background. */
private data class ArtworkPalette(val top: Color, val bottom: Color, val accent: Color)

/**
 * «Лавовая лампа»: медленно плавающие мягкие пятна в цветах текущей обложки.
 * Radial gradients (no RenderEffect → works on every Android version).
 */
@Composable
private fun LavaBackground(top: Color, bottom: Color, accent: Color?) {
    val t = rememberInfiniteTransition(label = "lava")
    val p1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "p1")
    val p2 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(34_000, easing = LinearEasing)), label = "p2")
    val p3 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(42_000, easing = LinearEasing)), label = "p3")

    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(
                    androidx.compose.ui.graphics.lerp(top, androidx.compose.ui.graphics.Color.Black, 0.45f),
                    androidx.compose.ui.graphics.lerp(bottom, androidx.compose.ui.graphics.Color.Black, 0.55f),
                ),
            ),
        )

        fun androidx.compose.ui.graphics.drawscope.DrawScope.blob(phase: Float, sx: Float, sy: Float, ox: Float, oy: Float, radius: Float, color: Color, alpha: Float) {
            val x = w * (ox + sx * 0.5f * kotlin.math.sin(Math.toRadians(phase.toDouble())).toFloat())
            val y = h * (oy + sy * 0.5f * kotlin.math.cos(Math.toRadians(phase * 0.8)).toFloat())
            val c = androidx.compose.ui.geometry.Offset(x, y)
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = c,
                    radius = radius,
                ),
                radius = radius,
                center = c,
            )
        }

        val big = size.minDimension * 0.62f
        blob(p1, 0.7f, 0.55f, 0.32f, 0.30f, big, accent ?: top, 0.50f)
        blob(p2, 0.8f, 0.6f, 0.72f, 0.68f, big * 0.9f, top, 0.45f)
        blob(p3, 0.6f, 0.7f, 0.5f, 0.9f, big * 1.1f, bottom, 0.40f)
        blob(p1 + 140f, 0.9f, 0.5f, 0.15f, 0.8f, big * 0.7f, accent ?: bottom, 0.30f)
        blob(p2 + 220f, 0.7f, 0.8f, 0.85f, 0.2f, big * 0.8f, top, 0.28f)
    }
}

/**
 * Extracts the dominant + vibrant colors of [coverUrl] with the Android Palette API (like the
 * desktop player). The result is remembered per cover so switching tracks crossfades between
 * palettes; null while nothing has been extracted yet (or when [enabled] is off).
 */
@Composable
private fun rememberArtworkPalette(coverUrl: String?, enabled: Boolean, isDark: Boolean = true): ArtworkPalette? {
    if (!enabled) return null
    val context = LocalContext.current
    var palette by remember { mutableStateOf<ArtworkPalette?>(null) }

    LaunchedEffect(coverUrl, enabled) {
        if (coverUrl.isNullOrBlank()) {
            palette = null
            return@LaunchedEffect
        }
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context).data(coverUrl).size(192).allowHardware(false).build()
        val bitmap = runCatching { loader.execute(request) }.getOrNull()?.drawable?.toBitmap()
        if (bitmap == null) {
            palette = null
            return@LaunchedEffect
        }
        val swatches = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(24).generate() }
        val dominant = swatches.dominantSwatch?.let { Color(it.rgb) }
        if (dominant == null) {
            palette = null
        } else {
            val accent = swatches.vibrantSwatch?.let { Color(it.rgb) }
                ?: swatches.lightVibrantSwatch?.let { Color(it.rgb) }
                ?: swatches.mutedSwatch?.let { Color(it.rgb) }
                ?: dominant
            // Dark theme: tint toward black (classic tinted player). Light theme: keep the
            // background light so dark text from the light palette stays readable.
            palette = if (isDark) {
                ArtworkPalette(
                    top = lerp(dominant, Color.Black, 0.25f),
                    bottom = lerp(dominant, Color.Black, 0.72f),
                    accent = accent,
                )
            } else {
                ArtworkPalette(
                    top = lerp(dominant, Color.White, 0.62f),
                    bottom = lerp(dominant, Color.White, 0.30f),
                    accent = accent,
                )
            }
        }
    }

    return palette
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPresetBottomSheet(
    currentSpeed: Float,
    currentPitch: Float,
    currentPitchLinked: Boolean,
    currentReverb: String,
    currentBass: Boolean,
    onRemixChange: (speed: Float, pitch: Float, pitchLinked: Boolean, reverb: String, bass: Boolean) -> Unit,
    onSavePreset: (speed: Float, pitch: Float, pitchLinked: Boolean, reverb: String, bass: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }
    var selectedPitch by remember(currentPitch) { mutableStateOf(currentPitch) }
    var pitchLinked by remember(currentPitchLinked) { mutableStateOf(currentPitchLinked) }
    var selectedReverb by remember(currentReverb) { mutableStateOf(currentReverb) }
    var selectedBass by remember(currentBass) { mutableStateOf(currentBass) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun applyChange(
        speed: Float = selectedSpeed,
        pitch: Float = selectedPitch,
        linked: Boolean = pitchLinked,
        reverb: String = selectedReverb,
        bass: Boolean = selectedBass,
    ) {
        val actualPitch = if (linked) speed else pitch
        selectedSpeed = speed
        selectedPitch = actualPitch
        pitchLinked = linked
        selectedReverb = reverb
        selectedBass = bass
        onRemixChange(speed, actualPitch, linked, reverb, bass)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top 3 quick remix cards: Slowed (+ Reverb + Bass), Default, Speedup (Nightcore)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isSlowed = Math.abs(selectedSpeed - 0.85f) < 0.03f && selectedReverb == "hall"
                val isDefault = Math.abs(selectedSpeed - 1.0f) < 0.03f && Math.abs(selectedPitch - 1.0f) < 0.03f && selectedReverb == "none" && !selectedBass
                val isSpeedup = Math.abs(selectedSpeed - 1.25f) < 0.03f && Math.abs(selectedPitch - 1.25f) < 0.03f && selectedReverb == "none"

                // Slowed card
                Surface(
                    onClick = {
                        applyChange(speed = 0.85f, pitch = 0.85f, linked = true, reverb = "hall", bass = true)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSlowed) Color.White else Color(0xFF1C1C1E),
                    contentColor = if (isSlowed) Color(0xFF111113) else Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = "Slowed", modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Slowed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSlowed) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            "0.85× • Зал",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSlowed) Color(0xFF555558) else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                // Default card
                Surface(
                    onClick = {
                        applyChange(speed = 1.0f, pitch = 1.0f, linked = true, reverb = "none", bass = false)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDefault) Color.White else Color(0xFF1C1C1E),
                    contentColor = if (isDefault) Color(0xFF111113) else Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = "Default", modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Default",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            "1.0× • Чистый",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDefault) Color(0xFF555558) else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                // Speedup / Nightcore card
                Surface(
                    onClick = {
                        applyChange(speed = 1.25f, pitch = 1.25f, linked = true, reverb = "none", bass = false)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSpeedup) Color.White else Color(0xFF1C1C1E),
                    contentColor = if (isSpeedup) Color(0xFF111113) else Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Speed, contentDescription = "Speedup", modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Speedup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSpeedup) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            "1.25× • Nightcore",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSpeedup) Color(0xFF555558) else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Speed & Pitch link switch chip
            Surface(
                onClick = { applyChange(linked = !pitchLinked) },
                shape = RoundedCornerShape(16.dp),
                color = if (pitchLinked) Color(0xFF26262A) else Color(0xFF1C1C1E),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (pitchLinked) Icons.Outlined.Lock else Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = if (pitchLinked) Color.White else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Питч как в ремиксах",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                if (pitchLinked) "Тональность меняется вместе со скоростью" else "Ручная настройка тональности",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (pitchLinked) Color.White else Color(0xFF333336),
                    ) {
                        Text(
                            if (pitchLinked) "Вкл" else "Выкл",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (pitchLinked) Color(0xFF111113) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Custom speed header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Скорость / Темп",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    String.format(java.util.Locale.US, "%.2f×", selectedSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Ruler tick marks
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val tickCount = 31 // from 0.5 to 2.0 with step 0.05
                val activeFraction = ((selectedSpeed - 0.5f) / 1.5f).coerceIn(0f, 1f)
                val activeTickIndex = (activeFraction * (tickCount - 1)).toInt()

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until tickCount) {
                        val isMajor = i % 5 == 0
                        val isCurrent = i == activeTickIndex
                        val isPassed = i <= activeTickIndex
                        val tickHeight = if (isMajor) 16.dp else 9.dp
                        val tickWidth = if (isMajor || isCurrent) 2.dp else 1.2.dp
                        val tickColor = when {
                            isCurrent -> Color.White
                            isPassed -> Color.White.copy(alpha = 0.7f)
                            else -> Color.White.copy(alpha = 0.22f)
                        }
                        Box(
                            Modifier
                                .width(tickWidth)
                                .height(tickHeight)
                                .clip(RoundedCornerShape(1.dp))
                                .background(tickColor),
                        )
                    }
                }
            }

            // Interactive slider for speed
            Slider(
                value = selectedSpeed,
                onValueChange = { s ->
                    val snapped = (Math.round(s * 20f) / 20f).coerceIn(0.5f, 2.0f)
                    applyChange(speed = snapped)
                },
                valueRange = 0.5f..2.0f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            // Independent pitch slider if decoupled
            if (!pitchLinked) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Тональность (Питч)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        String.format(java.util.Locale.US, "%.2f×", selectedPitch),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Slider(
                    value = selectedPitch,
                    onValueChange = { p ->
                        val snapped = (Math.round(p * 20f) / 20f).coerceIn(0.5f, 1.5f)
                        applyChange(pitch = snapped)
                    },
                    valueRange = 0.5f..1.5f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Reverb presets section
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Атмосфера (Reverb)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val reverbOptions = listOf(
                        "none" to "Выкл",
                        "hall" to "Зал",
                        "room" to "Клуб",
                        "plate" to "Эхо",
                    )
                    reverbOptions.forEach { (code, label) ->
                        val isSelected = selectedReverb == code
                        Surface(
                            onClick = { applyChange(reverb = code) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color.White else Color(0xFF1C1C1E),
                            contentColor = if (isSelected) Color(0xFF111113) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bass boost toggle row
            Surface(
                onClick = { applyChange(bass = !selectedBass) },
                shape = RoundedCornerShape(16.dp),
                color = if (selectedBass) Color(0xFF26262A) else Color(0xFF1C1C1E),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = if (selectedBass) Color.White else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Глубокий бас (Bass Boost)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                "Плотное клубное звучание низких частот",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedBass) Color.White else Color(0xFF333336),
                    ) {
                        Text(
                            if (selectedBass) "Вкл" else "Выкл",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedBass) Color(0xFF111113) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Wide pill button: [ Bookmark ] Запомнить
            Button(
                onClick = { onSavePreset(selectedSpeed, selectedPitch, pitchLinked, selectedReverb, selectedBass) },
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF111113),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Запомнить пресет",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
