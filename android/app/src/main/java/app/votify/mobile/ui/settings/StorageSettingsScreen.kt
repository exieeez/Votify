package app.votify.mobile.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val stats by viewModel.storage.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmHistory by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
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
                StorageRow(Icons.Outlined.MusicNote, stringResource(R.string.storage_tracks), stats.files.toString()) { sheet = "tracks" }
                SettingsDivider()
                StorageRow(Icons.Outlined.Download, stringResource(R.string.storage_offline), stats.offline.toString()) { sheet = "offline" }
                SettingsDivider()
                StorageRow(Icons.Outlined.GraphicEq, stringResource(R.string.storage_texts), stats.texts.toString()) { sheet = "texts" }
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

    if (confirmHistory) {
        ConfirmDialog(
            text = stringResource(R.string.storage_clear_history_confirm),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmHistory = false },
            onConfirm = {
                confirmHistory = false
                viewModel.clearHistory()
            },
        )
    }

    sheet?.let { kind ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = VotifyColors.SurfaceContainerLow,
            contentColor = VotifyColors.TextPrimary,
        ) {
            when (kind) {
                "tracks" -> TracksSheet(
                    stats = stats,
                    onClearHistory = { confirmHistory = true },
                )
                "offline" -> OfflineSheet(viewModel = viewModel)
                "texts" -> TextsSheet(
                    count = stats.texts,
                    onClear = {
                        sheet = null
                        viewModel.clearLyricsCache()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StorageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.labelLarge, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
    }
}

@Composable
private fun TracksSheet(stats: SettingsViewModel.StorageStats, onClearHistory: () -> Unit) {
    SheetTitle(stringResource(R.string.storage_tracks))
    SheetInfoRow(stringResource(R.string.storage_favorites), stats.fav.toString())
    SheetInfoRow(stringResource(R.string.storage_history), stats.hist.toString())
    SheetInfoRow(stringResource(R.string.storage_playlists), stats.pls.toString())
    if (stats.hist > 0) {
        SheetAction(
            icon = androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
            text = stringResource(R.string.storage_clear_history),
            onClick = onClearHistory,
        )
    }
}

@Composable
private fun OfflineSheet(viewModel: SettingsViewModel) {
    var items by remember { mutableStateOf<List<SettingsViewModel.DownloadItem>?>(null) }
    LaunchedEffect(Unit) { items = viewModel.downloads() }
    SheetTitle(stringResource(R.string.storage_offline))
    when (val list = items) {
        null -> Box(
            Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = VotifyColors.TextPrimary)
        }
        else -> if (list.isEmpty()) {
            Text(
                stringResource(R.string.storage_no_downloads),
                style = MaterialTheme.typography.bodyMedium,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(list, key = { it.file }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title.ifEmpty { "?" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VotifyColors.TextPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(item.artist, formatBytes(item.bytes)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteDownload(item.file)
                            items = list.filterNot { it.file == item.file }
                        }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
                                stringResource(R.string.action_delete),
                                tint = VotifyColors.TextMuted,
                            )
                        }
                    }
                }
            }
            SheetAction(
                icon = androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
                text = stringResource(R.string.storage_clear_downloads, list.size),
                onClick = {
                    viewModel.clearDownloads()
                    items = emptyList()
                },
            )
        }
    }
}

@Composable
private fun TextsSheet(count: Int, onClear: () -> Unit) {
    SheetTitle(stringResource(R.string.storage_texts))
    Text(
        stringResource(R.string.storage_texts_info, count),
        style = MaterialTheme.typography.bodyMedium,
        color = VotifyColors.TextMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (count > 0) {
        SheetAction(
            icon = androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
            text = stringResource(R.string.storage_clear_texts),
            onClick = onClear,
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = VotifyColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun SheetInfoRow(title: String, count: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = VotifyColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(count, style = MaterialTheme.typography.labelLarge, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = VotifyColors.SurfaceContainerHigh,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = VotifyColors.TextPrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}


private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
