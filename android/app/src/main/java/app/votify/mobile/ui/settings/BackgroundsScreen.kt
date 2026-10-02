package app.votify.mobile.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors
import java.io.File

/** «Библиотека» (кастомизация): галерея сохранённых фонов 2 колонки + URL и загрузка. */
@Composable
fun BackgroundsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeBg = settings.backgroundUrl
    var urlDialog by remember { mutableStateOf(false) }
    // Тап по плитке: фон применяется сразу и открывается окно его настройки (как в Мастерской).
    var tuneBackground by rememberSaveable { mutableStateOf<String?>(null) }

    // Все фоны (включая активный фон из Мастерской, чтобы никогда не терялся)
    val backgroundsList = remember(prefs.backgrounds, activeBg) {
        val list = prefs.backgrounds.toMutableList()
        if (activeBg.isNotBlank() && !list.contains(activeBg)) {
            list.add(0, activeBg)
        }
        list
    }

    LaunchedEffect(activeBg) {
        if (activeBg.isNotBlank() && activeBg !in prefs.backgrounds) {
            viewModel.addBackground(activeBg)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(viewModel::importBackgroundFile)
    }

    Box(Modifier.fillMaxSize()) {
        SettingsScaffold(stringResource(R.string.settings_backgrounds), contentPadding, onBack) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (backgroundsList.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Link, null,
                                    tint = VotifyColors.TextMuted,
                                    modifier = Modifier.size(44.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.backgrounds_empty),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VotifyColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.backgrounds_empty_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VotifyColors.TextMuted,
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().height((((backgroundsList.size + 1) / 2) * 190).dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            userScrollEnabled = false,
                        ) {
                            items(backgroundsList) { bg ->
                                BackgroundTile(
                                    url = bg,
                                    isSelected = (bg == activeBg),
                                    // Tap = apply it right away and open the tuning window.
                                    onClick = {
                                        viewModel.applyBackground(bg)
                                        tuneBackground = bg
                                    },
                                    onDelete = { viewModel.removeBackground(bg) },
                                )
                            }
                        }
                    }
                }

                // Bottom fixed buttons per the spec
                Surface(
                    onClick = { urlDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    color = VotifyColors.SurfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Link, null, tint = VotifyColors.TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.backgrounds_add_url), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = VotifyColors.SurfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Upload, null, tint = VotifyColors.TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.backgrounds_upload), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (urlDialog) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.backgrounds_add_url)) },
            text = {
                Column {
                    Text(stringResource(R.string.backgrounds_hint), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    VotifyTextField(draft, { draft = it }, stringResource(R.string.workshop_bg_hint_field))
                }
            },
            confirmButton = {
                Text(
                    stringResource(R.string.action_add),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.addBackground(draft)
                            urlDialog = false
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.action_cancel),
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.clickable { urlDialog = false }.padding(8.dp),
                )
            },
        )
    }

    // Локальная копия: delegated property нельзя умно привести к String.
    val tuneTarget = tuneBackground
    if (tuneTarget != null) {
        BackgroundTuneScreen(
            url = tuneTarget,
            viewModel = viewModel,
            onClose = { tuneBackground = null },
        )
    }
}

