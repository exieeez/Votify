package app.votify.mobile.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.AppIcon
import app.votify.mobile.data.AppIcons
import app.votify.mobile.data.customIconFile
import app.votify.mobile.ui.theme.VotifyColors

/** Подпись варианта иконки. */
@Composable
private fun iconLabel(icon: AppIcon): String = stringResource(
    when (icon) {
        AppIcon.Current -> R.string.icon_current
        AppIcon.BlackWhite -> R.string.icon_bw
        AppIcon.Glass -> R.string.icon_glass
        AppIcon.Classic -> R.string.icon_classic
        AppIcon.Custom -> R.string.icon_custom
    },
)

/**
 * Плитка одного варианта иконки: картинка, как её покажет лаунчер, и подпись. Активный
 * вариант обведён и помечен галочкой.
 */
@Composable
private fun IconOptionTile(
    bitmap: ImageBitmap?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) VotifyColors.SurfaceContainerHigh else VotifyColors.SurfaceContainerLow)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) VotifyColors.Primary else VotifyColors.BorderSubtle,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = label, modifier = Modifier.size(56.dp))
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                        .background(VotifyColors.SurfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = VotifyColors.TextMuted)
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(VotifyColors.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check, null,
                        tint = VotifyColors.OnPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) VotifyColors.TextPrimary else VotifyColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Плитка встроенного варианта: превью из ресурсов + подпись. */
@Composable
private fun BuiltInIconTile(
    icon: AppIcon,
    previews: Map<AppIcon, ImageBitmap?>,
    active: AppIcon,
    onSelect: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) = IconOptionTile(
    bitmap = previews[icon],
    label = iconLabel(icon),
    selected = active == icon,
    onClick = { onSelect(icon) },
    modifier = modifier,
)

/**
 * Раздел «Иконка приложения» — самый верхний в настройках (перед «Основными»).
 *
 * Варианты переключаются сразу по тапу: включается один launcher-алиас и выключаются
 * остальные, поэтому на рабочем столе всегда ровно одна иконка Votify. Для «своей»
 * картинки рядом есть строки: системный диалог смены иконки (Android 16+) и ярлык
 * на рабочий стол с картинкой.
 */
@Composable
fun AppIconSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val active by viewModel.activeIcon.collectAsStateWithLifecycle()
    val tick by viewModel.iconPreviewTick.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: android.net.Uri? ->
        uri?.let(viewModel::importAppIcon)
    }
    val launchPicker: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Превью собираем из ресурсов вариантов (tick — сигнал пересобрать после переключения).
    val previews: Map<AppIcon, ImageBitmap?> = remember(active, tick) {
        AppIcon.entries.associateWith { icon ->
            AppIcons.iconBitmap(context, icon, 160)?.asImageBitmap()
        }
    }
    val customBitmap: ImageBitmap? = remember(tick) {
        val file = customIconFile(context)
        if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
    }
    val customExists = remember(tick) { customIconFile(context).exists() }

    SettingsSectionLabel(stringResource(R.string.icon_section))
    SettingsCard {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            // Встроенные варианты — по две плитки в ряд.
            AppIcon.builtIn.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { icon ->
                        BuiltInIconTile(
                            icon = icon,
                            previews = previews,
                            active = active,
                            onSelect = viewModel::selectAppIcon,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            // Своя иконка: картинка пользователя или приглашение её выбрать.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconOptionTile(
                    bitmap = customBitmap,
                    label = iconLabel(AppIcon.Custom),
                    selected = active == AppIcon.Custom,
                    onClick = {
                        if (customExists) viewModel.selectAppIcon(AppIcon.Custom) else launchPicker()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }

            SettingsDivider()
            SettingsNavRow(
                Icons.Outlined.Image,
                stringResource(if (customExists) R.string.icon_change else R.string.icon_pick),
                stringResource(R.string.icon_custom),
            ) { launchPicker() }
            SettingsNavRow(
                Icons.Outlined.AddToHomeScreen,
                stringResource(R.string.icon_shortcut_title),
                stringResource(R.string.icon_shortcut_sub),
            ) { viewModel.pinCustomIconShortcut() }
            SettingsNavRow(
                Icons.Outlined.Palette,
                stringResource(R.string.icon_system_title),
                stringResource(R.string.icon_system_sub),
            ) {
                val intent = viewModel.systemIconIntent()
                if (intent == null) viewModel.onIconSystemUnavailable()
                else runCatching { context.startActivity(intent) }
            }

            Text(
                stringResource(R.string.icon_note),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            )
        }
    }
}
