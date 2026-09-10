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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Dotify-style settings chrome: a round back button on the left and a centered pill with the
 * screen title (#1E1E1E). Shared by every settings sub-screen.
 */
@Composable
fun SettingsScaffold(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = VotifyColors.SurfaceContainerHigh,
                contentColor = VotifyColors.TextPrimary,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(40.dp))
        }
        content()
    }
}

/** UPPERCASE gray section label (Dotify). */
@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = VotifyColors.TextSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Card with internal thin dividers between rows. */
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    VotifyCard(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) { Column { content() } }
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(VotifyColors.BorderSubtle.copy(alpha = 0.6f)))
}

/** icon | title (+subtitle) | chevron — navigational row. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
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

/** Title (+subtitle) with a toggle — Dotify switch: white pill when on. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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

/** Title (+subtitle) | current value | trailing icon — opens a single-choice dialog. */
@Composable
fun SettingsValueRow(
    title: String,
    subtitle: String? = null,
    value: String,
    trailing: ImageVector? = null,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
            }
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailing, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        ChoiceDialog(
            title = title,
            options = options,
            selected = options.firstOrNull { it.first == currentKey(options, value) }?.first,
            onDismiss = { open = false },
            onPick = { onPick(it); open = false },
        )
    }
}

private fun currentKey(options: List<Pair<String, String>>, value: String): String =
    options.firstOrNull { it.second == value }?.first ?: ""

/** Single-choice list dialog in Votify colors. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>, // key -> label
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VotifyColors.SurfaceContainerHigh,
        titleContentColor = VotifyColors.TextPrimary,
        textContentColor = VotifyColors.TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(key) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = VotifyColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (key == selected) {
                            Icon(Icons.Filled.Check, null, tint = VotifyColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                stringResource(R.string.action_cancel),
                color = VotifyColors.TextMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        },
    )
}

/** Full-width white CTA pill (Dotify «Проверить обновления» style). */
@Composable
fun SettingsCtaPill(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = VotifyColors.TextPrimary,
        contentColor = VotifyColors.PitchBlack,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
    Surface(
        onClick = onClick,
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
