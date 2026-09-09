package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.AudioQuality

/** «Аудио»: качество, кэш, бесшовность, кроссфейд, нормализация, автоплей, очередь. */
@Composable
fun AudioSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_audio), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_audio))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_quality),
                    subtitle = stringResource(R.string.settings_quality_sub),
                    value = qualityLabel(settings.audioQuality),
                    trailing = Icons.Outlined.Headphones,
                    options = listOf(
                        "low" to qualityLabel(AudioQuality.Low),
                        "medium" to qualityLabel(AudioQuality.Medium),
                        "high" to qualityLabel(AudioQuality.High),
                    ),
                    onPick = { key ->
                        AudioQuality.entries.firstOrNull { it.key == key }?.let(viewModel::setAudioQuality)
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_caching),
                    subtitle = stringResource(R.string.settings_caching_sub),
                    checked = prefs.caching,
                    onChange = { v -> viewModel.updatePrefs { it.copy(caching = v) } },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_gapless),
                    subtitle = stringResource(R.string.settings_gapless_sub),
                    checked = prefs.gapless,
                    onChange = { v -> viewModel.updatePrefs { it.copy(gapless = v) } },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_crossfade),
                    subtitle = stringResource(R.string.settings_crossfade_sub),
                    checked = prefs.crossfade,
                    onChange = { v -> viewModel.updatePrefs { it.copy(crossfade = v) } },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_normalization),
                    subtitle = stringResource(R.string.settings_normalization_sub),
                    checked = prefs.normalization,
                    onChange = { v -> viewModel.updatePrefs { it.copy(normalization = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_on_notification),
                    subtitle = null,
                    value = onNotificationLabel(prefs.onNotification),
                    trailing = Icons.Outlined.Speaker,
                    options = listOf(
                        "duck" to stringResource(R.string.settings_on_notification_duck),
                        "pause" to stringResource(R.string.settings_on_notification_pause),
                        "continue" to stringResource(R.string.settings_on_notification_continue),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(onNotification = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_autoplay))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_autoplay),
                    subtitle = stringResource(R.string.settings_autoplay_sub),
                    checked = prefs.autoplay,
                    onChange = { v -> viewModel.updatePrefs { it.copy(autoplay = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_queue))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_similar),
                    subtitle = stringResource(R.string.settings_similar_sub),
                    checked = prefs.similarToQueue,
                    onChange = { v -> viewModel.updatePrefs { it.copy(similarToQueue = v) } },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_restore_queue),
                    subtitle = stringResource(R.string.settings_restore_queue_sub),
                    checked = prefs.restoreQueue,
                    onChange = { v -> viewModel.updatePrefs { it.copy(restoreQueue = v) } },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_skip),
                    subtitle = stringResource(R.string.settings_volume_skip_sub),
                    checked = settings.volumeButtonsSkip,
                    onChange = viewModel::setVolumeButtonsSkip,
                )
            }
        }
    }
}

@Composable
private fun qualityLabel(q: AudioQuality): String = stringResource(
    when (q) {
        AudioQuality.Low -> R.string.settings_quality_low
        AudioQuality.Medium -> R.string.settings_quality_medium
        AudioQuality.High -> R.string.settings_quality_high
    },
)

@Composable
private fun onNotificationLabel(v: String): String = stringResource(
    when (v) {
        "pause" -> R.string.settings_on_notification_pause
        "continue" -> R.string.settings_on_notification_continue
        else -> R.string.settings_on_notification_duck
    },
)
