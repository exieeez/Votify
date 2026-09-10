package app.votify.mobile.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.FollowState
import app.votify.mobile.data.PublicProfile
import app.votify.mobile.data.SocialLinks
import app.votify.mobile.data.SocialUser
import app.votify.mobile.data.SocialValidate
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors

/** Outlined pill button — the secondary sibling of [PrimaryButton]. */
@Composable
internal fun SecondaryButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        color = VotifyColors.SurfaceContainerHigh,
        contentColor = VotifyColors.TextPrimary,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(
                    color = VotifyColors.TextPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Avatar circle: Firestore data-URL photo or an initial-letter fallback, plus the optional frame ring. */
@Composable
fun ProfileAvatar(
    avatar: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    frame: String = "",
) {
    val bitmap = remember(avatar) { decodeAvatarDataUrl(avatar) }
    val brush = remember(frame) { frameBrush(frame) }
    // Ring scales with avatar size, 2..4 dp.
    val ring = (size.value / 22f).dp.coerceIn(2.dp, 4.dp)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (brush != null) ring else 0.dp)
                .clip(CircleShape)
                .background(VotifyColors.PrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VotifyColors.OnPrimaryContainer,
                )
            }
        }
        if (brush != null) {
            Box(Modifier.fillMaxSize().border(ring, brush, CircleShape))
        }
    }
}

