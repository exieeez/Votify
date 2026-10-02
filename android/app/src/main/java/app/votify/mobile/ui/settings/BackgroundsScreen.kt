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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * Окно настройки фона — открывается по тапу на плитку (фон к этому моменту уже применён).
 * Сделано по образцу Мастерской: размытый фон окна, превью с живым затемнением/размытием,
 * секция «Тонкая подгонка» с ползунками и красная «Удалить фон» внизу.
 */
@Composable
fun BackgroundTuneScreen(
    url: String,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Кадр: во время жеста значения меняются десятки раз в секунду, поэтому держим их
    // локально (превью обновляется мгновенно), а в настройки пишем, когда палец отпущен.
    // remember без ключа: указатель на состояние не пересоздаётся при перекомпозиции,
    // иначе жест работал бы с устаревшей копией.
    var scale by remember { mutableFloatStateOf(prefs.bgScale.coerceIn(1f, 5f)) }
    var offsetX by remember { mutableFloatStateOf(prefs.bgOffsetX.coerceIn(-1f, 1f)) }
    var offsetY by remember { mutableFloatStateOf(prefs.bgOffsetY.coerceIn(-1f, 1f)) }
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
    // Локальные файлы хранятся путём («/data/…»), остальное — URL.
    val model = remember(url) {
        coil.request.ImageRequest.Builder(context)
            .data(if (url.startsWith("/")) File(url) else url)
            .crossfade(true)
            .build()
    }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(VotifyColors.SurfaceBase)) {
        // Подложка окна — та же картинка, размытая.
        coil.compose.AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onClose, size = 40.dp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.nav_back),
                        tint = VotifyColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_bg_tune),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Превью: как в кружке аватарки — щипком зумим, пальцем перетаскиваем прямо на экране
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(VotifyColors.SurfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            val width = size.width.coerceAtLeast(1)
                            val height = size.height.coerceAtLeast(1)
                            scale = nextScale
                            // 1:1 прямое перемещение пальцем: тянем вправо — картинка идёт вправо
                            offsetX = (offsetX - (pan.x / width) * 2f / nextScale).coerceIn(-1f, 1f)
                            offsetY = (offsetY - (pan.y / height) * 2f / nextScale).coerceIn(-1f, 1f)
                        }
                    },
            ) {
                coil.compose.AsyncImage(
                    model = model,
                    contentDescription = stringResource(R.string.settings_bg_tune),
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = prefs.bgDim.coerceIn(0, 92) / 100f)),
                )
                // Сетка кадрирования 3x3 как в фоторедакторе/аватарке
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = Color.White.copy(alpha = 0.12f)
                    val stroke = 1.dp.toPx()
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h / 3f), androidx.compose.ui.geometry.Offset(w, h / 3f), stroke)
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h * 2f / 3f), androidx.compose.ui.geometry.Offset(w, h * 2f / 3f), stroke)
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(w / 3f, 0f), androidx.compose.ui.geometry.Offset(w / 3f, h), stroke)
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(w * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(w * 2f / 3f, h), stroke)
                }
                if (scale > 1.05f) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    ) {
                        Text(
                            text = "%.1fx".format(scale),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            TuneSection(stringResource(R.string.settings_bg_crop)) {
                // Режим: как широкий ПК-фон ложится на узкий экран телефона.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.08f),
                            contentColor = if (selected) Color.Black else Color.White,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_bg_reset),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            viewModel.resetBackgroundCrop()
                        }
                        .padding(vertical = 12.dp),
                )
            }

            TuneSection(stringResource(R.string.workshop_adjust)) {
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
            }
            Text(
                stringResource(R.string.settings_bg_tune_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )

            // Удаление фона из галереи — как красная «Удалить» в Мастерской.
            Surface(
                onClick = {
                    viewModel.deleteBackground(url)
                    onClose()
                },
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4574C).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
            ) {
                Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.backgrounds_delete),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE4574C),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
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