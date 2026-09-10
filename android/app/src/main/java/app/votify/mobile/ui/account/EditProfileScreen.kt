package app.votify.mobile.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.SocialValidate
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** Profile editor: avatar, name, username claim, bio, links and privacy. */
@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val edit = state.edit

    LaunchedEffect(viewModel) {
        viewModel.startEdit()
        viewModel.events.collect { e ->
            when (e) {
                is ProfileViewModel.ProfileEvent.Message ->
                    onMessage(context.getString(e.msg.id, *e.msg.args.toTypedArray()))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dataUrl = processAvatarImage(context, uri)
        if (dataUrl == null) {
            onMessage(context.getString(R.string.edit_avatar_failed))
        } else {
            viewModel.editField(avatar = dataUrl)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringResource(R.string.edit_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            edit == null || edit.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.TextPrimary)
            }
            edit.noBackend -> CenteredNotice(
                text = stringResource(R.string.profile_need_firebase),
                action = stringResource(R.string.nav_back),
                onAction = onBack,
            )
            else -> Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Avatar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(edit.avatar, edit.name.ifEmpty { "?" }, 84.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.edit_avatar),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VotifyColors.TextPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Text(stringResource(R.string.edit_avatar_change), color = VotifyColors.TextPrimary)
                            }
                            if (edit.avatar.isNotEmpty()) {
                                IconButton(onClick = { viewModel.editField(avatar = "") }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.edit_avatar_remove), tint = VotifyColors.TextMuted)
                                }
                            }
                        }
                    }
                }

                // Display name
                VotifyTextField(
                    value = edit.name,
                    onValueChange = { viewModel.editField(name = it) },
                    label = stringResource(R.string.edit_name),
                )

                // Username + live availability
                VotifyTextField(
                    value = edit.username,
                    onValueChange = { viewModel.editField(username = it) },
                    label = stringResource(R.string.edit_username),
                )
                UsernameStatusLine(edit = edit, viewModel = viewModel)

                // Bio + counter
                VotifyTextField(
                    value = edit.about,
                    onValueChange = { viewModel.editField(about = it) },
                    label = stringResource(R.string.edit_bio),
                    singleLine = false,
                )
                Text(
                    "${edit.about.length}/${SocialValidate.BIO_MAX}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )

                // Links
                Text(
                    stringResource(R.string.edit_links_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VotifyColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.edit_links_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                )
                VotifyTextField(
                    value = edit.telegram,
                    onValueChange = { viewModel.editField(telegram = it) },
                    label = "Telegram",
                )
                VotifyTextField(
                    value = edit.soundcloud,
                    onValueChange = { viewModel.editField(soundcloud = it) },
                    label = "SoundCloud",
                )
                VotifyTextField(
                    value = edit.vk,
                    onValueChange = { viewModel.editField(vk = it) },
                    label = "VK",
                )

                // Privacy
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.edit_private),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VotifyColors.TextPrimary,
                        )
                        Text(
                            stringResource(R.string.edit_private_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = VotifyColors.TextMuted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = edit.isPrivate,
                        onCheckedChange = { viewModel.editField(isPrivate = it) },
                    )
                }

                // Actions
                val canSave = !edit.saving &&
                    edit.usernameStatus != ProfileViewModel.UsernameStatusUi.Invalid &&
                    edit.usernameStatus != ProfileViewModel.UsernameStatusUi.Taken &&
                    edit.usernameStatus != ProfileViewModel.UsernameStatusUi.Checking
                PrimaryButton(
                    text = stringResource(R.string.edit_save),
                    loading = edit.saving,
                    enabled = canSave,
                    onClick = { viewModel.saveEdit(onBack) },
                )
                SecondaryButton(
                    text = stringResource(R.string.edit_cancel),
                    loading = false,
                    enabled = !edit.saving,
                    onClick = onBack,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UsernameStatusLine(edit: ProfileViewModel.EditState, viewModel: ProfileViewModel) {
    val (text, color) = when (edit.usernameStatus) {
        ProfileViewModel.UsernameStatusUi.Idle ->
            stringResource(R.string.edit_username_hint) to VotifyColors.TextMuted
        ProfileViewModel.UsernameStatusUi.Checking ->
            stringResource(R.string.edit_username_checking) to VotifyColors.TextMuted
        ProfileViewModel.UsernameStatusUi.Ok ->
            stringResource(R.string.edit_username_ok) to VotifyColors.Primary
        ProfileViewModel.UsernameStatusUi.Mine ->
            stringResource(R.string.edit_username_mine) to VotifyColors.TextMuted
        ProfileViewModel.UsernameStatusUi.Taken ->
            stringResource(R.string.edit_username_taken) to MaterialTheme.colorScheme.error
        ProfileViewModel.UsernameStatusUi.Invalid ->
            stringResource(viewModel.usernameErrorString(edit.usernameError)) to MaterialTheme.colorScheme.error
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
