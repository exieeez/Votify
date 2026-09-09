package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.PlayerBackground

/** «Плеер»: стиль, заголовок, слайдер, кнопка, плашка, фон + вся секция мини-плеера. */
@Composable
fun PlayerSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_player), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_player_style))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_player_style),
                    subtitle = null,
                    value = playerStyleLabel(settings.artworkStyle.key),
                    trailing = Icons.Outlined.Album,
                    options = listOf(
                        "vinyl" to stringResource(R.string.settings_artwork_vinyl),
                        "square" to stringResource(R.string.settings_artwork_square),
                        "circle" to stringResource(R.string.settings_artwork_circle),
                        "blur" to stringResource(R.string.settings_artwork_blur),
                    ),
                    onPick = { key ->
                        when (key) {
                            "square" -> viewModel.setArtworkStyle(ArtworkStyle.Square)
                            "circle" -> viewModel.setArtworkStyle(ArtworkStyle.Circle)
                            "blur" -> viewModel.setArtworkStyle(ArtworkStyle.Blur)
                            else -> viewModel.setArtworkStyle(ArtworkStyle.Vinyl)
                        }
                        viewModel.updatePrefs { it.copy(playerStyle = key) }
                    },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_title_section))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_title_align),
                    subtitle = null,
                    value = if (prefs.titleAlign == "left") stringResource(R.string.settings_align_left) else stringResource(R.string.settings_align_center),
                    trailing = Icons.Outlined.FormatAlignCenter,
                    options = listOf(
                        "center" to stringResource(R.string.settings_align_center),
                        "left" to stringResource(R.string.settings_align_left),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(titleAlign = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_slider))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_slider_type),
                    subtitle = null,
                    value = if (prefs.sliderStyle == "classic") stringResource(R.string.settings_slider_classic) else "iOS",
                    trailing = Icons.Outlined.ToggleOn,
                    options = listOf(
                        "ios" to "iOS",
                        "classic" to stringResource(R.string.settings_slider_classic),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(sliderStyle = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_play_button))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_play_button_style),
                    subtitle = null,
                    value = if (prefs.playButtonStyle == "pill") stringResource(R.string.settings_shape_pill) else stringResource(R.string.settings_shape_circle),
                    trailing = Icons.Outlined.Circle,
                    options = listOf(
                        "circle" to stringResource(R.string.settings_shape_circle),
                        "pill" to stringResource(R.string.settings_shape_pill),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(playButtonStyle = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_info_chip))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_info_chip_show),
                    subtitle = null,
                    value = infoChipLabel(prefs.infoChip),
                    trailing = Icons.Outlined.Info,
                    options = listOf(
                        "source" to stringResource(R.string.settings_chip_source),
                        "text" to stringResource(R.string.settings_chip_text),
                        "none" to stringResource(R.string.swipe_action_none),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(infoChip = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_player_bg))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_player_bg),
                    subtitle = null,
                    value = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> stringResource(R.string.settings_player_bg_artwork)
                        PlayerBackground.Lava -> stringResource(R.string.settings_player_bg_lava)
                        else -> stringResource(R.string.settings_player_bg_dark)
                    },
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    options = listOf(
                        "artwork" to stringResource(R.string.settings_player_bg_artwork),
                        "lava" to stringResource(R.string.settings_player_bg_lava),
                        "dark" to stringResource(R.string.settings_player_bg_dark),
                    ),
                    onPick = { v ->
                        viewModel.setPlayerBackground(
                            when (v) {
                                "artwork" -> PlayerBackground.Artwork
                                "lava" -> PlayerBackground.Lava
                                else -> PlayerBackground.Dark
                            },
                        )
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_lyrics_over_art),
                    subtitle = stringResource(R.string.settings_lyrics_over_art_sub),
                    checked = settings.showLyricsOverArtwork,
                    onChange = viewModel::setShowLyricsOverArtwork,
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_mini_player))
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.settings_mini_presets),
                    subtitle = stringResource(R.string.settings_mini_presets_sub),
                    onClick = onOpenPresets,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_bg),
                    subtitle = null,
                    value = if (prefs.miniBg == "artwork") stringResource(R.string.settings_mini_bg_artwork) else stringResource(R.string.settings_mini_bg_plain),
                    trailing = Icons.Outlined.Palette,
                    options = listOf(
                        "plain" to stringResource(R.string.settings_mini_bg_plain),
                        "artwork" to stringResource(R.string.settings_mini_bg_artwork),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniBg = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_progress),
                    subtitle = null,
                    value = miniProgressLabel(prefs.miniProgress),
                    trailing = Icons.Outlined.Equalizer,
                    options = listOf(
                        "ring" to stringResource(R.string.settings_mini_progress_ring),
                        "bar" to stringResource(R.string.settings_mini_progress_bar),
                        "none" to stringResource(R.string.swipe_action_none),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniProgress = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_cover),
                    subtitle = null,
                    value = if (prefs.miniCoverShape == "circle") stringResource(R.string.settings_shape_circle) else stringResource(R.string.settings_shape_rounded),
                    trailing = Icons.Outlined.Circle,
                    options = listOf(
                        "circle" to stringResource(R.string.settings_shape_circle),
                        "rounded" to stringResource(R.string.settings_shape_rounded),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCoverShape = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_corners),
                    subtitle = null,
                    value = if (prefs.miniCorners == "pill") stringResource(R.string.settings_shape_pill) else stringResource(R.string.settings_shape_rounded),
                    trailing = Icons.Outlined.Circle,
                    options = listOf(
                        "pill" to stringResource(R.string.settings_shape_pill),
                        "rounded" to stringResource(R.string.settings_shape_rounded),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCorners = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_buttons),
                    subtitle = null,
                    value = miniButtonsLabel(prefs.miniButtons),
                    trailing = Icons.Outlined.SmartButton,
                    options = listOf(
                        "both" to stringResource(R.string.settings_mini_buttons_both),
                        "play" to stringResource(R.string.settings_mini_buttons_play),
                        "none" to stringResource(R.string.swipe_action_none),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniButtons = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_button_style),
                    subtitle = null,
                    value = if (prefs.miniButtonStyle == "outline") stringResource(R.string.settings_button_outline) else stringResource(R.string.settings_button_filled),
                    trailing = Icons.Outlined.SmartButton,
                    options = listOf(
                        "filled" to stringResource(R.string.settings_button_filled),
                        "outline" to stringResource(R.string.settings_button_outline),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniButtonStyle = v) } },
                )
            }
        }
    }
}

@Composable
private fun playerStyleLabel(v: String): String = stringResource(
    when (v) {
        "square" -> R.string.settings_artwork_square
        "circle" -> R.string.settings_artwork_circle
        "blur" -> R.string.settings_artwork_blur
        else -> R.string.settings_artwork_vinyl
    },
)

@Composable
private fun infoChipLabel(v: String): String = stringResource(
    when (v) {
        "text" -> R.string.settings_chip_text
        "none" -> R.string.swipe_action_none
        else -> R.string.settings_chip_source
    },
)

@Composable
private fun miniProgressLabel(v: String): String = stringResource(
    when (v) {
        "bar" -> R.string.settings_mini_progress_bar
        "none" -> R.string.swipe_action_none
        else -> R.string.settings_mini_progress_ring
    },
)

@Composable
private fun miniButtonsLabel(v: String): String = stringResource(
    when (v) {
        "play" -> R.string.settings_mini_buttons_play
        "none" -> R.string.swipe_action_none
        else -> R.string.settings_mini_buttons_both
    },
)
