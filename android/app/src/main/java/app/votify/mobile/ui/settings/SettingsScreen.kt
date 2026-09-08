package app.votify.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.BuildConfig
import app.votify.mobile.R
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.AudioQuality
import app.votify.mobile.ui.components.ConfirmDialog
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Settings per design/screens/settings.png: grouped cards with an uppercase section label on the
 * left and a muted tag on the right. Audio quality is a server-side setting; everything else is
 * local (DataStore).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        // Header
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

        // ---- Audio ----
        SectionLabel(stringResource(R.string.settings_section_audio), stringResource(R.string.settings_section_audio_tag))
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_quality), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (state.qualitySyncing) {
                        CircularProgressIndicator(color = VotifyColors.TextMuted, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text(qualityTitle(settings.audioQuality), style = MaterialTheme.typography.labelLarge, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        OptionTile(
                            title = qualityTitle(q),
                            subtitle = qualitySubtitle(q),
                            selected = settings.audioQuality == q,
                            enabled = !state.qualitySyncing,
                            onClick = { viewModel.setAudioQuality(q) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        state.qualityOffline -> stringResource(R.string.settings_quality_offline)
                        else -> stringResource(R.string.settings_quality_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                )
            }
        }

        // ---- Appearance ----
        SectionLabel(stringResource(R.string.settings_section_appearance), stringResource(R.string.settings_section_appearance_tag))
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeTile(Icons.Outlined.DarkMode, stringResource(R.string.settings_theme_oled), settings.theme == AppTheme.OledBlack, { viewModel.setTheme(AppTheme.OledBlack) }, Modifier.weight(1f))
                    ThemeTile(Icons.Outlined.Tonality, stringResource(R.string.settings_theme_graphite), settings.theme == AppTheme.Graphite, { viewModel.setTheme(AppTheme.Graphite) }, Modifier.weight(1f))
                    ThemeTile(Icons.Outlined.PhoneAndroid, stringResource(R.string.settings_theme_system), settings.theme == AppTheme.System, { viewModel.setTheme(AppTheme.System) }, Modifier.weight(1f))
                }

                Divider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_artwork), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(artworkTitle(settings.artworkStyle), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtworkTile(ArtworkStyle.Vinyl, settings.artworkStyle == ArtworkStyle.Vinyl, { viewModel.setArtworkStyle(ArtworkStyle.Vinyl) }, Modifier.weight(1f))
                    ArtworkTile(ArtworkStyle.Square, settings.artworkStyle == ArtworkStyle.Square, { viewModel.setArtworkStyle(ArtworkStyle.Square) }, Modifier.weight(1f))
                    ArtworkTile(ArtworkStyle.Blur, settings.artworkStyle == ArtworkStyle.Blur, { viewModel.setArtworkStyle(ArtworkStyle.Blur) }, Modifier.weight(1f))
                }

                Divider()

                SwitchRow(
                    title = stringResource(R.string.settings_lyrics_over_art),
                    subtitle = stringResource(R.string.settings_lyrics_over_art_sub),
                    checked = settings.showLyricsOverArtwork,
                    onChange = viewModel::setShowLyricsOverArtwork,
                )
            }
        }

        // ---- Gestures ----
        SectionLabel(stringResource(R.string.settings_section_gestures), stringResource(R.string.settings_section_gestures_tag))
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                Text(stringResource(R.string.settings_swipe), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_swipe_sub), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentButton(stringResource(R.string.settings_swipe_track), settings.miniPlayerSwipeChangesTrack, { viewModel.setMiniPlayerSwipeChangesTrack(true) }, Modifier.weight(1f))
                    SegmentButton(stringResource(R.string.settings_swipe_queue), !settings.miniPlayerSwipeChangesTrack, { viewModel.setMiniPlayerSwipeChangesTrack(false) }, Modifier.weight(1f))
                }

                Divider()

                SwitchRow(
                    title = stringResource(R.string.settings_volume_skip),
                    subtitle = stringResource(R.string.settings_volume_skip_sub),
                    checked = settings.volumeButtonsSkip,
                    onChange = viewModel::setVolumeButtonsSkip,
                )
            }
        }

        // ---- Data ----
        SectionLabel(stringResource(R.string.settings_section_data), stringResource(R.string.settings_section_data_tag))
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_data_stats, favoriteCount, historyCount), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
                Spacer(Modifier.width(12.dp))
                PillChip(
                    text = stringResource(R.string.action_delete),
                    selected = false,
                    onClick = { if (historyCount > 0) confirmClear = true },
                    leading = { Icon(Icons.Outlined.DeleteOutline, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                )
            }
        }

        // ---- About ----
        SectionLabel(stringResource(R.string.settings_section_about), stringResource(R.string.settings_section_about_tag))
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_about_name), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                    }
                    StatusPill(state.server, state.serverVersion)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_about_server, viewModel.apiBase),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    PillChip(
                        text = stringResource(R.string.settings_about_check),
                        selected = false,
                        onClick = {
                            viewModel.checkServer()
                            viewModel.syncQualityFromServer()
                        },
                        leading = { Icon(Icons.Outlined.Refresh, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.dialog_clear_history),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
        )
    }
}

