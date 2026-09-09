package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** «Прокси»: адрес сервера Votify + HTTP-прокси для стриминга (интеграции). */
@Composable
fun ProxySettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var serverDraft by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var hostDraft by remember(prefs.proxyHost) { mutableStateOf(prefs.proxyHost) }
    var portDraft by remember(prefs.proxyPort) { mutableStateOf(prefs.proxyPort) }

    SettingsScaffold(stringResource(R.string.settings_proxy), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_server))
            SettingsCard {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(stringResource(R.string.settings_server_url), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_server_sub), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    VotifyTextField(serverDraft, { serverDraft = it }, "http://192.168.1.10:17217")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (state.server) {
                            ServerStatus.Online -> stringResource(R.string.settings_server_online, state.serverName)
                            ServerStatus.Checking -> stringResource(R.string.settings_server_checking)
                            ServerStatus.Embedded -> stringResource(R.string.settings_server_standalone)
                            ServerStatus.Offline -> stringResource(R.string.settings_server_offline)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                    )
                }
                SettingsCtaPill(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.saveServerUrl(serverDraft) },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_proxy))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_proxy),
                    subtitle = stringResource(R.string.settings_proxy_sub),
                    checked = prefs.proxyEnabled,
                    onChange = { v -> viewModel.updatePrefs { it.copy(proxyEnabled = v) } },
                )
                SettingsDivider()
                Column(Modifier.padding(vertical = 10.dp)) {
                    VotifyTextField(hostDraft, { hostDraft = it }, stringResource(R.string.settings_proxy_host))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) {
                            VotifyTextField(portDraft, { portDraft = it }, stringResource(R.string.settings_proxy_port))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_proxy_hint), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
                SettingsCtaPill(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        viewModel.updatePrefs { it.copy(proxyHost = hostDraft.trim(), proxyPort = portDraft.trim()) }
                    },
                )
            }
        }
    }
}
