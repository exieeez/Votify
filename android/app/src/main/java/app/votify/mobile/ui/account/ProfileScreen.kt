package app.votify.mobile.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.FavTrackInfo
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage

/** Profile page — the phone version of the PC «Профиль» overlay. */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onToast: (String) -> Unit,
    onPlayFav: (FavTrackInfo) -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onAvatarPicked)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            if (e is ProfileEvent.Message) onToast(e.text)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // Header: back + title + friends
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenFriends) {
                Icon(Icons.Outlined.Group, stringResource(R.string.friends_title), tint = VotifyColors.TextPrimary)
            }
        }

        // Герой профиля виден всегда (гость, вошедший, ошибка загрузки) — как на ПК.
        if (state.loading && !state.isGuest && state.profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary)
            }
        } else {
            // Профиль всегда виден (гость — «Гость»/@guest, как на ПК)
            val p = state.profile
            val guest = state.isGuest || p == null
            val profName = p?.name ?: stringResource(R.string.profile_guest_name)
            val profHandle = p?.handle ?: "guest"
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Hero: banner + avatar + name + handle + роль
                Box {
                    BannerBox(
                        banner = p?.banner.orEmpty(),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-48).dp)
                            .then(
                                if (!guest) Modifier.clickable {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else Modifier
                            ),
                    ) {
                        AvatarBox(
                            url = state.stagedAvatar ?: p?.avatar.orEmpty(),
                            size = 96.dp,
                            border = true,
                        )
                        // camera badge (только для вошедших)
                        if (!guest) {
                            Surface(
                                shape = CircleShape,
                                color = VotifyColors.SurfaceContainerHigh,
                                modifier = Modifier.align(Alignment.BottomEnd).size(30.dp),
                                content = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.PhotoCamera,
                                            null,
                                            tint = VotifyColors.TextSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(52.dp))
                Text(
                    profName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VotifyColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // @handle — тап копирует (как «Скопировать юзернейм» на ПК)
                Text(
                    "@" + profHandle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VotifyColors.Primary,
                    modifier = Modifier
                        .clickable {
                            clipboard.setText(AnnotatedString("@" + profHandle))
                            onToast("@" + profHandle)
                        }
                        .padding(vertical = 2.dp),
                )
                // Роль: как на ПК («Пользователь» / «Не авторизован»)
                Text(
                    stringResource(if (!guest) R.string.profile_role_user else R.string.profile_role_guest),
                    style = MaterialTheme.typography.labelSmall,
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (state.error != null && p == null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = VotifyColors.Error,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = viewModel::reload) {
                            Text(stringResource(R.string.profile_retry))
                        }
                    }
                }
                (p?.about ?: "").takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VotifyColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                    )
                }

                // Гость: «Войти / Зарегистрироваться»; вошедший: «Редактировать»
                when {
                    state.editing -> ProfileEditForm(state, viewModel)
                    guest && state.error == null -> OutlinedButton(
                        onClick = onOpenLogin,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Person, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_login_btn))
                    }
                    guest -> Box {
                        // гость + ошибка (например, Firebase не настроен): и вход, и повтор
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onOpenLogin,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.profile_login_btn), fontSize = 13.sp)
                            }
                        }
                    }
                    else -> OutlinedButton(
                        onClick = viewModel::startEdit,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_edit))
                    }
                }

                // Favorite track (Telegram style, as on PC)
                val fav = if (state.editing && p != null) {
                    if (state.favCleared) null else (state.stagedFav ?: p.favTrack)
                } else p?.favTrack
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
                                    stringResource(R.string.profile_fav_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VotifyColors.TextMuted,
                                )
                            }
                            IconButton(onClick = { onPlayFav(fav) }) {
                                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.profile_play), tint = VotifyColors.Primary)
                            }
                        }
                    }
                }

                // Playlists + «Опубликовать»
                Spacer(Modifier.height(16.dp))
                VotifyCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.profile_playlists),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VotifyColors.TextPrimary,
                                )
                                Text(
                                    state.publishMsg ?: stringResource(R.string.profile_publish_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VotifyColors.TextMuted,
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::publish,
                                enabled = !state.publishing && !guest,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                if (state.publishing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = VotifyColors.Primary,
                                    )
                                } else {
                                    Icon(Icons.Outlined.PlaylistAdd, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.publishing) stringResource(R.string.profile_publishing)
                                    else stringResource(R.string.profile_publish),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (playlists.isEmpty()) {
                            Text(
                                stringResource(R.string.profile_playlists_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            playlists.forEach { pl: PlaylistSummary ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Artwork(pl.cover ?: "", modifier = Modifier.size(40.dp), size = 40.dp)
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
                                            stringResource(R.string.profile_tracks_count, pl.trackCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VotifyColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Friends entry
                Spacer(Modifier.height(12.dp))
                VotifyCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = onOpenFriends,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Group, null, tint = VotifyColors.TextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.friends_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = VotifyColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            tint = VotifyColors.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Logout — только для вошедших
                if (!guest) {
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.ExitToApp, null, tint = VotifyColors.Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_logout), color = VotifyColors.Error)
                    }
                }
            }
        }
    }
}