@Composable
private fun BackgroundTile(
    url: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(VotifyColors.SurfaceContainerHigh)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, VotifyColors.Primary, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(if (url.startsWith("/")) File(url) else url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) {
            Surface(
                shape = CircleShape,
                color = VotifyColors.Primary,
                contentColor = androidx.compose.ui.graphics.Color.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                }
            }
        }
        Surface(
            onClick = onDelete,
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
        ) {
            Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Полноэкранный режим настройки фона (как страница главной, где фон на весь экран
 * и управляется пальцами: зум щипком, сдвиг перетаскиванием).
 */
@Composable
fun BackgroundTuneScreen(
    url: String,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(prefs.bgScale.coerceIn(1f, 5f)) }
    var offsetX by remember { mutableFloatStateOf(prefs.bgOffsetX.coerceIn(-1f, 1f)) }
    var offsetY by remember { mutableFloatStateOf(prefs.bgOffsetY.coerceIn(-1f, 1f)) }
    var showUiPreview by remember { mutableStateOf(true) }
    var showAdjustSliders by remember { mutableStateOf(false) }

    LaunchedEffect(prefs.bgScale, prefs.bgOffsetX, prefs.bgOffsetY) {
        scale = prefs.bgScale.coerceIn(1f, 5f)
        offsetX = prefs.bgOffsetX.coerceIn(-1f, 1f)
        offsetY = prefs.bgOffsetY.coerceIn(-1f, 1f)
    }

    // Debounce save to DataStore to keep 60/120fps touch gestures butter smooth
    LaunchedEffect(scale, offsetX, offsetY) {
        kotlinx.coroutines.delay(150)
        if (scale != prefs.bgScale) viewModel.setBackgroundScale(scale)
        if (offsetX != prefs.bgOffsetX) viewModel.setBackgroundOffsetX(offsetX)
        if (offsetY != prefs.bgOffsetY) viewModel.setBackgroundOffsetY(offsetY)
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.setBackgroundScale(scale)
            viewModel.setBackgroundOffsetX(offsetX)
            viewModel.setBackgroundOffsetY(offsetY)
        }
    }

    val model = remember(url) {
        coil.request.ImageRequest.Builder(context)
            .data(if (url.startsWith("/")) File(url) else url)
            .crossfade(true)
            .build()
    }

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    val width = size.width.coerceAtLeast(1)
                    val height = size.height.coerceAtLeast(1)
                    scale = nextScale
                    // 1:1 прямое перемещение пальцем по всему экрану
                    offsetX = (offsetX - (pan.x / width) * 2f / nextScale).coerceIn(-1f, 1f)
                    offsetY = (offsetY - (pan.y / height) * 2f / nextScale).coerceIn(-1f, 1f)
                }
            },
    ) {
        // --- 1. ПОЛНОЭКРАННЫЙ ФОН НА ВЕСЬ ДИСПЛЕЙ ---
        coil.compose.AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = when (prefs.bgFit) {
                1 -> ContentScale.Fit
                2 -> ContentScale.FillBounds
                else -> ContentScale.Crop
            },
            alignment = androidx.compose.ui.BiasAlignment(offsetX, offsetY),
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .blur(prefs.bgBlur.coerceIn(0, 60).dp),
        )

        // Затемнение фона
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = prefs.bgDim.coerceIn(0, 92) / 100f)),
        )

        // --- 2. МАКЕТ СТРАНИЦЫ ГЛАВНОЙ (ПРЕВЬЮ ИНТЕРФЕЙСА) ---
        AnimatedVisibility(
            visible = showUiPreview,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeMockupOverlay()
        }

        // --- 3. ВЕРХНЯЯ ПАНЕЛЬ ---
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.nav_back),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                Text(
                    stringResource(R.string.settings_bg_tune),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            // Переключатель отображения интерфейса главной (глазок)
            Surface(
                onClick = { showUiPreview = !showUiPreview },
                shape = CircleShape,
                color = if (showUiPreview) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (showUiPreview) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Превью интерфейса главной",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Кнопка «Готово»
            Surface(
                onClick = onClose,
                shape = RoundedCornerShape(20.dp),
                color = VotifyColors.Primary,
                contentColor = Color.Black,
                modifier = Modifier.height(40.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Индикатор зума
        if (scale > 1.05f) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp),
            ) {
                Text(
                    text = "%.1fx".format(scale),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // --- 4. НИЖНЯЯ ПАНЕЛЬ УПРАВЛЕНИЯ ---
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF2181818),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val labels = listOf(
                            stringResource(R.string.settings_bg_fit_fill),
                            stringResource(R.string.settings_bg_fit_whole),
                            stringResource(R.string.settings_bg_fit_stretch),
                        )
                        labels.forEachIndexed { index, label ->
                            val selected = prefs.bgFit == index
                            Surface(
                                onClick = { viewModel.setBackgroundFit(index) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.1f),
                                contentColor = if (selected) Color.Black else Color.White,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        // Сбросить положение и масштаб
                        Surface(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                viewModel.resetBackgroundCrop()
                            },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.settings_bg_reset), modifier = Modifier.size(18.dp))
                            }
                        }

                        // Открыть/закрыть ползунки затемнения и размытия
                        Surface(
                            onClick = { showAdjustSliders = !showAdjustSliders },
                            shape = CircleShape,
                            color = if (showAdjustSliders) VotifyColors.Primary else Color.White.copy(alpha = 0.1f),
                            contentColor = if (showAdjustSliders) Color.Black else Color.White,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Tune, stringResource(R.string.workshop_adjust), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    AnimatedVisibility(visible = showAdjustSliders) {
                        Column(Modifier.padding(top = 10.dp)) {
                            TuneSliderRow(
                                label = stringResource(R.string.settings_bg_dim),
                                value = prefs.bgDim.toFloat(),
                                range = 0f..92f,
                                onApply = { v -> viewModel.setBackgroundDim(v.toInt()) },
                            )
                            TuneSliderRow(
                                label = stringResource(R.string.settings_bg_blur),
                                value = prefs.bgBlur.toFloat(),
                                range = 0f..60f,
                                onApply = { v -> viewModel.setBackgroundBlur(v.toInt()) },
                            )
                            Spacer(Modifier.height(6.dp))
                            // Удалить фон
                            Surface(
                                onClick = {
                                    viewModel.deleteBackground(url)
                                    onClose()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4574C).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.backgrounds_delete),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE4574C),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Макет интерфейса страницы главной (полупрозрачный поверх фона). */
@Composable
private fun HomeMockupOverlay() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(56.dp))

        // Хедер Votify + поиск/настройки
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.14f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.ic_votify_logo), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Votify", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Settings, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // «Моя волна» — большая центральная плашка
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.home_wave_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.home_wave_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Карточка «Любимые треки»
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(VotifyColors.Primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = VotifyColors.Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_favorites), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.favorites_empty), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
        }

        Spacer(Modifier.weight(1f))

        // Нижняя панель навигации (Главная / Поиск / Моя музыка)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 86.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockNavTab(painterResource(R.drawable.ic_nav_home_filled), stringResource(R.string.nav_home), selected = true)
            MockNavTab(painterResource(R.drawable.ic_nav_search_outline), stringResource(R.string.nav_search), selected = false)
            MockNavTab(painterResource(R.drawable.ic_nav_library_outline), stringResource(R.string.nav_library), selected = false)
        }
    }
}

@Composable
private fun MockNavTab(painter: androidx.compose.ui.graphics.painter.Painter, label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (selected) VotifyColors.Primary else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TuneSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE61A1A1A))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun TuneSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onApply: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(current.toInt().toString(), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onApply(current) },
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF3A3A3C),
            ),
        )
    }
}