package app.votify.mobile.ui.account

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.FavTrackInfo
import app.votify.mobile.data.ProfileInfo
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** «Друзья» — поиск по юзернейму + список друзей (как модалка на ПК, один экран на телефоне). */
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenUser: (uid: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var removeTarget by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ProfileInfo?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringRes(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Column {
                Text(
                    stringRes(R.string.friends_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!state.loading) {
                    Text(
                        stringRes(R.string.friends_count, state.friends.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = VotifyColors.TextMuted,
                    )
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary)
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // 1. Search
                VotifyCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column {
                        Text(
                            stringRes(R.string.friends_search),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VotifyColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        VotifyTextField(
                            state.query,
                            viewModel::onQueryChange,
                            stringRes(R.string.friends_search_ph),
                        )
                        if (state.searching) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VotifyColors.Primary)
                            }
                        } else if (state.query.isNotBlank()) {
                            if (state.results.isEmpty()) {
                                Text(
                                    stringRes(R.string.friends_search_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VotifyColors.TextMuted,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            } else {
                                state.results.forEach { r ->
                                    FriendRow(
                                        profile = r,
                                        trailing = {
                                            if (state.addingUid == r.uid) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = VotifyColors.Primary)
                                            } else {
                                                OutlinedButton(
                                                    onClick = { viewModel.addFriend(r) },
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                ) {
                                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(stringRes(R.string.friends_add), fontSize = 13.sp)
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Friends list
                Spacer(Modifier.height(12.dp))
                VotifyCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column {
                        Text(
                            stringRes(R.string.friends_list),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VotifyColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        if (state.friends.isEmpty()) {
                            Text(
                                stringRes(R.string.friends_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            state.friends.forEach { f ->
                                FriendRow(
                                    profile = f,
                                    onClick = { onOpenUser(f.uid) },
                                    trailing = {
                                        if (state.removingUid == f.uid) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = VotifyColors.Error)
                                        } else {
                                            IconButton(onClick = { removeTarget = f }) {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    contentDescription = stringRes(R.string.friends_remove),
                                                    tint = VotifyColors.TextMuted,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                            Text(
                                stringRes(R.string.friends_remove_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = VotifyColors.TextMuted,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Remove confirmation
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringRes(R.string.friends_remove_title)) },
            text = { Text(stringRes(R.string.friends_remove_msg, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFriend(target)
                    removeTarget = null
                }) {
                    Text(stringRes(R.string.friends_remove), color = VotifyColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringRes(R.string.action_cancel)) }
            },
        )
    }
}

/** Avatar + name + @handle row. */
@Composable
private fun FriendRow(
    profile: ProfileInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(profile.avatar, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VotifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@" + profile.handle,
                style = MaterialTheme.typography.labelSmall,
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** Чужой профиль (открыт из списка друзей): hero + «Добавить/Убрать» + любимый трек + плейлисты. */
@Composable
fun UserScreen(
    viewModel: FriendsViewModel,
    uid: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayFav: (FavTrackInfo) -> Unit,
) {
    val state by viewModel.userState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringRes(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringRes(R.string.friend_profile),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary)
            }
        } else state.profile?.let { p ->
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box {
                    BannerBox(
                        banner = p.banner,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-48).dp),
                    ) {
                        AvatarBox(p.avatar, size = 96.dp, border = true)
                    }
                }
                Spacer(Modifier.height(52.dp))
                Text(
                    p.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VotifyColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@" + p.handle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.Primary,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                if (p.about.isNotBlank()) {
                    Text(
                        p.about,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VotifyColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                    )
                }

                // Add / remove
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    if (state.isFriend) {
                        OutlinedButton(
                            onClick = viewModel::removeCurrentFriend,
                            enabled = !state.removing,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = VotifyColors.Error),
                        ) {
                            if (state.removing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = VotifyColors.Error)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringRes(R.string.friends_remove))
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::addCurrentFriend,
                            enabled = !state.adding,
                        ) {
                            if (state.adding) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = VotifyColors.Primary)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(stringRes(R.string.friends_add_btn))
                        }
                    }
                }

                // Favorite track
                val fav = p.favTrack
                if (fav != null) {
                    Spacer(Modifier.height(16.dp))
                    VotifyCard(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(fav.cover, modifier = Modifier.size(56.dp), size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    fav.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VotifyColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    fav.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VotifyColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringRes(R.string.profile_fav_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VotifyColors.TextMuted,
                                )
                            }
                            IconButton(onClick = { onPlayFav(fav) }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = stringRes(R.string.profile_play), tint = VotifyColors.Primary)
                            }
                        }
                    }
                }

                // Playlists
                if (p.playlists.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    VotifyCard(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Column {
                            Text(
                                stringRes(R.string.profile_playlists),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = VotifyColors.TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            p.playlists.forEach { pl ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Artwork(pl.cover, modifier = Modifier.size(40.dp), size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            pl.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VotifyColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            stringRes(R.string.profile_tracks_count, pl.count),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VotifyColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: stringRes(R.string.profile_load_error),
                    color = VotifyColors.TextSecondary,
                )
            }
        }
    }
}

// local alias (composable: stringResource требует composable-контекста)
@androidx.compose.runtime.Composable
private fun stringRes(id: Int, vararg args: Any?): String =
    androidx.compose.ui.res.stringResource(id, *args)
