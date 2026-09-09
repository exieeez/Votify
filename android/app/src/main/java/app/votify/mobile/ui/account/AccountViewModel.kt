package app.votify.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.Account
import app.votify.mobile.data.AuthResponse
import app.votify.mobile.data.CloudSync
import app.votify.mobile.data.CloudSyncAuto
import app.votify.mobile.data.FirebaseConfig
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.toJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which form is on screen. The forgot-password flow differs per backend. */
enum class AccountMode { Login, Register, ForgotEmail, ForgotCode }

/** Available account backend right now. */
enum class AccountBackend { Firebase, Server, None }

data class AccountUiState(
    val mode: AccountMode = AccountMode.Login,
    val backend: AccountBackend = AccountBackend.None,
    val loading: Boolean = false,
    val error: String? = null,
    /** Reset code returned inline by the local server when SMTP is not configured. */
    val resetCode: String? = null,
    /** Firebase emails a reset link — show a confirmation instead of the code form. */
    val resetSent: Boolean = false,
    /** OAuth Web Client ID for Google Sign-In (default ships in the build). */
    val googleClientId: String = app.votify.mobile.data.DEFAULT_GOOGLE_CLIENT_ID,
)

sealed interface AccountEvent {
    data class LoggedIn(val displayName: String) : AccountEvent
    data object LoggedOut : AccountEvent
    /** Library + settings were restored from the account cloud after signing in. */
    data object DataRestored : AccountEvent
    /** Local library + settings were pushed to the account cloud after signing in. */
    data object DataSaved : AccountEvent
}

/**
 * Accounts: **Firebase** (the same project the PC app used — works standalone once the web
 * config is set in the Workshop) or the **local server** (server mode). Firebase has priority
 * when configured.
 */
