package app.votify.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyHaptics
import app.votify.mobile.ui.components.rememberVotifyHaptic
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Modern Votify settings chrome: a round back button on the left and an elongated pill
 * with the screen title filling the rest of the row.
 */
@Composable
fun SettingsScaffold(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = {
                    haptic()
                    onBack()
                },
                shape = CircleShape,
                color = VotifyColors.SurfaceContainerHigh,
                contentColor = VotifyColors.TextPrimary,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        content()
    }
}

/** UPPERCASE gray section label. */
@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.5.sp,
            letterSpacing = 0.8.sp,
        ),
        color = VotifyColors.TextMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** Card with internal thin dividers between rows or standalone card item. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    VotifyCard(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = VotifyColors.SurfaceContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        onClick = onClick,
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(VotifyColors.BorderSubtle.copy(alpha = 0.5f)))
}

/** icon | title (+subtitle) | chevron — navigational row. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic()
                onClick()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
    }
}

/** Title (+subtitle) with a toggle. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic()
                onChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = VotifyColors.TextMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic()
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = VotifyColors.PitchBlack,
                checkedTrackColor = VotifyColors.TextPrimary,
                checkedBorderColor = VotifyColors.TextPrimary,
                uncheckedThumbColor = VotifyColors.TextMuted,
                uncheckedTrackColor = VotifyColors.SurfaceContainerHigh,
                uncheckedBorderColor = VotifyColors.BorderProminent,
            ),
        )
    }
}

/**
 * Modern Settings Value Row matching the screenshots:
 * Title at the top, Current Value/Subtitle below it, and Trailing Icon on the right.
 * Opens a modern bottom sheet with option cards upon clicking.
 */
@Composable
fun SettingsValueRow(
    title: String,
    subtitle: String? = null,
    value: String,
    trailing: ImageVector? = null,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    var open by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic()
                open = true
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle ?: value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Icon(trailing, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }

    if (open) {
        ChoiceBottomSheet(
            title = title,
            options = options,
            selected = options.firstOrNull { it.first == currentKey(options, value) }?.first ?: value,
            onDismiss = { open = false },
            onPick = {
                onPick(it)
                open = false
            },
        )
    }
}

private fun currentKey(options: List<Pair<String, String>>, value: String): String =
    options.firstOrNull { it.second == value || it.first == value }?.first ?: ""

/**
 * Modern modal bottom sheet selection picker matching the user's design:
 * - Rounded top corners
 * - Centered drag handle
 * - Interactive preview if applicable (e.g. audio slider preview)
 * - Individual rounded option cards with icons, labels, subtitles, and checkmarks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceBottomSheet(
    title: String,
    options: List<Pair<String, String>>, // key -> label
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // If selecting slider style: show interactive live slider preview card as in Screenshot 3
            if (title.contains("слайдер", ignoreCase = true) || title.contains("slider", ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.42f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 120.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1:24", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = VotifyColors.TextMuted)
                            Text("3:45", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = VotifyColors.TextMuted)
                        }
                    }
                }
            }

            options.forEach { (key, label) ->
                val isSelected = key == selected || label == selected
                val icon = resolveChoiceIcon(key, label)
                val subtitle = resolveChoiceSubtitle(key, label)

                Surface(
                    onClick = {
                        haptic()
                        onPick(key)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color(0xFF222226) else Color(0xFF1B1B1E),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = VotifyColors.TextPrimary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = VotifyColors.TextPrimary,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = VotifyColors.TextMuted,
                                )
                            }
                        }
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Resolves an appropriate descriptive icon for common setting options. */
private fun resolveChoiceIcon(key: String, label: String): ImageVector? {
    val lowerKey = key.lowercase()
    val lowerLabel = label.lowercase()
    return when {
        lowerKey.contains("artwork") || lowerLabel.contains("обложк") -> Icons.Outlined.Image
        lowerKey.contains("lava") || lowerLabel.contains("цвет") || lowerKey.contains("palette") -> Icons.Outlined.Palette
        lowerKey.contains("dark") || lowerKey.contains("none") || lowerLabel.contains("нет") || lowerLabel.contains("без фона") -> Icons.Outlined.Block
        lowerKey.contains("standard") || lowerLabel.contains("стандарт") -> Icons.Outlined.Brush
        lowerKey.contains("thin") || lowerLabel.contains("тонк") -> Icons.Outlined.Remove
        lowerKey.contains("ios") -> Icons.Outlined.ToggleOn
        lowerKey.contains("wave") || lowerLabel.contains("волн") -> Icons.Outlined.Equalizer
        lowerKey.contains("circle") || lowerLabel.contains("круг") -> Icons.Outlined.Circle
        lowerKey.contains("pill") || lowerLabel.contains("капсул") -> Icons.Outlined.SmartButton
        lowerKey.contains("source") || lowerLabel.contains("источник") -> Icons.Outlined.Info
        lowerKey.contains("text") || lowerLabel.contains("текст") || lowerKey.contains("lyrics") -> Icons.Outlined.Description
        lowerKey.contains("preset") || lowerLabel.contains("пресет") -> Icons.Outlined.AutoAwesome
        else -> null
    }
}

/** Resolves an appropriate subtitle for common setting options (as seen in screenshots). */
private fun resolveChoiceSubtitle(key: String, label: String): String? {
    val lowerKey = key.lowercase()
    val lowerLabel = label.lowercase()
    return when {
        lowerKey == "artwork" || (lowerLabel == "обложка" && !lowerKey.contains("mini")) -> "Размытая обложка трека"
        lowerKey == "lava" || lowerLabel.contains("адаптивный") -> "Адаптивный градиент под обложку"
        lowerKey == "dark" || lowerKey == "none" || lowerLabel.contains("без фона") -> "Без фона"
        lowerKey == "standard" -> "Стандартное оформление"
        lowerKey == "accent" -> "Цвет текущей обложки"
        lowerKey == "thin" -> "Минималистичный тонкий ползунок"
        lowerKey == "ios" -> "Стиль ползунка в стиле iOS"
        lowerKey == "wave" -> "Анимированная аудио-волна"
        lowerKey == "source" -> "Показывать источник трека"
        lowerKey == "text" -> "Показывать текущий текст песни"
        else -> null
    }
}

/** Legacy ChoiceDialog fallback delegating to ChoiceBottomSheet. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ChoiceBottomSheet(
        title = title,
        options = options,
        selected = selected,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

/** Full-width CTA pill. */
@Composable
fun SettingsCtaPill(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    Surface(
        onClick = {
            haptic()
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = VotifyColors.TextPrimary,
        contentColor = VotifyColors.PitchBlack,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** Small preview bar with action icons (Свайпы screen). */
@Composable
fun SwipePreviewBar(vararg icons: ImageVector) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VotifyColors.SurfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.forEach { Icon(it, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Icon + title + subtitle inside a 1x2 sync grid cell (Основные → Синхронизация). */
@Composable
fun SyncCell(
    icon: ImageVector,
    iconTint: Color = VotifyColors.TextPrimary,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberVotifyHaptic()
    Surface(
        onClick = {
            haptic()
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = VotifyColors.SurfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
    }
}