// ---- pieces ----

@Composable
private fun SectionLabel(title: String, tag: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = VotifyColors.TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(tag, style = MaterialTheme.typography.labelMedium, color = VotifyColors.TextMuted)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(VotifyColors.BorderSubtle))
}

/** Selected: white fill + black text + check badge; otherwise #2A2A2A with hairline. */
@Composable
private fun OptionTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainerHigh)
            .border(1.dp, if (selected) VotifyColors.Primary else VotifyColors.BorderSubtle, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) VotifyColors.OnPrimary else VotifyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Check, null, tint = VotifyColors.OnPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) VotifyColors.OnPrimary.copy(alpha = 0.7f) else VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThemeTile(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainerHigh)
            .border(1.dp, if (selected) VotifyColors.Primary else VotifyColors.BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) VotifyColors.OnPrimary else VotifyColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) VotifyColors.OnPrimary else VotifyColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Artwork-style preview tiles: vinyl disc / square / blurred square. */
@Composable
private fun ArtworkTile(style: ArtworkStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(VotifyColors.SurfaceContainerHigh)
            .border(1.dp, if (selected) VotifyColors.Primary else VotifyColors.BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when (style) {
                ArtworkStyle.Vinyl -> Box(
                    Modifier.size(40.dp).clip(CircleShape).background(VotifyColors.PitchBlack).border(1.dp, VotifyColors.BorderProminent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(10.dp).clip(CircleShape).background(VotifyColors.TextPrimary)) }

                ArtworkStyle.Square -> Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(VotifyColors.SurfaceContainerHighest).border(1.dp, VotifyColors.BorderProminent, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CropSquare, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(16.dp)) }

                ArtworkStyle.Blur -> Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(VotifyColors.SurfaceContainerHighest.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.BlurOn, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(22.dp)) }
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = VotifyColors.Primary,
                    contentColor = VotifyColors.OnPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                ) { Icon(Icons.Filled.Check, null, modifier = Modifier.padding(2.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            artworkTitle(style),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) VotifyColors.TextPrimary else VotifyColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainerHigh,
        contentColor = if (selected) VotifyColors.OnPrimary else VotifyColors.TextSecondary,
        border = if (selected) null else BorderStroke(1.dp, VotifyColors.BorderSubtle),
        modifier = modifier.height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VotifyColors.OnPrimary,
                checkedTrackColor = VotifyColors.Primary,
                checkedBorderColor = VotifyColors.Primary,
                uncheckedThumbColor = VotifyColors.TextMuted,
                uncheckedTrackColor = VotifyColors.SurfaceContainerHigh,
                uncheckedBorderColor = VotifyColors.BorderProminent,
            ),
        )
    }
}

@Composable
private fun StatusPill(status: ServerStatus, version: String) {
    val (text, dot) = when (status) {
        ServerStatus.Online -> stringResource(R.string.settings_about_server_ok, version.ifBlank { "ok" }) to VotifyColors.TextPrimary
        ServerStatus.Offline -> stringResource(R.string.settings_about_server_fail) to VotifyColors.TextMuted
        ServerStatus.Checking, ServerStatus.Unknown -> "…" to VotifyColors.TextMuted
    }
    Surface(shape = CircleShape, color = VotifyColors.SurfaceContainerHigh, border = BorderStroke(1.dp, VotifyColors.BorderSubtle)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun qualityTitle(q: AudioQuality) = stringResource(
    when (q) {
        AudioQuality.Low -> R.string.settings_quality_low
        AudioQuality.Medium -> R.string.settings_quality_medium
        AudioQuality.High -> R.string.settings_quality_high
    },
)

@Composable
private fun qualitySubtitle(q: AudioQuality) = stringResource(
    when (q) {
        AudioQuality.Low -> R.string.settings_quality_low_sub
        AudioQuality.Medium -> R.string.settings_quality_medium_sub
        AudioQuality.High -> R.string.settings_quality_high_sub
    },
)

@Composable
private fun artworkTitle(s: ArtworkStyle) = stringResource(
    when (s) {
        ArtworkStyle.Vinyl -> R.string.settings_artwork_vinyl
        ArtworkStyle.Square -> R.string.settings_artwork_square
        ArtworkStyle.Blur -> R.string.settings_artwork_blur
    },
)
