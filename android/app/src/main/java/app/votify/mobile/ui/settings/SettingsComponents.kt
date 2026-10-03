package app.votify.mobile.ui.settings

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
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.votify.mobile.R
import app.votify.mobile.ui.components.rememberVotifyHaptic
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Modern Votify settings chrome: a round back button on the left and an elongated pill
 * with the screen title filling the rest of the row (matching screenshots).
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
                color = Color(0xFF141416),
                contentColor = Color.White,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(23.dp),
                color = Color(0xFF141416),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = Color.White,
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
        color = Color(0xFF8E8E93),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Clean solid container card without borders or outlines (native sleek look). */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    val shape = RoundedCornerShape(18.dp)
    val cardBg = Color(0xFF141416)
    val base = modifier
        .padding(horizontal = 14.dp, vertical = 3.dp)
        .fillMaxWidth()
        .clip(shape)
        .background(cardBg)

    val clickableModifier = if (onClick != null) {
        base.clickable {
            haptic()
            onClick()
        }
    } else base

    Box(
        modifier = clickableModifier.padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(0.6.dp)
            .background(Color.White.copy(alpha = 0.05f)),
    )
}

/** icon | title (+subtitle) | trailing icon/chevron — navigational row. */
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
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Color.White,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color(0xFF8E8E93),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(20.dp))
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
                color = Color.White,
                fontWeight = FontWeight.Normal,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color(0xFF8E8E93),
                )
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
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                checkedBorderColor = Color.White,
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF242426),
                uncheckedBorderColor = Color(0xFF38383A),
            ),
        )
    }
}

data class ChoiceOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
)

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
    options: List<Pair<String, String>> = emptyList(),
    choiceOptions: List<ChoiceOption>? = null,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Color.White,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle ?: value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Icon(trailing, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(20.dp))
        }
    }

    if (open) {
        val resolvedChoices = choiceOptions ?: options.map { (k, l) ->
            ChoiceOption(
                key = k,
                label = l,
                subtitle = resolveChoiceSubtitle(k, l),
                icon = resolveChoiceIcon(k, l),
            )
        }
        val currentSelectedKey = resolvedChoices.firstOrNull { it.key == value || it.label == value }?.key ?: value

        ChoiceBottomSheet(
            title = title,
            options = resolvedChoices,
            selected = currentSelectedKey,
            onDismiss = { open = false },
            onPick = {
                onPick(it)
                open = false
            },
        )
    }
}

/**
 * Modern modal bottom sheet selection picker matching the user's design:
 * - Rounded top corners (no border lines)
 * - Centered subtle drag handle
 * - Interactive slider preview if selecting slider style
 * - Individual rounded option cards with solid dark background, zero borders,
 *   circular icon pill only when selected, and white checkmark on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceBottomSheet(
    title: String,
    options: List<ChoiceOption>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val haptic = rememberVotifyHaptic()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F11),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
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
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Slider preview card as shown in user's Screenshot 4
            if (title.contains("слайдер", ignoreCase = true) || title.contains("slider", ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.38f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 110.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1:24", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color(0xFF8E8E93))
                            Text("3:45", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color(0xFF8E8E93))
                        }
                    }
                }
            }

            options.forEach { option ->
                val isSelected = option.key == selected || option.label == selected
                val icon = option.icon ?: resolveChoiceIcon(option.key, option.label)
                val subtitle = option.subtitle ?: resolveChoiceSubtitle(option.key, option.label)

                Surface(
                    onClick = {
                        haptic()
                        onPick(option.key)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2C2C30)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else {
                                Box(
                                    Modifier.size(38.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Normal,
                                color = Color.White,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = Color(0xFF8E8E93),
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

@Composable
fun ChoiceBottomSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ChoiceBottomSheet(
        title = title,
        options = options.map { (key, label) ->
            ChoiceOption(
                key = key,
                label = label,
                subtitle = resolveChoiceSubtitle(key, label),
                icon = resolveChoiceIcon(key, label),
            )
        },
        selected = selected,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

/** Resolves an appropriate descriptive icon for common setting options. */
private fun resolveChoiceIcon(key: String, label: String): ImageVector? {
    val lowerKey = key.lowercase()
    val lowerLabel = label.lowercase()
    return when {
        lowerKey == "square" || lowerLabel == "стандартный" && lowerKey.contains("square") -> Icons.Outlined.MusicNote
        lowerKey == "blur" || lowerLabel == "большой" -> Icons.Outlined.Tv
        lowerKey == "vinyl" || lowerLabel == "пластинка" -> Icons.Outlined.Album
        lowerKey.contains("artwork") || lowerLabel.contains("обложк") -> Icons.Outlined.Image
        lowerKey.contains("lava") || lowerLabel.contains("цвет") || lowerKey.contains("palette") -> Icons.Outlined.Palette
        lowerKey == "none" || lowerKey == "dark" || lowerLabel.contains("нет") || lowerLabel.contains("без фона") -> Icons.Outlined.Block
        lowerKey == "classic" || lowerLabel == "стандартный" -> Icons.Outlined.RadioButtonUnchecked
        lowerKey == "thin" || lowerLabel.contains("тонк") -> Icons.Outlined.Remove
        lowerKey.contains("ios") -> Icons.Outlined.ToggleOn
        lowerKey.contains("wave") || lowerLabel.contains("волн") -> Icons.Outlined.Equalizer
        lowerKey == "circle" || lowerLabel.contains("круг") -> Icons.Outlined.RadioButtonUnchecked
        lowerKey == "pill" -> Icons.Outlined.RadioButtonUnchecked
        lowerKey == "soft" || lowerLabel.contains("мягк") -> Icons.Outlined.GridView
        lowerKey == "rounded" || lowerLabel.contains("закруглен") -> Icons.Outlined.GridView
        lowerKey == "minimal" || lowerLabel.contains("минималистичн") -> Icons.Outlined.Grain
        lowerKey == "filled" || lowerLabel.contains("залит") -> Icons.Outlined.RadioButtonUnchecked
        lowerKey == "standard" || lowerLabel.contains("стандартн") -> Icons.Outlined.MoreHoriz
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
        lowerKey == "square" || (lowerLabel == "стандартный" && !lowerKey.contains("classic")) -> "Классический вид с обложкой"
        lowerKey == "blur" || lowerLabel == "большой" -> "Обложка на весь экран"
        lowerKey == "vinyl" || lowerLabel == "пластинка" -> "Виниловая пластинка с вращением"
        lowerKey == "artwork" || (lowerLabel == "обложка" && !lowerKey.contains("mini")) -> "Размытая обложка трека"
        lowerKey == "lava" || lowerLabel.contains("адаптивный") || lowerLabel == "цвет" -> "Адаптивный градиент под обложку"
        lowerKey == "dark" || (lowerKey == "none" && lowerLabel.contains("нет")) -> "Без фона"
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
        color = Color.White,
        contentColor = Color.Black,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black)
        }
    }
}

/** Small preview bar with action icons (Свайпы screen). */
@Composable
fun SwipePreviewBar(vararg icons: ImageVector) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1C1C1E),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.forEach { Icon(it, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Icon + title + subtitle inside a 1x2 sync grid cell (Основные → Синхронизация). */
@Composable
fun SyncCell(
    icon: ImageVector,
    iconTint: Color = Color.White,
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
        color = Color(0xFF141416),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8E93))
        }
    }
}
