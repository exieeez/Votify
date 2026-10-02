package app.votify.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.Account
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.ProfileInfo
import app.votify.mobile.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val friends: List<ProfileInfo> = emptyList(),
    // search
    val query: String = "",
    val searching: Boolean = false,
    val results: List<ProfileInfo> = emptyList(),
    // add/remove in flight
    val addingUid: String? = null,
    val removingUid: String? = null,
)

data class UserUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val profile: ProfileInfo? = null,
    val isFriend: Boolean = false,
    val adding: Boolean = false,
    val removing: Boolean = false,
)

/**
 * Друзья: поиск по юзернейму (collections profiles + users + точный handle),
 * список друзей (users/{me}/friends), добавление и удаление, чужой профиль.
 * Те же документы Firestore, что использует веб-версия.
 */
class FriendsViewModel(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state

    private val _userState = MutableStateFlow(UserUiState())
    val userState: StateFlow<UserUiState> = _userState

    init {
        loadFriends()
    }

    // ------------------------------------------------------------------ friends list

    fun loadFriends() {
        viewModelScope.launch {
            val acct = settingsRepo.account.first()
            if (acct == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.getFriends(token, acct.uid)
            }.onSuccess { friends ->
                _state.update { it.copy(loading = false, friends = friends) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private var searchJob: Job? = null

    /** Search-as-you-type (debounced 500 ms inside the flow). */
    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            val acct = settingsRepo.account.first() ?: return@launch
            _state.update { it.copy(searching = true) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.searchUsers(token, q, acct.uid)
            }.onSuccess { results ->
                _state.update { it.copy(searching = false, results = results) }
            }.onFailure {
                _state.update { it.copy(searching = false, results = emptyList()) }
            }
        }
    }

    fun addFriend(target: ProfileInfo) {
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            _state.update { it.copy(addingUid = target.uid) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.addFriend(token, acct.uid, target.uid, target.name)
            }.onSuccess {
                _state.update { it.copy(addingUid = null) }
                loadFriends()
            }.onFailure {
                _state.update { it.copy(addingUid = null) }
            }
        }
    }

    fun removeFriend(target: ProfileInfo) {
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            _state.update { it.copy(removingUid = target.uid) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.removeFriend(token, acct.uid, target.uid)
            }.onSuccess {
                _state.update { it.copy(removingUid = null) }
                loadFriends()
            }.onFailure {
                _state.update { it.copy(removingUid = null) }
            }
        }
    }

    // ------------------------------------------------------------------ user profile

    fun loadUser(uid: String) {
        _userState.update { UserUiState() }
        viewModelScope.launch {
            val acct = settingsRepo.account.first()
            if (acct == null) {
                _userState.update { it.copy(loading = false) }
                return@launch
            }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                val profile = client.getProfile(token, uid)
                val isFriend = client.getFriends(token, acct.uid).any { it.uid == uid }
                profile to isFriend
            }.onSuccess { (profile, isFriend) ->
                _userState.update {
                    it.copy(
                        loading = false,
                        profile = profile,
                        isFriend = isFriend,
                        error = if (profile == null) "Профиль пользователя не найден" else null,
                    )
                }
            }.onFailure { e ->
                _userState.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun addCurrentFriend() {
        val p = _userState.value.profile ?: return
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            _userState.update { it.copy(adding = true) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.addFriend(token, acct.uid, p.uid, p.name)
            }.onSuccess {
                _userState.update { it.copy(adding = false, isFriend = true) }
                loadFriends()
            }.onFailure {
                _userState.update { it.copy(adding = false) }
            }
        }
    }

    fun removeCurrentFriend() {
        val p = _userState.value.profile ?: return
        viewModelScope.launch {
            val acct = settingsRepo.account.first() ?: return@launch
            _userState.update { it.copy(removing = true) }
            runCatching {
                val client = client() ?: error("no firebase config")
                val token = validToken(client, acct)
                client.removeFriend(token, acct.uid, p.uid)
            }.onSuccess {
                _userState.update { it.copy(removing = false, isFriend = false) }
                loadFriends()
            }.onFailure {
                _userState.update { it.copy(removing = false) }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun client(): FirebaseRest? =
        FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig)?.let { FirebaseRest(it) }

    private suspend fun validToken(client: FirebaseRest, acct: Account): String =
        runCatching { client.refreshIdToken(acct.refreshToken) }.getOrNull() ?: acct.token
}
