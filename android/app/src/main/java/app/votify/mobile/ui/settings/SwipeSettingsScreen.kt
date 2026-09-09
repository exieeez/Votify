package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.theme.VotifyColors

/** «Свайпы»: действия свайпов для библиотеки, очереди, мини-плеера и плеера. */
@Composable
fun SwipeSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_swipes), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_library))
            SwipePreviewBar(Icons.Outlined.Add, Icons.Outlined.FavoriteBorder, Icons.Outlined.Delete)
            SettingsCard {
                SwipeRow(
                    arrow = Icons.Outlined.ArrowBack,
                    title = stringResource(R.string.swipe_left),
                    value = prefs.librarySwipeLeft,
                    onPick = { v -> viewModel.updatePrefs { it.copy(librarySwipeLeft = v) } },
                )
                SettingsDivider()
                SwipeRow(
                    arrow = Icons.Outlined.ArrowForward,
                    title = stringResource(R.string.swipe_right),
                    value = prefs.librarySwipeRight,
                    onPick = { v -> viewModel.updatePrefs { it.copy(librarySwipeRight = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_queue))
            SwipePreviewBar(Icons.Outlined.Delete, Icons.AutoMirrored.Filled.QueueMusic, Icons.Outlined.Delete)
            SettingsCard {
                SwipeRow(
                    arrow = Icons.Outlined.ArrowBack,
                    title = stringResource(R.string.swipe_left),
                    value = prefs.queueSwipeLeft,
                    onPick = { v -> viewModel.updatePrefs { it.copy(queueSwipeLeft = v) } },
                )
                SettingsDivider()
                SwipeRow(
                    arrow = Icons.Outlined.ArrowForward,
                    title = stringResource(R.string.swipe_right),
                    value = prefs.queueSwipeRight,
                    onPick = { v -> viewModel.updatePrefs { it.copy(queueSwipeRight = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_mini_player))
            SwipePreviewBar(Icons.Outlined.SkipPrevious, Icons.Outlined.PlayArrow, Icons.Outlined.SkipNext)
            SettingsCard {
                SwipeRow(
                    arrow = Icons.Outlined.ArrowBack,
                    title = stringResource(R.string.swipe_left),
                    value = prefs.miniSwipeLeft,
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniSwipeLeft = v) } },
                )
                SettingsDivider()
                SwipeRow(
                    arrow = Icons.Outlined.ArrowForward,
                    title = stringResource(R.string.swipe_right),
                    value = prefs.miniSwipeRight,
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniSwipeRight = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_player))
            SwipePreviewBar(Icons.Outlined.SkipPrevious, Icons.Outlined.Pause, Icons.Outlined.SkipNext)
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.player_swipes),
                    subtitle = stringResource(R.string.player_swipes_sub),
                    checked = prefs.playerSwipes,
                    onChange = { v -> viewModel.updatePrefs { it.copy(playerSwipes = v) } },
                )
            }
        }
    }
}

@Composable
private fun SwipeRow(
    arrow: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onPick: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(arrow, null, tint = VotifyColors.TextMuted, modifier = Modifier.padding(end = 8.dp))
        Box(Modifier.weight(1f)) {
            SettingsValueRow(
                title = title,
                subtitle = null,
                value = swipeActionLabel(value),
                trailing = swipeActionIcon(value),
                options = swipeActionOptions(),
                onPick = onPick,
            )
        }
    }
}

@Composable
private fun swipeActionOptions() = listOf(
    "queue" to stringResource(R.string.swipe_action_queue),
    "favorite" to stringResource(R.string.swipe_action_favorite),
    "delete" to stringResource(R.string.swipe_action_delete),
    "next" to stringResource(R.string.swipe_action_next),
    "previous" to stringResource(R.string.swipe_action_previous),
    "none" to stringResource(R.string.swipe_action_none),
)

@Composable
private fun swipeActionLabel(v: String): String = stringResource(
    when (v) {
        "queue" -> R.string.swipe_action_queue
        "favorite" -> R.string.swipe_action_favorite
        "delete" -> R.string.swipe_action_delete
        "next" -> R.string.swipe_action_next
        "previous" -> R.string.swipe_action_previous
        else -> R.string.swipe_action_none
    },
)

private fun swipeActionIcon(v: String) = when (v) {
    "queue" -> Icons.Outlined.PlayArrow
    "favorite" -> Icons.Outlined.FavoriteBorder
    "delete" -> Icons.Outlined.Delete
    "next" -> Icons.Outlined.SkipNext
    "previous" -> Icons.Outlined.SkipPrevious
    else -> null
}