@Composable
private fun CoverArt(cover: String, modifier: Modifier = Modifier) {
    if (cover.isBlank()) {
        Box(
            modifier.background(VotifyColors.SurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = VotifyColors.TextMuted)
        }
    } else {
        coil.compose.AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/**
 * Own profile ([targetId] null) or a foreign one (uid or @username):
 * header, stats, friend search, requests, friends and the playlist showcase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    targetId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(targetId) { viewModel.load(targetId) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is ProfileViewModel.ProfileEvent.Message ->
                    onMessage(context.getString(e.msg.id, *e.msg.args.toTypedArray()))
            }
        }
    }
    // The editor lives on its own back-stack entry with its own ViewModel instance —
    // quietly re-read the profile when coming back so saves show up immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumedOnce by remember(targetId) { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, targetId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) viewModel.refreshQuiet() else resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val profile = state.profile
    val locked = !state.isOwn && profile?.isPrivate == true && state.followState != FollowState.FOLLOWING

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
                stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!state.loading && !state.noBackend && !state.notFound) {
                if (profile?.username?.isNotEmpty() == true) {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString("@" + profile.username))
                        onMessage(context.getString(R.string.profile_username_copied))
                    }) {
                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.profile_copy_username), tint = VotifyColors.TextSecondary)
                    }
                }
                if (state.isOwn) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.profile_settings), tint = VotifyColors.TextSecondary)
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Filled.Logout, stringResource(R.string.profile_logout), tint = VotifyColors.TextSecondary)
                    }
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.TextPrimary)
            }
            state.noBackend -> CenteredNotice(
                text = stringResource(R.string.profile_need_firebase),
                action = stringResource(R.string.profile_logout),
                onAction = { viewModel.logout() },
            )
            state.notFound -> CenteredNotice(
                text = stringResource(R.string.profile_user_not_found),
                action = stringResource(R.string.nav_back),
                onAction = onBack,
            )
            profile == null && state.isOwn -> CenteredNotice(
                text = stringResource(R.string.profile_no_profile_sub),
                title = stringResource(R.string.profile_no_profile_title),
                action = stringResource(R.string.profile_create),
                onAction = onEdit,
            )
            profile != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    ProfileHeader(
                        profile = profile,
                        isOwn = state.isOwn,
                        followState = state.followState,
                        actionBusy = state.actionBusy,
                        onEdit = onEdit,
                        onFollow = viewModel::follow,
                        onUnfollow = viewModel::unfollow,
                        onCancelRequest = viewModel::cancelOutgoing,
                    )
                }
                if (state.isOwn) {
                    item(key = "search") {
                        FindFriendsCard(
                            query = state.searchQuery,
                            searching = state.searching,
                            results = state.searchResults,
                            searchDone = state.searchDone,
                            onQuery = viewModel::onSearchQuery,
                            onOpenUser = onOpenUser,
                        )
                    }
                    if (state.incoming.isNotEmpty() || state.requestsLoading) {
                        item(key = "incoming") {
                            RequestsCard(
                                title = stringResource(R.string.profile_requests_in),
                                users = state.incoming,
                                loading = state.requestsLoading && state.incoming.isEmpty(),
                                onOpenUser = onOpenUser,
                                onAccept = viewModel::acceptRequest,
                                onDecline = viewModel::declineRequest,
                            )
                        }
                    }
                    if (state.outgoing.isNotEmpty()) {
                        item(key = "outgoing") {
                            OutgoingCard(
                                users = state.outgoing,
                                onOpenUser = onOpenUser,
                                onCancel = viewModel::cancelRequest,
                            )
                        }
                    }
                    if (state.friends.isNotEmpty()) {
                        item(key = "friends") {
                            FriendsRow(users = state.friends, onOpenUser = onOpenUser)
                        }
                    }
                    item(key = "showcase-head") {
                        ShowcaseHead(
                            dirty = state.showcaseDirty,
                            publishing = state.publishing,
                            onPublish = viewModel::publishShowcase,
                        )
                    }
                    if (state.myPlaylists.isEmpty()) {
                        item(key = "showcase-empty") {
                            Text(
                                stringResource(R.string.profile_showcase_empty_own),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    } else {
                        items(state.myPlaylists.chunked(2), key = { it.first().id }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { p ->
                                    OwnPlaylistCard(
                                        name = p.name,
                                        count = p.trackCount,
                                        cover = p.cover.orEmpty(),
                                        onClick = { onOpenPlaylist(p.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    if (locked) {
                        item(key = "locked") {
                            LockedCard()
                        }
                    } else {
                        item(key = "showcase-title") {
                            Text(
                                stringResource(R.string.profile_showcase),
                                style = MaterialTheme.typography.titleMedium,
                                color = VotifyColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                        if (profile.showcase.isEmpty()) {
                            item(key = "showcase-empty") {
                                Text(
                                    stringResource(R.string.profile_showcase_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VotifyColors.TextMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        } else {
                            items(profile.showcase.chunked(2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { item ->
                                        ForeignShowcaseCard(
                                            name = item.name,
                                            count = item.count,
                                            cover = item.cover,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    state.sheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = VotifyColors.SurfaceContainerLow,
            contentColor = VotifyColors.TextPrimary,
        ) {
            Text(
                stringResource(sheet.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                sheet.loading -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = VotifyColors.TextPrimary)
                }
                sheet.users.isEmpty() -> Text(
                    stringResource(R.string.profile_empty_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    items(sheet.users, key = { it.uid }) { user ->
                        SheetUserRow(
                            user = user,
                            trailing = when {
                                !state.isOwn -> null
                                sheet.kind == ProfileViewModel.SheetKind.FOLLOWERS ->
                                    stringResource(R.string.profile_remove) to { viewModel.removeFollower(user.uid) }
                                else ->
                                    stringResource(R.string.profile_unfollow) to { viewModel.unfollowUid(user.uid) }
                            },
                            onOpen = {
                                viewModel.closeSheet()
                                onOpenUser(user.uid)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun CenteredNotice(text: String, action: String, onAction: () -> Unit, title: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = VotifyColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = action, loading = false, enabled = true, onClick = onAction)
    }
}

@Composable
private fun ProfileHeader(
    profile: PublicProfile,
    isOwn: Boolean,
    followState: FollowState,
    actionBusy: Boolean,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onCancelRequest: () -> Unit,
) {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Own avatar is tappable (opens the editor) and carries an edit badge.
            if (isOwn) {
                Box {
                    Box(
                        Modifier.clip(CircleShape).clickable(onClick = onEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileAvatar(profile.avatar, profile.displayName.ifEmpty { "?" }, 84.dp, frame = profile.frame)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(VotifyColors.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            stringResource(R.string.edit_avatar_change),
                            tint = VotifyColors.OnPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else {
                ProfileAvatar(profile.avatar, profile.displayName.ifEmpty { "?" }, 84.dp, frame = profile.frame)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                profile.displayName.ifEmpty { "?" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VotifyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (profile.username.isNotEmpty()) {
                Text(
                    "@" + profile.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.TextSecondary,
                )
            }
            if (profile.about.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (!profile.links.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                LinksRow(profile.links)
            }
            Spacer(Modifier.height(16.dp))
            if (isOwn) {
                PrimaryButton(
                    text = stringResource(R.string.profile_edit),
                    loading = false,
                    enabled = true,
                    onClick = onEdit,
                )
            } else {
                when (followState) {
                    FollowState.NONE -> PrimaryButton(
                        text = stringResource(R.string.profile_follow),
                        loading = actionBusy,
                        enabled = true,
                        onClick = onFollow,
                    )
                    FollowState.FOLLOWING -> SecondaryButton(
                        text = stringResource(R.string.profile_unfollow),
                        loading = actionBusy,
                        enabled = true,
                        onClick = onUnfollow,
                    )
                    FollowState.REQUESTED -> SecondaryButton(
                        text = stringResource(R.string.profile_cancel_request),
                        loading = actionBusy,
                        enabled = true,
                        onClick = onCancelRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinksRow(links: SocialLinks) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        links.entries().forEach { (kind, handle) ->
            val label = when (kind) {
                "telegram" -> "Telegram"
                "vk" -> "VK"
                else -> "SoundCloud"
            }
            AssistChip(
                onClick = {
                    val url = SocialValidate.linkUrl(kind, handle)
                    if (url.isNotEmpty()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
                label = { Text("@$handle", maxLines = 1) },
                leadingIcon = { Text(label, style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted) },
            )
        }
    }
}

@Composable
private fun FindFriendsCard(
    query: String,
    searching: Boolean,
    results: List<SocialUser>,
    searchDone: Boolean,
    onQuery: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                stringResource(R.string.profile_find_friends),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.profile_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = VotifyColors.TextMuted) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(
                            color = VotifyColors.TextMuted,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        query.isNotEmpty() -> Icon(
                            Icons.Filled.Close,
                            null,
                            tint = VotifyColors.TextMuted,
                            modifier = Modifier.clip(CircleShape).clickable { onQuery("") },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VotifyColors.TextPrimary,
                    unfocusedTextColor = VotifyColors.TextPrimary,
                    cursorColor = VotifyColors.TextPrimary,
                    focusedBorderColor = VotifyColors.TextPrimary,
                    unfocusedBorderColor = VotifyColors.BorderProminent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (searchDone && results.isEmpty() && query.trim().length >= 2) {
                Text(
                    stringResource(R.string.profile_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            results.forEach { user ->
                UserRow(user = user, onClick = { onOpenUser(user.username.ifEmpty { user.uid }) })
            }
        }
    }
}

@Composable
private fun UserRow(user: SocialUser, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(user.avatar, user.displayName.ifEmpty { "?" }, 44.dp, frame = user.frame)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName.ifEmpty { "?" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.username.isNotEmpty()) {
                Text(
                    "@" + user.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RequestsCard(
    title: String,
    users: List<SocialUser>,
    loading: Boolean,
    onOpenUser: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = VotifyColors.TextPrimary)
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VotifyColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
            users.forEach { user ->
                UserRow(user = user, onClick = { onOpenUser(user.uid) }) {
                    Row {
                        IconButton(onClick = { onAccept(user.uid) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Check, stringResource(R.string.profile_accept), tint = VotifyColors.TextPrimary)
                        }
                        IconButton(onClick = { onDecline(user.uid) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Close, stringResource(R.string.profile_decline), tint = VotifyColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutgoingCard(users: List<SocialUser>, onOpenUser: (String) -> Unit, onCancel: (String) -> Unit) {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                stringResource(R.string.profile_requests_out),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
            )
            users.forEach { user ->
                UserRow(user = user, onClick = { onOpenUser(user.uid) }) {
                    TextButton(onClick = { onCancel(user.uid) }) {
                        Text(stringResource(R.string.profile_cancel_request), color = VotifyColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsRow(users: List<SocialUser>, onOpenUser: (String) -> Unit) {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                stringResource(R.string.profile_friends),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                users.forEach { user ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenUser(user.uid) },
                    ) {
                        ProfileAvatar(user.avatar, user.displayName.ifEmpty { "?" }, 52.dp, frame = user.frame)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            user.displayName.ifEmpty { "?" },
                            style = MaterialTheme.typography.labelSmall,
                            color = VotifyColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseHead(dirty: Boolean, publishing: Boolean, onPublish: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.profile_showcase),
                style = MaterialTheme.typography.titleMedium,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            if (dirty && !publishing) {
                Text(
                    stringResource(R.string.profile_showcase_dirty),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                )
            }
        }
        if (dirty || publishing) {
            TextButton(onClick = onPublish, enabled = !publishing) {
                if (publishing) {
                    CircularProgressIndicator(
                        color = VotifyColors.TextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(Icons.Filled.Refresh, null, tint = VotifyColors.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profile_showcase_publish), color = VotifyColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun OwnPlaylistCard(name: String, count: Int, cover: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    VotifyCard(modifier, onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        Column {
            CoverArt(cover, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(pluralTracks(count), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
    }
}

@Composable
private fun ForeignShowcaseCard(name: String, count: Int, cover: String, modifier: Modifier = Modifier) {
    VotifyCard(modifier, contentPadding = PaddingValues(10.dp)) {
        Column {
            CoverArt(cover, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(8.dp))
            Text(
                name.ifEmpty { "?" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(pluralTracks(count), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
    }
}

@Composable
private fun LockedCard() {
    VotifyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.profile_private),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
            )
            Text(
                stringResource(R.string.profile_private_sub),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SheetUserRow(user: SocialUser, trailing: Pair<String, () -> Unit>?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(user.avatar, user.displayName.ifEmpty { "?" }, 44.dp, frame = user.frame)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName.ifEmpty { "?" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.username.isNotEmpty()) {
                Text(
                    "@" + user.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            TextButton(onClick = trailing.second) {
                Text(trailing.first, color = VotifyColors.TextSecondary)
            }
        }
    }
}
