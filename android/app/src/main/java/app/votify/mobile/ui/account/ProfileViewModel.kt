package app.votify.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.BIO_MAX_LENGTH
import app.votify.mobile.data.DISPLAY_NAME_MAX_LENGTH
import app.votify.mobile.data.Friendship
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.UserProfile
import app.votify.mobile.data.isValidHandle
import app.votify.mobile.data.normalizeHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Проверка юзернейма при редактировании профиля. */
enum class HandleState { Unknown, Checking, Free, Taken, Invalid, Yours }

/** Отношение с найденным человеком. */
enum class Relation { None, Self, Friend, Incoming, Outgoing }

/** Связь + профиль собеседника: список друзей и заявок показываем с именами. */
data class FriendEntry(val friendship: Friendship, val profile: UserProfile)

/** Результат поиска по юзернейму. */
data class SearchResult(val profile: UserProfile, val relation: Relation)

data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val profile: UserProfile? = null,
    val friends: List<FriendEntry> = emptyList(),
    /** Заявки, которые прислали мне. */
    val incoming: List<FriendEntry> = emptyList(),
    /** Заявки, которые отправил я. */
    val outgoing: List<FriendEntry> = emptyList(),
    val searching: Boolean = false,
    val search: SearchResult? = null,
    val handleState: HandleState = HandleState.Unknown,
    val handleMessage: String? = null,
)

/**
 * Профиль, юзернейм (занимается как в Telegram) и друзья.
 * Всё живёт в Firestore: profiles/{uid}, usernames/{handle}, friendships/{a_b}.
 */
class ProfileViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        refresh()
    }

    // ------------------------------------------------------------------ чтение

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val session = session()
            val fb = client()
            if (session == null || fb == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val (token, uid) = session
            runCatching {
                val profile = fb.loadProfile(token, uid) ?: UserProfile(uid = uid)
                val links = fb.friendships(token, uid)
                val profiles = fb.loadProfiles(token, links.map { it.other(uid) })
                val entries = links.map { link ->
                    FriendEntry(link, profiles[link.other(uid)] ?: UserProfile(uid = link.other(uid)))
                }
                Triple(profile, entries, uid)
            }.onSuccess { (profile, entries, myUid) ->
                _state.update {
                    it.copy(
                        loading = false,
                        profile = profile,
                        friends = entries.filter { e -> e.friendship.isAccepted },
                        incoming = entries.filter { e -> e.friendship.isPending && e.friendship.to == myUid },
                        outgoing = entries.filter { e -> e.friendship.isPending && e.friendship.from == myUid },
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: "error") }
            }
        }
    }

    // ------------------------------------------------------------------ профиль

    /** Проверяет юзернейм на лету: формат, свободен/занят. */
    fun checkHandle(input: String) {
        viewModelScope.launch {
            val handle = normalizeHandle(input)
            when {
                handle.isBlank() -> _state.update { it.copy(handleState = HandleState.Unknown, handleMessage = null) }
                !isValidHandle(handle) -> _state.update {
                    it.copy(handleState = HandleState.Invalid, handleMessage = "5–32 символа: латиница, цифры и _")
                }
                handle == normalizeHandle(it_value_profile_handle()) -> _state.update {
                    it.copy(handleState = HandleState.Yours, handleMessage = "Это ваш юзернейм")
                }
                else -> {
                    _state.update { it.copy(handleState = HandleState.Checking, handleMessage = null) }
                    val token = session()?.first ?: return@launch
                    val fb = client() ?: return@launch
                    val taken = runCatching { fb.isHandleTaken(token, handle) }.getOrNull()
                    _state.update {
                        when (taken) {
                            true -> it.copy(handleState = HandleState.Taken, handleMessage = "Юзернейм занят")
                            false -> it.copy(handleState = HandleState.Free, handleMessage = "Юзернейм свободен")
                            null -> it.copy(handleState = HandleState.Unknown, handleMessage = "Не удалось проверить")
                        }
                    }
                }
            }
        }
    }

    /**
     * Сохраняет профиль. Если юзернейм поменялся — сначала занимаем новый
     * (Firestore не даст занять чужой), и только потом освобождаем старый.
     */
    fun saveProfile(draft: UserProfile) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            val session = session()
            val fb = client()
            if (session == null || fb == null) {
                _state.update { it.copy(saving = false) }
                return@launch
            }
            val (token, uid) = session
            val oldHandle = normalizeHandle(_state.value.profile?.handle.orEmpty())
            val newHandle = normalizeHandle(draft.handle)
            runCatching {
                if (newHandle.isNotBlank() && newHandle != oldHandle) {
                    if (!isValidHandle(newHandle)) throw IOException("Юзернейм: 5–32 символа, латиница, цифры и _")
                    // Упадёт с ALREADY_EXISTS, если имя уже занято.
                    fb.claimHandle(token, uid, draft.handle)
                }
                val clean = draft.copy(
                    uid = uid,
                    handle = if (newHandle.isBlank()) "" else newHandle,
                    displayName = draft.displayName.trim().take(DISPLAY_NAME_MAX_LENGTH),
                    bio = draft.bio.trim().take(BIO_MAX_LENGTH),
                    avatarUrl = draft.avatarUrl.trim(),
                    telegram = draft.telegram.trim(),
                    soundcloud = draft.soundcloud.trim(),
                    vk = draft.vk.trim(),
                )
                fb.saveProfile(token, uid, clean)
                if (oldHandle.isNotBlank() && oldHandle != newHandle) fb.releaseHandle(token, oldHandle)
                clean
            }.onSuccess { saved ->
                _state.update { it.copy(saving = false, profile = saved, message = "Профиль сохранён") }
                refresh()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = e.message ?: "error") }
            }
        }
    }

    // ------------------------------------------------------------------ друзья

    /** Ищем человека по @юзернейму — точное совпадение, как в Telegram. */
    fun findByHandle(query: String) {
        viewModelScope.launch {
            val handle = normalizeHandle(query)
            if (!isValidHandle(handle)) {
                _state.update {
                    it.copy(search = null, searching = false, error = "Введите юзернейм: 5–32 символа, латиница, цифры и _")
                }
                return@launch
            }
            _state.update { it.copy(searching = true, error = null, message = null) }
            val session = session()
            val fb = client()
            if (session == null || fb == null) {
                _state.update { it.copy(searching = false) }
                return@launch
            }
            val (token, uid) = session
            runCatching { fb.profileByHandle(token, handle) }
                .onSuccess { profile ->
                    _state.update { s ->
                        s.copy(searching = false, search = profile?.let { p -> SearchResult(p, relationOf(p.uid, uid)) })
                    }
                    if (profile == null) _state.update { it.copy(error = "Никого не нашли") }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(searching = false, error = e.message ?: "error") }
                }
        }
    }

    fun clearSearch() = _state.update { it.copy(search = null, searching = false) }

    fun sendRequest(toUid: String) = act("Заявка отправлена") { token, uid, fb -> fb.sendFriendRequest(token, uid, toUid) }

    fun acceptRequest(id: String) = act("Заявка принята") { token, _, fb -> fb.acceptFriendRequest(token, id) }

    fun declineRequest(id: String) = act("Заявка отклонена") { token, _, fb -> fb.deleteFriendship(token, id) }

    fun removeFriend(id: String) = act("Удалено из друзей") { token, _, fb -> fb.deleteFriendship(token, id) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun logout() {
        viewModelScope.launch { settingsRepo.clearAccount() }
    }

    // ------------------------------------------------------------------ helpers

    private fun relationOf(otherUid: String, myUid: String): Relation = when {
        otherUid == myUid -> Relation.Self
        _state.value.friends.any { it.profile.uid == otherUid } -> Relation.Friend
        _state.value.outgoing.any { it.profile.uid == otherUid } -> Relation.Outgoing
        _state.value.incoming.any { it.profile.uid == otherUid } -> Relation.Incoming
        else -> Relation.None
    }

    private fun act(message: String, block: suspend (token: String, uid: String, fb: FirebaseRest) -> Unit) {
        viewModelScope.launch {
            val session = session() ?: return@launch
            val fb = client() ?: return@launch
            val (token, uid) = session
            runCatching { block(token, uid, fb) }
                .onSuccess {
                    _state.update { s -> s.copy(message = message, error = null) }
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { s -> s.copy(error = e.message ?: "error") }
                }
        }
    }

    /** Актуальный idToken и uid. Firebase-токены живут час — освежаем перед делом. */
    private suspend fun session(): Pair<String, String>? {
        val account = settingsRepo.account.first() ?: return null
        if (account.uid.isBlank()) return null
        var token = account.token
        if (account.refreshToken.isNotBlank()) {
            val cfg = FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig) ?: return null
            token = FirebaseRest(cfg).refreshIdToken(account.refreshToken) ?: token
        }
        return token to account.uid
    }

    private suspend fun client(): FirebaseRest? =
        FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig)?.let { FirebaseRest(it) }

    private fun it_value_profile_handle(): String = _state.value.profile?.handle.orEmpty()
}
