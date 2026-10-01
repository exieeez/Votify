package app.votify.mobile.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.CloudSync
import app.votify.mobile.data.FavTrackInfo
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.ProfileInfo
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.local.VotifyDatabase
import app.votify.mobile.player.PlayerController
import app.votify.mobile.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val profile: ProfileInfo? = null,
    val publishing: Boolean = false,
    val publishMsg: String? = null,
    // edit form
    val editing: Boolean = false,
    val editName: String = "",
    val editHandle: String = "",
    val editAbout: String = "",
    val editBanner: String = "",
    /** Avatar picked from the gallery, not saved yet (data URL). */
    val stagedAvatar: String? = null,
    /** Favorite track picked for the profile, not saved yet. */
    val stagedFav: FavTrackInfo? = null,
    val favCleared: Boolean = false,
    val saving: Boolean = false,
    val savingAvatar: Boolean = false,
    val handleError: String? = null,
)

sealed interface ProfileEvent {
    data class Message(val text: String) : ProfileEvent
}

/**
 * Profile page (same content as the PC version, adapted to a phone):
 * banner, avatar, name, @handle, about, favorite track, playlists with «Опубликовать».
 * Reads/writes the same Firestore documents the web app uses.
 */
class ProfileViewModel(
    private val settingsRepo: SettingsRepository,
    private val database: VotifyDatabase,
    private val appContext: Context,
    private val player: PlayerController,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    /** Локальные плейлисты для карточки «Плейлисты» (как на ПК — свои плейлисты). */
    val playlists: StateFlow<List<app.votify.mobile.data.local.PlaylistSummary>> =
        database.playlists().observeSummaries()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<ProfileEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events

    init {
        viewModelScope.launch { load() }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val acct = settingsRepo.account.first()
        if (acct == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        runCatching {
            val client = client() ?: error(appContext.getString(R.string.profile_no_backend))
            val token = validToken(client, acct)
            client.getProfile(token, acct.uid)
        }.onSuccess { profile ->
            _state.update { it.copy(loading = false, profile = profile) }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.message ?: "error") }
        }
    }

    private suspend fun client(): FirebaseRest? =
        FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig)?.let { FirebaseRest(it) }

    private suspend fun validToken(client: FirebaseRest, acct: app.votify.mobile.data.Account): String =
        runCatching { client.refreshIdToken(acct.refreshToken) }.getOrNull() ?: acct.token

    // ------------------------------------------------------------------ edit form

    fun startEdit() {
        val p = _state.value.profile ?: return
        _state.update {
            it.copy(
                editing = true,
                editName = p.name,
                editHandle = p.handle,
                editAbout = p.about,
                editBanner = p.banner,
                stagedAvatar = null,
                stagedFav = null,
                favCleared = false,
                handleError = null,
            )
        }
    }

    fun cancelEdit() {
        _state.update { it.copy(editing = false, stagedAvatar = null, stagedFav = null, favCleared = false, handleError = null) }
    }

    fun setName(v: String) = _state.update { it.copy(editName = v) }
    fun setHandle(v: String) = _state.update { it.copy(editHandle = v) }
    fun setAbout(v: String) = _state.update { it.copy(editAbout = v) }
    fun setBanner(v: String) = _state.update { it.copy(editBanner = v) }

    /** The currently playing track becomes the profile's favorite (staged until «Сохранить»). */
    fun setFavoriteFromCurrent() {
        val current = player.state.value.current ?: return
        _state.update {
            it.copy(
                stagedFav = FavTrackInfo(current.id, current.title, current.artist, current.cover, current.duration),
                favCleared = false,
            )
        }
    }

    fun clearFavorite() = _state.update { it.copy(stagedFav = null, favCleared = true) }

    /** Pick an avatar from the gallery: compress to a JPEG data URL, save immediately (like the PC). */
    fun onAvatarPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(savingAvatar = true) }
            val dataUrl = with(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input) ?: return@runCatching null
                        val max = 384
                        val scale = minOf(1f, max / maxOf(bmp.width, bmp.height).toFloat())
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).toInt().coerceAtLeast(1),
                                (bmp.height * scale).toInt().coerceAtLeast(1),
                                true,
                            )
                        } else bmp
                        val out = java.io.ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
                        "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                }.getOrNull()
            }
            if (dataUrl == null) {
                _state.update { it.copy(savingAvatar = false) }
                _events.tryEmit(ProfileEvent.Message(appContext.getString(R.string.profile_avatar_failed)))
                return@launch
            }
            val acct = settingsRepo.account.first()
            val p = _state.value.profile
            runCatching {
                if (acct == null || p == null) error("no account")
                val client = client() ?: error("no config")
                val token = validToken(client, acct)
                client.saveProfile(
                    idToken = token,
                    uid = acct.uid,
                    displayName = p.name,
                    handle = p.handle,
                    photoUrl = dataUrl,
                    bio = p.about,
                    banner = p.banner,
                    favTrack = p.favTrack,
                )
                dataUrl
            }.onSuccess { saved ->
                _state.update { s -> s.copy(savingAvatar = false, stagedAvatar = saved) }
                _events.tryEmit(ProfileEvent.Message(appContext.getString(R.string.profile_avatar_saved)))
                load()
            }.onFailure {
                _state.update { it.copy(savingAvatar = false) }
                _events.tryEmit(ProfileEvent.Message(it.message ?: "error"))
            }
        }
    }

    fun save() {
        val s = _state.value
        val p = s.profile ?: return
        val handle = s.editHandle.trim().lowercase().removePrefix("@")
        if (handle.isNotEmpty() && !handle.matches(Regex("[a-z0-9_]{3,20}"))) {
            _state.update { it.copy(handleError = appContext.getString(R.string.profile_handle_invalid)) }
            return
        }
        _state.update { it.copy(saving = true, handleError = null) }
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            runCatching {
                val client = client() ?: error("no config")
                val token = validToken(client, acct)
                val fav = if (s.favCleared) null else (s.stagedFav ?: p.favTrack)
                val avatar = s.stagedAvatar ?: p.avatar
                client.saveProfile(
                    idToken = token,
                    uid = acct.uid,
                    displayName = s.editName,
                    handle = handle,
                    photoUrl = avatar,
                    bio = s.editAbout,
                    banner = s.editBanner.trim(),
                    favTrack = fav,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, editing = false) }
                _events.tryEmit(ProfileEvent.Message(appContext.getString(R.string.profile_saved)))
                load()
            }.onFailure { e ->
                _state.update { it.copy(saving = false) }
                _events.tryEmit(ProfileEvent.Message(e.message ?: "error"))
            }
        }
    }

    // ------------------------------------------------------------------ publish

    /** «Опубликовать»: push the local library to the account cloud (like the PC). */
    fun publish() {
        if (_state.value.publishing) return
        _state.update { it.copy(publishing = true, publishMsg = null) }
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            runCatching {
                val client = client() ?: error("no config")
                val token = validToken(client, acct)
                val blob = CloudSync(database, settingsRepo).exportBlob()
                client.publishLibrary(token, acct.uid, blob)
            }.onSuccess {
                _state.update { it.copy(publishing = false, publishMsg = appContext.getString(R.string.profile_published)) }
                _events.tryEmit(ProfileEvent.Message(appContext.getString(R.string.profile_publish_toast)))
                load()
            }.onFailure { e ->
                _state.update { it.copy(publishing = false, publishMsg = e.message) }
            }
        }
    }
}
