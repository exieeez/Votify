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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.NamedPreset
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** «Пресеты»: снимки настроек — создать/применить/импорт. */
@Composable
fun PresetsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var nameDialog by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    SettingsScaffold(stringResource(R.string.settings_presets), contentPadding, onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (prefs.presets.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 90.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Inventory2, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.presets_empty),
                                style = MaterialTheme.typography.titleMedium,
                                color = VotifyColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.presets_empty_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                            )
                        }
                    }
                } else {
                    prefs.presets.forEach { preset ->
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(VotifyColors.SurfaceContainerHigh)
                                .clickable { viewModel.applyPreset(preset) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Inventory2, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = VotifyColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            // export copies the preset JSON to the clipboard
                            Text(
                                stringResource(R.string.preset_export),
                                style = MaterialTheme.typography.labelMedium,
                                color = VotifyColors.TextSecondary,
                                modifier = Modifier
                                    .clickable {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(viewModel.exportPreset(preset)))
                                    }
                                    .padding(8.dp),
                            )
                            Surface(
                                onClick = { viewModel.deletePreset(preset.name) },
                                shape = RoundedCornerShape(50),
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                                contentColor = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(start = 4.dp).size(30.dp),
                            ) {
                                Icon(Icons.Filled.Close, null, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            Surface(
                onClick = { nameDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Add, null, tint = VotifyColors.TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.presets_create), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(
                onClick = { importDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = VotifyColors.SurfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.FileUpload, null, tint = VotifyColors.TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.presets_import), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (nameDialog) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nameDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.presets_create)) },
            text = { VotifyTextField(draft, { draft = it }, stringResource(R.string.preset_name_hint)) },
            confirmButton = {
                Text(
                    stringResource(R.string.action_save),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.createPreset(draft)
                            nameDialog = false
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.action_cancel),
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.clickable { nameDialog = false }.padding(8.dp),
                )
            },
        )
    }

    if (importDialog) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.presets_import)) },
            text = { VotifyTextField(draft, { draft = it }, stringResource(R.string.preset_json_hint), singleLine = false) },
            confirmButton = {
                Text(
                    stringResource(R.string.action_save),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.importPreset(draft)
                            importDialog = false
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.action_cancel),
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.clickable { importDialog = false }.padding(8.dp),
                )
            },
        )
    }
}
