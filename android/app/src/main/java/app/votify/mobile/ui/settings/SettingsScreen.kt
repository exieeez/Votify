package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Main settings screen (Dotify): grouped cards of navigational rows.
 * ОСНОВНЫЕ / ВНЕШНИЙ ВИД / КАСТОМИЗАЦИЯ / ИНТЕГРАЦИИ — every row opens its own screen.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSwipe: () -> Unit,
    onOpenInterface: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenArtwork: () -> Unit,
    onOpenBackgrounds: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenProxy: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        SettingsSectionLabel(stringResource(R.string.settings_cat_main))
        SettingsCard {
            SettingsNavRow(Icons.Outlined.Settings, stringResource(R.string.settings_general), null, onOpenGeneral)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.MusicNote, stringResource(R.string.settings_audio), null, onOpenAudio)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.Storage, stringResource(R.string.settings_storage), null, onOpenStorage)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.SwapHoriz, stringResource(R.string.settings_swipes), null, onOpenSwipe)
        }

        SettingsSectionLabel(stringResource(R.string.settings_cat_appearance))
        SettingsCard {
            SettingsNavRow(Icons.Outlined.DesktopWindows, stringResource(R.string.settings_interface), null, onOpenInterface)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.PlayArrow, stringResource(R.string.settings_player), null, onOpenPlayer)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.Image, stringResource(R.string.settings_artwork), null, onOpenArtwork)
        }

        SettingsSectionLabel(stringResource(R.string.settings_cat_customization))
        SettingsCard {
            SettingsNavRow(Icons.Outlined.Collections, stringResource(R.string.settings_backgrounds), null, onOpenBackgrounds)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.ViewInAr, stringResource(R.string.settings_presets), null, onOpenPresets)
            SettingsDivider()
            SettingsNavRow(Icons.Outlined.Inventory2, stringResource(R.string.settings_workshop_short), null, onOpenWorkshop)
        }

        SettingsSectionLabel(stringResource(R.string.settings_cat_integrations))
        SettingsCard {
            SettingsNavRow(Icons.Outlined.Security, stringResource(R.string.settings_proxy), null, onOpenProxy)
        }
    }
}
