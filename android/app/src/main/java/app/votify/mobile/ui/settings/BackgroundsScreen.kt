package app.votify.mobile.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
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
    var urlDialog by remember { mutableStateOf(false) }
    // Тап по плитке: фон применяется сразу и открывается окно его настройки (как в Мастерской).
    var tuneBackground by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(viewModel::importBackgroundFile)
    }

    Box(Modifier.fillMaxSize()) {
        SettingsScaffold(stringResource(R.string.settings_backgrounds), contentPadding, onBack) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (prefs.backgrounds.isEmpty()) {
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
                            modifier = Modifier.fillMaxWidth().height((((prefs.backgrounds.size + 1) / 2) * 190).dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            userScrollEnabled = false,
                        ) {
                            items(prefs.backgrounds) { bg ->
                                BackgroundTile(
                                    url = bg,
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

    if (tuneBackground != null) {
        BackgroundTuneScreen(
            url = tuneBackground!!,
            viewModel = viewModel,
            onClose = { tuneBackground = null },
        )
    }
}

@Composable
private fun BackgroundTile(url: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(VotifyColors.SurfaceContainerHigh)
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

            // Превью: затемнение и размытие видны на нём сразу.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VotifyColors.SurfaceContainerHigh),
            ) {
                coil.compose.AsyncImage(
                    model = model,
                    contentDescription = stringResource(R.string.settings_bg_tune),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(prefs.bgBlur.coerceIn(0, 60).dp),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = prefs.bgDim.coerceIn(0, 92) / 100f)),
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
                modifier = Modifier.padding(horizontal = 4.dp, top = 8.dp),
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