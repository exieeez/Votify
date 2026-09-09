package app.votify.mobile.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.BuildConfig
import app.votify.mobile.R
import app.votify.mobile.ui.theme.VotifyColors

/** «Основные»: язык, синхронизация, профиль, уведомления, канвас, о приложении, обновления. */
@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(stringResource(R.string.settings_general), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_language))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = null,
                    value = stringResource(R.string.settings_language_ru),
                    trailing = Icons.Outlined.Language,
                    options = listOf(
                        "ru" to stringResource(R.string.settings_language_ru),
                        "en" to stringResource(R.string.settings_language_en),
                    ),
                    onPick = { viewModel.setLanguage(it) },
                    // language switching is RU-only for now
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_general))
            SettingsCard {
                // Профиль
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val acct = account
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(VotifyColors.SurfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (acct == null) {
                            Icon(Icons.Outlined.Person, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                acct.username.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = VotifyColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            acct?.username?.ifBlank { acct.email } ?: stringResource(R.string.settings_account_login),
                            style = MaterialTheme.typography.titleSmall,
                            color = VotifyColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            acct?.email ?: stringResource(R.string.settings_account_login_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = VotifyColors.TextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (acct != null) {
                        Surface(
                            onClick = viewModel::logout,
                            shape = CircleShape,
                            color = VotifyColors.SurfaceContainerHigh,
                            contentColor = VotifyColors.TextPrimary,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.padding(8.dp).size(18.dp))
                        }
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VotifyColors.TextMuted)
                        Spacer(Modifier.width(2.dp))
                        Surface(onClick = onOpenAccount, shape = CircleShape, color = VotifyColors.SurfaceContainerHigh) {
                            Text(
                                stringResource(R.string.settings_account_login),
                                style = MaterialTheme.typography.labelMedium,
                                color = VotifyColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_sub),
                    checked = prefs.notifications,
                    onChange = { v -> viewModel.updatePrefs { it.copy(notifications = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_extra))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_canvas),
                    subtitle = stringResource(R.string.settings_canvas_sub),
                    checked = prefs.canvas,
                    onChange = { v -> viewModel.updatePrefs { it.copy(canvas = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_about))
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coil.compose.AsyncImage(
                        model = R.drawable.ic_launcher_visual,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Votify", style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    }
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.settings_terms),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.settings_subscriptions),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsCtaPill(
                text = stringResource(R.string.settings_check_updates),
                icon = Icons.Filled.Refresh,
                onClick = viewModel::checkUpdates,
            )
        }
    }
}