class AccountViewModel(
    private val api: VotifyApi,
    private val settingsRepo: SettingsRepository,
    private val music: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state

    /** The signed-in account (Firebase or local server), or null while logged out. */
    val account = settingsRepo.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Sign out and drop the stored session. */
    fun logout() {
        viewModelScope.launch {
            settingsRepo.clearAccount()
            api.authToken = null
            _events.tryEmit(AccountEvent.LoggedOut)
        }
    }

    private val _events = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<AccountEvent> = _events

    private var firebase: FirebaseRest? = null

    init {
        // Re-evaluate the backend whenever the Firebase config changes (e.g. the user
        // connects Firebase in the Workshop while this screen is already alive).
        viewModelScope.launch {
            settingsRepo.settings.collectLatest { s ->
                _state.update {
                    it.copy(
                        googleClientId = app.votify.mobile.data.parseCustomPrefs(s.customPrefs).googleClientId
                            .ifBlank { app.votify.mobile.data.DEFAULT_GOOGLE_CLIENT_ID },
                    )
                }
                val cfg = FirebaseRest.effectiveConfig(s.firebaseConfig)
                firebase = cfg?.let { FirebaseRest(it) }
                val newBackend = when {
                    cfg != null -> AccountBackend.Firebase
                    music.isServerMode -> AccountBackend.Server
                    else -> AccountBackend.None
                }
                _state.update { if (it.backend != newBackend) it.copy(backend = newBackend, error = null) else it }
            }
        }
    }

    /** Store the OAuth Web Client ID used for Google Sign-In. */
    fun setGoogleClientId(id: String) {
        viewModelScope.launch {
            val cur = app.votify.mobile.data.parseCustomPrefs(settingsRepo.settings.first().customPrefs)
            // An emptied field falls back to the built-in default.
            val effective = id.trim().ifBlank { app.votify.mobile.data.DEFAULT_GOOGLE_CLIENT_ID }
            settingsRepo.setCustomPrefs(cur.copy(googleClientId = effective).toJson())
            _state.update { it.copy(googleClientId = effective) }
        }
    }

    /** Google Sign-In (Credential Manager returned the ID token) → Firebase session. */
    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val client = firebase
            if (client == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            runCatching { client.signInWithGoogle(idToken) }
                .onSuccess { acct ->
                    _state.update { it.copy(loading = false) }
                    onFirebaseAccount(acct.email, acct.username, acct.idToken, acct.uid, acct.refreshToken)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
        }
    }

    fun setMode(mode: AccountMode) {
        _state.update { it.copy(mode = mode, error = null, resetCode = null, resetSent = false) }
    }

    fun login(email: String, password: String) {
        when (_state.value.backend) {
            AccountBackend.Firebase -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                val client = firebase
                if (client == null) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                runCatching { client.login(email.trim(), password) }
                    .onSuccess { acct ->
                        _state.update { it.copy(loading = false) }
                        onFirebaseAccount(acct.email, acct.username, acct.idToken, acct.uid, acct.refreshToken)
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update { it.copy(loading = false, error = e.message ?: "error") }
                    }
            }
            AccountBackend.Server -> serverCall { api.login(email.trim(), password) }
            AccountBackend.None -> Unit
        }
    }

    fun register(email: String, username: String, password: String) {
        when (_state.value.backend) {
            AccountBackend.Firebase -> fbRegister(email.trim(), username.trim(), password)
            AccountBackend.Server -> serverCall { api.register(email.trim(), username.trim(), password) }
            AccountBackend.None -> Unit
        }
    }

    /** Step 1 of password recovery. */
    fun sendResetCode(email: String) {
        when (_state.value.backend) {
            AccountBackend.Firebase -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                firebase?.runCatching { sendPasswordReset(email.trim()) }
                    ?.onSuccess { _state.update { it.copy(loading = false, resetSent = true) } }
                    ?.onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update { it.copy(loading = false, error = e.message ?: "error") }
                    }
            }
            AccountBackend.Server -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching { api.requestPasswordReset(email.trim()) }
                    .onSuccess { r -> _state.update { it.copy(loading = false, mode = AccountMode.ForgotCode, resetCode = r.code) } }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update { it.copy(loading = false, error = e.message ?: "error") }
                    }
            }
            AccountBackend.None -> Unit
        }
    }

    /** Step 2 (server only): exchange the inline code for a new password. */
    fun resetPassword(email: String, code: String, newPassword: String) = serverCall {
        api.resetPassword(email.trim(), code.trim(), newPassword)
    }

    private fun fbRegister(email: String, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val client = firebase ?: run {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            runCatching { client.register(email, username, password) }
                .onSuccess { acct ->
                    _state.update { it.copy(loading = false) }
                    onFirebaseAccount(acct.email, acct.username, acct.idToken, acct.uid, acct.refreshToken)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
        }
    }

    private fun serverCall(onOk: (AuthResponse) -> Unit = ::onAuthenticated, block: suspend () -> AuthResponse) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onSuccess { r ->
                    _state.update { it.copy(loading = false) }
                    onOk(r)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
        }
    }

    private fun onAuthenticated(r: AuthResponse) {
        api.authToken = r.token
        viewModelScope.launch {
            settingsRepo.setAccount(r.user.email, r.user.username, r.token)
        }
        _events.tryEmit(AccountEvent.LoggedIn(r.user.username.ifBlank { r.user.email }))
    }

    private fun onFirebaseAccount(email: String, username: String, idToken: String, uid: String, refreshToken: String) {
        viewModelScope.launch {
            settingsRepo.setAccount(email, username, idToken, uid, refreshToken)
        }
        _events.tryEmit(AccountEvent.LoggedIn(username.ifBlank { email }))
        // Auto cloud sync right after signing in:
        //  - fresh install / wiped app + cloud data exists  → restore everything;
        //  - local data exists + nothing in the cloud yet    → save it to the account.
        //  Both sides non-empty → leave the choice to the manual push/pull buttons.
        viewModelScope.launch {
            val cfg = FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig) ?: return@launch
            val client = FirebaseRest(cfg)
            val sync = CloudSync(app.votify.mobile.VotifyApp.instance.database, settingsRepo)
            val token = runCatching { client.refreshIdToken(refreshToken) }.getOrNull() ?: idToken
            runCatching {
                val cloud = client.pullUserSync(token, uid)
                val localEmpty = !sync.hasLocalData()
                when {
                    cloud != null && localEmpty -> {
                        val blob = sync.fromJson(cloud)
                        if (blob != null && (blob.favorites.isNotEmpty() || blob.playlists.isNotEmpty() || blob.customPrefs.isNotBlank())) {
                            // Mute the auto-backup watcher while the restore writes rows,
                            // so importing does not immediately push the same data back.
                            CloudSyncAuto.suppressFor(20000)
                            sync.importBlob(blob)
                            _events.tryEmit(AccountEvent.DataRestored)
                        }
                    }
                    cloud == null && !localEmpty -> {
                        client.pushUserSync(token, uid, sync.toJson(sync.exportBlob()))
                        _events.tryEmit(AccountEvent.DataSaved)
                    }
                }
            }
        }
    }
}
