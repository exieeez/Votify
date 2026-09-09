package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.components.ConfirmDialog
import app.votify.mobile.ui.theme.VotifyColors

/** «Хранилище»: usage widget, данные по категориям, «Очистить всё». */
@Composable
fun StorageSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val stats by viewModel.storage.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    SettingsScaffold(stringResource(R.string.settings_storage), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Memory widget
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(vertical = 28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(shape = RoundedCornerShape(50), color = VotifyColors.TextPrimary, modifier = Modifier.size(10.dp)) {}
                    Spacer(Modifier.height(14.dp))
                    Text(
                        formatBytes(stats.bytes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.storage_files, stats.files),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                    )
                    Text(
                        stringResource(R.string.storage_percent, stats.percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                    )
                }
            }

            SettingsSectionLabel(stringResource(R.string.storage_cleanup))
            SettingsCard {
                StorageRow(Icons.Outlined.MusicNote, stringResource(R.string.storage_tracks), stats.files.toString())
                SettingsDivider()
                StorageRow(Icons.Outlined.Download, stringResource(R.string.storage_offline), stats.offline.toString())
                SettingsDivider()
                StorageRow(Icons.Outlined.GraphicEq, stringResource(R.string.storage_texts), stats.texts.toString())
            }

            if (stats.offline > 0) {
                Surface(
                    onClick = viewModel::clearDownloads,
                    shape = RoundedCornerShape(18.dp),
                    color = VotifyColors.SurfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Download, null, tint = VotifyColors.TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.storage_clear_downloads, stats.offline),
                            style = MaterialTheme.typography.titleSmall,
                            color = VotifyColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Surface(
                onClick = { if (stats.files > 0 || stats.bytes > 0) confirmClear = true },
                shape = RoundedCornerShape(18.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = VotifyColors.TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.storage_clear_all),
                        style = MaterialTheme.typography.titleSmall,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.storage_clear_confirm),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.clearAllData()
            },
        )
    }
}

@Composable
private fun StorageRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.labelLarge, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
