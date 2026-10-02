package app.votify.mobile.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.votify.mobile.R
import app.votify.mobile.data.FavTrackInfo
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage

/** Profile page — matched 1:1 with the PC Desktop version of Votify. */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onToast: (String) -> Unit,
    onPlayFav: (FavTrackInfo) -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onAvatarPicked)
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onBannerPicked)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            if (e is ProfileEvent.Message) onToast(e.text)
        }
    }

    val p = state.profile
    val guest = state.isGuest
    val profName = (p?.name ?: "").takeIf { it.isNotBlank() }
        ?: if (guest) stringResource(R.string.profile_guest_name) else state.fallbackName
    val profHandle = (p?.handle ?: "").takeIf { it.isNotBlank() }
        ?: if (guest) "guest" else state.fallbackHandle

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // --- Top App Bar (pc-profile-appbar) ---
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.weight(1f))

            // Action buttons row (pc-appbar-actions)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopAppbarActionBtn(
                    icon = Icons.Outlined.Group,
                    contentDescription = stringResource(R.string.friends_title),
                    onClick = onOpenFriends,
                )
                TopAppbarActionBtn(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = "Скопировать юзернейм",
                    onClick = {
                        clipboard.setText(AnnotatedString("@$profHandle"))
                        onToast("@$profHandle")
                    },
                )
                TopAppbarActionBtn(
                    icon = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    onClick = onOpenSettings,
                )
                if (!guest) {
                    TopAppbarActionBtn(
                        icon = Icons.Outlined.ExitToApp,
                        contentDescription = stringResource(R.string.profile_logout),
                        isDanger = true,
                        onClick = onLogout,
                    )
                } else {
                    TopAppbarActionBtn(
                        icon = Icons.Outlined.Person,
                        contentDescription = stringResource(R.string.profile_login_short),
                        onClick = onOpenLogin,
                    )
                }
            }
        }

        if (state.loading && !guest && p == null) {
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
                // --- 1. Hero User Profile Card (pc-profile-hero-card) ---
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        // Full Card Height Profile Banner with Bottom Darkening
                        BannerBox(
                            banner = if (state.editBanner.isNotBlank()) state.editBanner else p?.banner.orEmpty(),
                            modifier = Modifier.matchParentSize(),
                        )

                        if (!guest) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(32.dp)
                                    .clickable {
                                        bannerPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "Сменить баннер",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 36.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Avatar
                            Box(Modifier.size(84.dp)) {
                                AvatarBox(
                                    url = state.stagedAvatar ?: (p?.avatar ?: "").takeIf { it.isNotBlank() } ?: "",
                                    size = 84.dp,
                                    border = true,
                                )
                                if (!guest) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(28.dp)
                                            .clickable {
                                                avatarPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = "Сменить аватар",
                                                tint = Color.Black,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                profName,
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
                                "@$profHandle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFA3A3A3),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable {
                                        clipboard.setText(AnnotatedString("@$profHandle"))
                                        onToast("@$profHandle")
                                    }
                                    .padding(vertical = 2.dp),
                            )
                            Text(
                                stringResource(if (!guest) R.string.profile_role_user else R.string.profile_role_guest),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF737373),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                            (p?.about ?: "").takeIf { it.isNotBlank() && !state.editing }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA3A3A3),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }

                            // Button «Редактировать» (pc-btn-edit-profile)
                            if (!guest) {
                                Button(
                                    onClick = {
                                        if (state.editing) viewModel.cancelEdit() else viewModel.startEdit()
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.editing) Color(0xFF2A2A2A) else Color.White,
                                        contentColor = if (state.editing) Color.White else Color.Black,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(38.dp),
                                ) {
                                    Text(
                                        if (state.editing) stringResource(R.string.profile_cancel) else stringResource(R.string.profile_edit),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onOpenLogin,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(38.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.profile_login_btn),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                }
                            }

                            // --- Edit Form (in-place when editing) ---
                            if (state.editing) {
                                Spacer(Modifier.height(18.dp))
                                ProfileEditFormInline(
                                    state = state,
                                    viewModel = viewModel,
                                    onPickBanner = {
                                        bannerPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // --- 2. Favorite Track Card (pc-profile-section-card pc-fav-track-section) ---
                val fav = if (state.editing && p != null) {
                    if (state.favCleared) null else (state.stagedFav ?: p.favTrack)
                } else p?.favTrack

                if (fav != null) {
                    FavoriteTrackCard(
                        fav = fav,
                        onPlay = onPlayFav,
                    )
                }

                // --- 3. Playlists Card (pc-profile-section-card) ---
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
                                    stringResource(R.string.profile_playlists),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    state.publishMsg ?: stringResource(R.string.profile_publish_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF737373),
                                    fontSize = 12.sp,
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::publish,
                                enabled = !state.publishing && !guest,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0x0FFFFFFF),
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                if (state.publishing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = VotifyColors.Primary,
                                    )
                                } else {
                                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.publishing) stringResource(R.string.profile_publishing)
                                    else stringResource(R.string.profile_publish),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (playlists.isEmpty()) {
                            Text(
                                stringResource(R.string.profile_playlists_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF737373),
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
                                    Artwork(pl.cover ?: "", modifier = Modifier.size(44.dp), size = 44.dp)
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
                                            stringResource(R.string.profile_tracks_count, pl.trackCount),
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
        }
    }
}

/** In-line Edit Form matching the Desktop PC structure. */
@Composable
private fun ProfileEditFormInline(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    onPickBanner: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.profile_name),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            VotifyTextField(state.editName, viewModel::setName, stringResource(R.string.profile_name))
        }

        Column {
            Text(
                stringResource(R.string.profile_handle),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            VotifyTextField(state.editHandle, viewModel::setHandle, stringResource(R.string.profile_handle))
            state.handleError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = VotifyColors.Error, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Column {
            Text(
                stringResource(R.string.profile_about),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            VotifyTextField(
                state.editAbout,
                viewModel::setAbout,
                stringResource(R.string.profile_about),
                singleLine = false,
            )
        }

        // Banner presets & upload
        Column {
            Text(
                stringResource(R.string.profile_banner),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BannerChip(BANNER_GRADIENTS[0], selected = state.editBanner == "grad-1") { viewModel.setBanner(toggleGrad("grad-1", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[1], selected = state.editBanner == "grad-2") { viewModel.setBanner(toggleGrad("grad-2", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[2], selected = state.editBanner == "grad-3") { viewModel.setBanner(toggleGrad("grad-3", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[3], selected = state.editBanner == "grad-4") { viewModel.setBanner(toggleGrad("grad-4", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[4], selected = state.editBanner == "grad-5") { viewModel.setBanner(toggleGrad("grad-5", state.editBanner)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BannerChip(BANNER_GRADIENTS[5], selected = state.editBanner == "grad-6") { viewModel.setBanner(toggleGrad("grad-6", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[6], selected = state.editBanner == "grad-7") { viewModel.setBanner(toggleGrad("grad-7", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[7], selected = state.editBanner == "grad-8") { viewModel.setBanner(toggleGrad("grad-8", state.editBanner)) }
                BannerChip(BANNER_GRADIENTS[8], selected = state.editBanner == "grad-9") { viewModel.setBanner(toggleGrad("grad-9", state.editBanner)) }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1B2F))
                        .border(
                            if (state.editBanner.isEmpty()) 2.dp else 1.dp,
                            if (state.editBanner.isEmpty()) Color.White else Color(0x33FFFFFF),
                            CircleShape,
                        )
                        .clickable { viewModel.setBanner("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", color = Color(0xFFA3A3A3), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickBanner,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Загрузить фото", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.setBanner("") },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Сбросить", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            VotifyTextField(state.editBanner, viewModel::setBanner, stringResource(R.string.profile_banner_url))
        }

        // Favorite track controls
        Column {
            Text(
                stringResource(R.string.profile_fav_label),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA3A3A3),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::setFavoriteFromCurrent,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_fav_current), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = viewModel::clearFavorite,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.profile_fav_clear), fontSize = 12.sp)
                }
            }
            state.stagedFav?.let {
                Text(
                    "♪ ${it.title} — ${it.artist}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.favCleared) {
                Text(
                    stringResource(R.string.profile_fav_cleared),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF737373),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // Save / Cancel buttons
        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.profile_save),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

/** PC styled action button for the Top App Bar. */
@Composable
private fun TopAppbarActionBtn(
    icon: ImageVector,
    contentDescription: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDanger) Color(0x22EF4444) else Color(0x14FFFFFF),
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (isDanger) Color(0xFFEF4444) else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun toggleGrad(grad: String, current: String) = if (current == grad) "" else grad

@Composable
private fun BannerChip(stops: List<Color>, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(stops))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else Color(0x33FFFFFF),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
fun rememberFavTrackBrush(coverUrl: String?): Brush {
    val context = LocalContext.current
    var dominantColor by remember(coverUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(coverUrl) {
        val cleanUrl = coverUrl?.trim().orEmpty()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("data:image/svg")) {
            dominantColor = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val bitmap: Bitmap? = if (cleanUrl.startsWith("data:image/") && cleanUrl.contains("base64,")) {
                    val b64 = cleanUrl.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(cleanUrl)
                        .size(64)
                        .allowHardware(false)
                        .build()
                    loader.execute(request).drawable?.toBitmap()
                }

                if (bitmap != null) {
                    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                    val domSwatch = palette.dominantSwatch
                        ?: palette.vibrantSwatch
                        ?: palette.mutedSwatch
                    val rgb = domSwatch?.rgb
                    if (rgb != null) {
                        dominantColor = Color(rgb)
                    } else {
                        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, false)
                        var rAcc = 0L
                        var gAcc = 0L
                        var bAcc = 0L
                        var count = 0
                        for (x in 0 until scaled.width) {
                            for (y in 0 until scaled.height) {
                                val pixel = scaled.getPixel(x, y)
                                val pr = android.graphics.Color.red(pixel)
                                val pg = android.graphics.Color.green(pixel)
                                val pb = android.graphics.Color.blue(pixel)
                                val brightness = (pr * 299 + pg * 587 + pb * 114) / 1000
                                if (brightness in 19..234) {
                                    rAcc += pr
                                    gAcc += pg
                                    bAcc += pb
                                    count++
                                }
                            }
                        }
                        if (count > 0) {
                            dominantColor = Color(
                                (rAcc / count).toInt(),
                                (gAcc / count).toInt(),
                                (bAcc / count).toInt(),
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore fallback to null
            }
        }
    }

    return remember(dominantColor) {
        val dom = dominantColor
        if (dom != null) {
            val r = dom.red
            val g = dom.green
            val b = dom.blue
            val r1 = r * 0.55f
            val g1 = g * 0.55f
            val b1 = b * 0.55f
            val r2 = r * 0.22f
            val g2 = g * 0.22f
            val b2 = b * 0.22f

            Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color(red = r1, green = g1, blue = b1, alpha = 0.72f),
                    0.75f to Color(red = r2, green = g2, blue = b2, alpha = 0.95f),
                    1.0f to Color(0xFF121216),
                ),
            )
        } else {
            Brush.linearGradient(
                listOf(Color(0xFF181818), Color(0xFF181818)),
            )
        }
    }
}

/**
 * 1:1 match with PC version's `.pc-fav-track-row`:
 * - Dynamic cover background tint (Palette/sampling -> linear-gradient(135deg, rgba(r1, g1, b1, 0.72) 0%, rgba(r2, g2, b2, 0.95) 75%, #121216 100%))
 * - 72dp cover with 16dp rounded corners and translucent play icon overlay
 * - Full title "<Artist> - <Title>", subtitle "<Artist>", label "ЛЮБИМЫЙ ТРЕК"
 */
@Composable
fun FavoriteTrackCard(
    fav: FavTrackInfo,
    onPlay: (FavTrackInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = rememberFavTrackBrush(fav.cover)
    val rawArtist = if (fav.artist.isNotBlank() && fav.artist != "Unknown" && fav.artist != "Неизвестный исполнитель") fav.artist else ""
    val hasArtistInTitle = rawArtist.isNotBlank() && fav.title.contains(rawArtist, ignoreCase = true)
    val displayTitle = if (rawArtist.isNotBlank() && !hasArtistInTitle) "$rawArtist - ${fav.title}" else fav.title
    val displayArtist = if (rawArtist.isNotBlank()) "<$rawArtist>" else "<unknown>"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable(onClick = { onPlay(fav) })
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Artwork(
                    url = fav.cover,
                    size = 72.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.40f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(R.string.profile_play),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8DA0B6),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_fav_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5C728C),
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

@Composable
fun BannerBox(banner: String, modifier: Modifier = Modifier) {
    val cleanBanner = banner.trim()
    val base64Bitmap = remember(cleanBanner) {
        if (cleanBanner.startsWith("data:image/") && cleanBanner.contains("base64,")) {
            runCatching {
                val b64 = cleanBanner.substringAfter("base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    Box(modifier) {
        val preset = cleanBanner.removePrefix("grad-").toIntOrNull()
        when {
            base64Bitmap != null -> {
                Image(
                    bitmap = base64Bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            cleanBanner.startsWith("http://") || cleanBanner.startsWith("https://") -> {
                AsyncImage(
                    model = cleanBanner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            preset != null && preset in 1..9 -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(BANNER_GRADIENTS[preset - 1])),
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF1E1E24), Color(0xFF2A2B36)))),
            )
        }

        // Overlay matching PC .pc-profile-banner-overlay:
        // linear-gradient(to bottom, rgba(0, 0, 0, 0.1) 0%, rgba(24, 24, 24, 0.55) 35%, rgba(24, 24, 24, 0.92) 80%, #181818 100%)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.10f),
                            0.35f to Color(0xFF181818).copy(alpha = 0.55f),
                            0.80f to Color(0xFF181818).copy(alpha = 0.92f),
                            1.0f to Color(0xFF181818),
                        ),
                    ),
                ),
        )
    }
}

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

@Composable
fun AvatarBox(url: String, size: androidx.compose.ui.unit.Dp, border: Boolean = false) {
    val shape = CircleShape
    val cleanUrl = url.trim()

    val base64Bitmap = remember(cleanUrl) {
        if (cleanUrl.startsWith("data:image/") && cleanUrl.contains("base64,")) {
            runCatching {
                val b64 = cleanUrl.substringAfter("base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    Surface(
        shape = shape,
        color = Color(0xFF242730),
        border = if (border) androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF181818)) else null,
        modifier = Modifier.size(size),
        content = {
            Box(Modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) {
                when {
                    base64Bitmap != null -> {
                        Image(
                            bitmap = base64Bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(shape),
                        )
                    }
                    cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> {
                        SubcomposeAsyncImage(
                            model = cleanUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(shape),
                            error = {
                                Icon(
                                    Icons.Outlined.Person,
                                    null,
                                    tint = Color(0xFF7D8494),
                                    modifier = Modifier.size(size * 0.55f),
                                )
                            },
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Outlined.Person,
                            null,
                            tint = Color(0xFF7D8494),
                            modifier = Modifier.size(size * 0.55f),
                        )
                    }
                }
            }
        },
    )
}
