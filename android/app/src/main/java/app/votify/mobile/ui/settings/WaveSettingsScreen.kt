package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.WaveLang
import app.votify.mobile.ui.theme.VotifyColors

/** «Настроить» под «Моей волной»: язык волны и поведение очереди. */
@Composable
fun WaveSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.wave_settings_title), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.wave_language))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.wave_language),
                    subtitle = stringResource(R.string.wave_hint),
                    value = when (settings.waveLang) {
                        WaveLang.Ukrainian -> stringResource(R.string.wave_lang_uk)
                        WaveLang.Russian -> stringResource(R.string.wave_lang_ru)
                        WaveLang.English -> stringResource(R.string.wave_lang_en)
                        WaveLang.Any -> stringResource(R.string.wave_lang_any)
                    },
                    trailing = Icons.Outlined.ChevronRight,
                    options = listOf(
                        WaveLang.Ukrainian.key to stringResource(R.string.wave_lang_uk),
                        WaveLang.Russian.key to stringResource(R.string.wave_lang_ru),
                        WaveLang.English.key to stringResource(R.string.wave_lang_en),
                        WaveLang.Any.key to stringResource(R.string.wave_lang_any),
                    ),
                    onPick = { key -> viewModel.setWaveLang(WaveLang.fromKey(key)) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSectionLabel(stringResource(R.string.home_my_wave))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.wave_exclude_listened),
                    subtitle = stringResource(R.string.wave_exclude_listened_desc),
                    checked = settings.waveExcludeListened,
                    onChange = viewModel::setWaveExcludeListened,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.wave_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