/** The «Редактировать» form: name, handle, about, banner, avatar, favorite track. */
@Composable
private fun ProfileEditForm(state: ProfileUiState, viewModel: ProfileViewModel) {
    VotifyCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.profile_edit_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VotifyColors.TextPrimary,
            )
            VotifyTextField(state.editName, viewModel::setName, stringResource(R.string.profile_name))
            VotifyTextField(state.editHandle, viewModel::setHandle, stringResource(R.string.profile_handle))
            state.handleError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = VotifyColors.Error)
            }
            VotifyTextField(
                state.editAbout,
                viewModel::setAbout,
                stringResource(R.string.profile_about),
                singleLine = false,
            )
            VotifyTextField(state.editBanner, viewModel::setBanner, stringResource(R.string.profile_banner))

            // Favorite track controls
            Text(
                stringResource(R.string.profile_fav_label),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::setFavoriteFromCurrent,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.profile_fav_current), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = viewModel::clearFavorite,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    enabled = !state.saving,
                ) {
                    Text(stringResource(R.string.profile_fav_clear), fontSize = 13.sp)
                }
            }
            state.stagedFav?.let {
                Text(
                    "♪ ${it.title} — ${it.artist}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VotifyColors.Primary,
                )
            }
            if (state.favCleared) {
                Text(
                    stringResource(R.string.profile_fav_cleared),
                    style = MaterialTheme.typography.labelSmall,
                    color = VotifyColors.TextMuted,
                )
            }

            // Save / cancel
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFF22C55E),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profile_save), color = Color(0xFF22C55E), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = viewModel::cancelEdit) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        }
    }
}

/** Banner: image (data:/https:), a grad-N preset (same colors as the web) or a dark fallback. */
@Composable
fun BannerBox(banner: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        val preset = banner.removePrefix("grad-").toIntOrNull()
        when {
            banner.startsWith("http") || banner.startsWith("data:") -> AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            preset != null && preset in 1..9 -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(BANNER_GRADIENTS[preset - 1]),
                ),
            )
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF1B1B2F), Color(0xFF0F0F1A))),
                ),
            )
        }
        // subtle darkening at the bottom so the avatar stands out
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)),
                    ),
                ),
        )
    }
}

/** 135°-style gradients, the same stops the web uses for profile banners. */
private val BANNER_GRADIENTS: List<List<Color>> = listOf(
    listOf(Color(0xFF7928CA), Color(0xFFFF0080), Color(0xFF11101D)),
    listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF050B14)),
    listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)),
    listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF022C22)),
    listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFF1A0505)),
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFF030F26)),
    listOf(Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF0F172A)),
    listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFF1E050C)),
    listOf(Color(0xFF18181B), Color(0xFF09090B)),
)

/** Round avatar with a placeholder person when there is none. */
@Composable
fun AvatarBox(url: String, size: androidx.compose.ui.unit.Dp, border: Boolean = false) {
    val shape = CircleShape
    Surface(
        shape = shape,
        color = VotifyColors.SurfaceContainer,
        border = if (border) androidx.compose.foundation.BorderStroke(3.dp, VotifyColors.SurfaceContainerLowest) else null,
        modifier = Modifier
            .size(size)
            .then(if (border) Modifier else Modifier),
        content = {
            Box(Modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) {
                if (url.isBlank()) {
                    Icon(
                        Icons.Outlined.Person,
                        null,
                        tint = VotifyColors.TextMuted,
                        modifier = Modifier.size(size * 0.6f),
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(shape),
                    )
                }
            }
        },
    )
}

