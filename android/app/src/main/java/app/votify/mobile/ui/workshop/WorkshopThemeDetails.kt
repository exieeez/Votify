package app.votify.mobile.ui.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.CustomPrefs
import app.votify.mobile.data.WorkshopThemeDoc
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Детальная настройка темы из Мастерской (spec §12): накладывается поверх размытого фона
 * самой темы. Превью, «Область применения» (фон/обложка/слайдер), «Условия отображения»
 * (всегда/заглушка), 4 ползунка тонкой подгонки и красная «Удалить» внизу.
 */
@Composable
fun WorkshopThemeDetails(
    doc: WorkshopThemeDoc,
    viewModel: WorkshopViewModel,
    prefs: CustomPrefs,
    onClose: () -> Unit,
) {
    val spec by viewModel.activeSpec.collectAsStateWithLifecycle()
    val uid by viewModel.accountUid.collectAsStateWithLifecycle()
    val appBg by viewModel.appBackgroundUrl.collectAsStateWithLifecycle()

    // Theme art first; when the theme has no image of its own, the app background
    // keeps showing behind the settings (previously the screen covered it with dark).
    val backdropUrl = doc.theme.backgroundUrl.ifBlank { appBg }

    Box(Modifier.fillMaxSize().background(VotifyColors.SurfaceBase)) {
        // Blurred theme backdrop.
        if (backdropUrl.isNotBlank()) {
            coil.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    runCatching { Color(android.graphics.Color.parseColor(doc.theme.background)) }.getOrDefault(Color(0xFF121212)),
                ),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // ---- Top bar ----
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onClose, size = 40.dp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (uid != null && uid == doc.ownerId) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                        Text(
                            stringResource(R.string.workshop_yours),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }


            // ---- 1. Theme preview ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        runCatching { Color(android.graphics.Color.parseColor(doc.theme.background)) }.getOrDefault(Color(0xFF121212)),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                    .then(
                        if (doc.theme.backgroundUrl.isNotBlank()) {
                            Modifier.pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                    val width = size.width.coerceAtLeast(1)
                                    val height = size.height.coerceAtLeast(1)
                                    scale = nextScale
                                    offsetX = (offsetX - (pan.x / width) * 2f / nextScale).coerceIn(-1f, 1f)
                                    offsetY = (offsetY - (pan.y / height) * 2f / nextScale).coerceIn(-1f, 1f)
                                }
                            }
                        } else Modifier
                    ),
            ) {
                if (doc.theme.backgroundUrl.isNotBlank()) {
                    // ПК-темы часто с широкими обоями — здесь видно, как они кадрируются.
                    coil.compose.AsyncImage(
                        model = doc.theme.backgroundUrl,
                        contentDescription = doc.title,
                        contentScale = when (prefs.bgFit) {
                            1 -> ContentScale.Fit
                            2 -> ContentScale.FillBounds
                            else -> ContentScale.Crop
                        },
                        alignment = androidx.compose.ui.BiasAlignment(
                            offsetX.coerceIn(-1f, 1f),
                            offsetY.coerceIn(-1f, 1f),
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale.coerceIn(1f, 5f)),
                    )
                    // Сетка кадрирования 3x3
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
                Row(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                runCatching { Color(android.graphics.Color.parseColor(doc.theme.primary)) }.getOrDefault(Color.White),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            doc.authorName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF0A0A0A),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(doc.authorName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            // ---- Кнопка «Применить» ----
            val isApplied = (spec.backgroundUrl == doc.theme.backgroundUrl && doc.theme.backgroundUrl.isNotBlank()) ||
                (spec.primary.equals(doc.theme.primary, ignoreCase = true) && spec.background.equals(doc.theme.background, ignoreCase = true))

            Surface(
                onClick = { viewModel.applyTheme(doc) },
                shape = RoundedCornerShape(16.dp),
                color = if (isApplied) Color.White.copy(alpha = 0.15f) else runCatching { Color(android.graphics.Color.parseColor(doc.theme.primary)) }.getOrDefault(VotifyColors.Primary),
                contentColor = if (isApplied) Color.White else Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                Row(
                    Modifier.padding(vertical = 14.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isApplied) stringResource(R.string.workshop_applied) else stringResource(R.string.workshop_apply),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ---- 2. Область применения темы ----
            DetailSection(stringResource(R.string.workshop_theme_placement)) {
                ToggleRow(
                    title = stringResource(R.string.workshop_placement_background),
                    subtitle = stringResource(R.string.workshop_placement_background_sub),
                    checked = prefs.themeApplyBackground,
                ) { v -> viewModel.setThemeBackground(doc, v) }
                ToggleRow(
                    title = stringResource(R.string.workshop_placement_artwork),
                    subtitle = stringResource(R.string.workshop_placement_artwork_sub),
                    checked = prefs.themeApplyArtwork,
                ) { v -> viewModel.setThemeArtwork(doc, v) }
                ToggleRow(
                    title = stringResource(R.string.workshop_placement_slider),
                    subtitle = stringResource(R.string.workshop_placement_slider_sub),
                    checked = prefs.themeApplySlider,
                ) { v -> viewModel.setThemeSlider(doc, v) }
            }

            // ---- 3. Условия отображения ----
            DetailSection(stringResource(R.string.workshop_conditions)) {
                ToggleRow(
                    title = stringResource(R.string.workshop_cond_always),
                    subtitle = stringResource(R.string.workshop_cond_always_sub),
                    checked = prefs.themeArtworkAlways,
                ) { v -> viewModel.updatePrefs { it.copy(themeArtworkAlways = v) } }
                ToggleRow(
                    title = stringResource(R.string.workshop_cond_fallback),
                    subtitle = stringResource(R.string.workshop_cond_fallback_sub),
                    checked = !prefs.themeArtworkAlways,
                ) { v -> viewModel.updatePrefs { it.copy(themeArtworkAlways = !v) } }
            }


            // ---- 5. Тонкая подгонка ----
            DetailSection(stringResource(R.string.workshop_adjust)) {
                SliderRow(
                    label = stringResource(R.string.workshop_adj_blur),
                    value = spec.backgroundBlur.toFloat(),
                    range = 0f..60f,
                    onApply = { v -> viewModel.transformSpec(doc) { it.copy(backgroundBlur = v.toInt()) } },
                )
                SliderRow(
                    label = stringResource(R.string.workshop_adj_dim),
                    value = (100 - spec.uiTransparency).toFloat(),
                    range = 0f..70f,
                    onApply = { v -> viewModel.transformSpec(doc) { it.copy(uiTransparency = (100 - v.toInt()).coerceIn(10, 100)) } },
                )
                SliderRow(
                    label = stringResource(R.string.workshop_adj_art_blur),
                    value = prefs.artBlur.toFloat(),
                    range = 0f..30f,
                    onApply = { v -> viewModel.updatePrefs { it.copy(artBlur = v.toInt()) } },
                )
                SliderRow(
                    label = stringResource(R.string.workshop_adj_art_dim),
                    value = prefs.artDim.toFloat(),
                    range = 0f..80f,
                    onApply = { v -> viewModel.updatePrefs { it.copy(artDim = v.toInt()) } },
                )
            }

            // ---- 6. Удалить ----
            Surface(
                onClick = { viewModel.deleteTheme(doc); onClose() },
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4574C).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
            ) {
                Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.workshop_delete),
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
private fun DetailSection(title: String, content: @Composable () -> Unit) {
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
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color.White,
                checkedThumbColor = Color(0xFF0A0A0A),
                uncheckedTrackColor = Color(0xFF3A3A3C),
                uncheckedThumbColor = Color(0xFFB0B0B2),
            ),
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onApply: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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
