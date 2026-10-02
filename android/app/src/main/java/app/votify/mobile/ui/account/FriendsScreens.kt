package app.votify.mobile.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/** «Друзья» — поиск по юзернейму + список друзей (стилизация под ПК Votify). */
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenUser: (uid: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<ProfileInfo?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringRes(R.string.nav_back), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    stringRes(R.string.friends_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                if (!state.loading) {
                    Text(
                        stringRes(R.string.friends_count, state.friends.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA3A3A3),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary)
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Search Friends Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringRes(R.string.friends_search),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
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
                                    color = Color(0xFF737373),
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
                                                Button(
                                                    onClick = { viewModel.addFriend(r) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF22C55E),
                                                        contentColor = Color.White,
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                ) {
                                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(stringRes(R.string.friends_add), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                // 2. Friends List Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringRes(R.string.friends_list),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (state.friends.isEmpty()) {
                            Text(
                                stringRes(R.string.friends_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF737373),
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
                                                    tint = Color(0xFF737373),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
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
            .clip(RoundedCornerShape(10.dp))
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(profile.avatar, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@" + profile.handle,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontSize = 12.sp,
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
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(uid) {
        viewModel.loadUser(uid)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringRes(R.string.nav_back), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringRes(R.string.friend_profile),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary)
            }
        } else state.profile?.let { p ->
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Hero Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        BannerBox(
                            banner = p.banner,
                            modifier = Modifier.matchParentSize(),
                        )

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 36.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Avatar
                            Box(Modifier.size(84.dp)) {
                                AvatarBox(p.avatar, size = 84.dp, border = true)
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "@" + p.handle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFA3A3A3),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable {
                                        clipboard.setText(AnnotatedString("@" + p.handle))
                                    }
                                    .padding(vertical = 2.dp),
                            )
                            Text(
                                stringRes(R.string.profile_role_user),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF737373),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                            if (p.about.isNotBlank()) {
                                Text(
                                    p.about,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA3A3A3),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }

                            // Add / remove friend action button
                            if (state.isFriend) {
                                OutlinedButton(
                                    onClick = viewModel::removeCurrentFriend,
                                    enabled = !state.removing,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0x1AEF4444),
                                        contentColor = Color(0xFFEF4444),
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(38.dp),
                                ) {
                                    if (state.removing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color(0xFFEF4444))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(stringRes(R.string.friends_remove), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = viewModel::addCurrentFriend,
                                    enabled = !state.adding,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF22C55E),
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(38.dp),
                                ) {
                                    if (state.adding) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(stringRes(R.string.friends_add_btn), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Favorite track
                val fav = p.favTrack
                if (fav != null) {
                    FavoriteTrackCard(
                        fav = fav,
                        onPlay = onPlayFav,
                    )
                }

                // 3. Playlists
                if (p.playlists.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF181818),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringRes(R.string.profile_playlists),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringRes(R.string.profile_tracks_count, p.playlists.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF737373),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            p.playlists.forEach { pl ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Artwork(pl.cover, modifier = Modifier.size(44.dp), size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            pl.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            stringRes(R.string.profile_tracks_count, pl.count),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF737373),
                                            fontSize = 12.sp,
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
                    color = Color(0xFFA3A3A3),
                )
            }
        }
    }
}

// local alias (composable: stringResource требует composable-контекста)
@Composable
private fun stringRes(id: Int, vararg args: Any?): String {
    val nonNull = args.filterNotNull().toTypedArray()
    return if (nonNull.isEmpty()) stringResource(id)
    else stringResource(id, *nonNull)
}

