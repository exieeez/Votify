package app.votify.mobile.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** «Обложка»: GIF-обложка, анимация, эффекты, обложка в обложке. */
@Composable
fun ArtworkSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var gifDialog by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(R.string.settings_artwork), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_gif_section))
            SettingsCard {
                Surface(onClick = { gifDialog = true }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_gif_artwork),
                                style = MaterialTheme.typography.titleSmall,
                                color = VotifyColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            if (prefs.gifArtwork.isBlank()) stringResource(R.string.settings_gif_none) else stringResource(R.string.settings_gif_picked),
                            style = MaterialTheme.typography.labelLarge,
                            color = VotifyColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = VotifyColors.Primary) {
                            Text(
                                "GIF",
                                style = MaterialTheme.typography.labelSmall,
                                color = VotifyColors.OnPrimary,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            SettingsSectionLabel(stringResource(R.string.settings_animation))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_artwork_animation),
                    subtitle = null,
                    value = animLabel(prefs.artworkAnimation),
                    trailing = Icons.Outlined.RotateRight,
                    options = listOf(
                        "none" to stringResource(R.string.swipe_action_none),
                        "spin" to stringResource(R.string.settings_anim_spin),
                        "sway" to stringResource(R.string.settings_anim_sway),
                        "pulse" to stringResource(R.string.settings_anim_pulse),
                        "float" to stringResource(R.string.settings_anim_float),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(artworkAnimation = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_effects))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_artwork_effect),
                    subtitle = null,
                    value = effectLabel(prefs.artworkEffect),
                    trailing = Icons.Outlined.Close,
                    options = listOf(
                        "none" to stringResource(R.string.swipe_action_none),
                        "grayscale" to stringResource(R.string.settings_effect_grayscale),
                        "blur" to stringResource(R.string.settings_effect_blur),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(artworkEffect = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_extra))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_artwork_inside),
                    subtitle = stringResource(R.string.settings_artwork_inside_sub),
                    checked = prefs.artworkInside,
                    onChange = { v -> viewModel.updatePrefs { it.copy(artworkInside = v) } },
                )
            }
        }
    }

    if (gifDialog) {
        var draft by remember { mutableStateOf(prefs.gifArtwork) }
        AlertDialog(
            onDismissRequest = { gifDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.settings_gif_artwork)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_gif_hint), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    VotifyTextField(draft, { draft = it }, stringResource(R.string.workshop_bg_hint_field))
                }
            },
            confirmButton = {
                Text(
                    stringResource(R.string.action_save),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.updatePrefs { it.copy(gifArtwork = draft.trim()) }
                            gifDialog = false
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Row {
                    if (prefs.gifArtwork.isNotBlank()) {
                        Text(
                            stringResource(R.string.workshop_bg_clear),
                            color = VotifyColors.TextMuted,
                            modifier = Modifier
                                .clickable {
                                    viewModel.updatePrefs { it.copy(gifArtwork = "") }
                                    gifDialog = false
                                }
                                .padding(8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.action_cancel),
                        color = VotifyColors.TextMuted,
                        modifier = Modifier
                            .clickable { gifDialog = false }
                            .padding(8.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun animLabel(v: String): String = stringResource(
    when (v) {
        "spin" -> R.string.settings_anim_spin
        "sway" -> R.string.settings_anim_sway
        "pulse" -> R.string.settings_anim_pulse
        "float" -> R.string.settings_anim_float
        else -> R.string.swipe_action_none
    },
)

@Composable
private fun effectLabel(v: String): String = stringResource(
    when (v) {
        "grayscale" -> R.string.settings_effect_grayscale
        "blur" -> R.string.settings_effect_blur
        else -> R.string.swipe_action_none
    },
)
