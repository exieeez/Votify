package app.votify.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.R
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.FollowState
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.OwnSocialData
import app.votify.mobile.data.PublicProfile
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.SocialException
import app.votify.mobile.data.SocialFailure
import app.votify.mobile.data.SocialLinks
import app.votify.mobile.data.SocialRepository
import app.votify.mobile.data.SocialUser
import app.votify.mobile.data.SocialValidate
import app.votify.mobile.data.ShowcaseItem
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.local.PlaylistSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Own + foreign profiles, follow graph, search, requests and the profile editor.
 * Each navigation entry owns an instance; the editor screen drives [startEdit].
 */
class ProfileViewModel(
    private val api: VotifyApi,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    /** Localized snackbar message (resolved by the caller, like LibraryViewModel). */
    data class Msg(val id: Int, val args: List<String> = emptyList())

    sealed interface ProfileEvent {
        data class Message(val msg: Msg) : ProfileEvent
    }

    enum class SheetKind { FOLLOWERS, FOLLOWING }

    data class SheetData(
        val title: Int,
        val users: List<SocialUser>,
        val loading: Boolean,
        val kind: SheetKind,
    )

    enum class UsernameStatusUi { Idle, Checking, Ok, Mine, Taken, Invalid }

    data class EditState(
        val loading: Boolean = true,
        val name: String = "",
        val username: String = "",
        val usernameClean: String = "",
        val usernameStatus: UsernameStatusUi = UsernameStatusUi.Idle,
        val usernameError: SocialValidate.UsernameError? = null,
        val originalUsername: String = "",
        val about: String = "",
        val telegram: String = "",
        val soundcloud: String = "",
        val vk: String = "",
        val isPrivate: Boolean = false,
        val avatar: String = "",
        val frame: String = "",
        val email: String = "",
        val saving: Boolean = false,
        val noBackend: Boolean = false,
    )

    data class UiState(
        val loading: Boolean = true,
        val notFound: Boolean = false,
        val noBackend: Boolean = false,
        val myUid: String = "",
        val isOwn: Boolean = true,
        val profile: PublicProfile? = null,
        val followState: FollowState = FollowState.NONE,
        val actionBusy: Boolean = false,
        val searchQuery: String = "",
        val searching: Boolean = false,
        val searchResults: List<SocialUser> = emptyList(),
        val searchDone: Boolean = false,
        val searchLatinHint: Boolean = false,
        val incoming: List<SocialUser> = emptyList(),
        val outgoing: List<SocialUser> = emptyList(),
        val requestsLoading: Boolean = false,
        val friends: List<SocialUser> = emptyList(),
        val sheet: SheetData? = null,
        val myPlaylists: List<PlaylistSummary> = emptyList(),
        val showcaseDirty: Boolean = false,
        val publishing: Boolean = false,
        val edit: EditState? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private var social: SocialRepository? = null
    private var targetId: String? = null
    private var searchJob: Job? = null
    private var usernameJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                val cfg = FirebaseRest.effectiveConfig(s.firebaseConfig)
                social = cfg?.let { SocialRepository(FirebaseRest(it), settings) }
            }
        }
        viewModelScope.launch {
            library.playlists.collect { lists ->
                _state.update { it.copy(myPlaylists = lists) }
                recomputeShowcaseDirty()
            }
        }
    }

    private suspend fun emit(msg: Msg) = _events.emit(ProfileEvent.Message(msg))

    /**
     * The config collector in init() may not have fired yet when the screen loads —
     * build the repository on demand instead of flashing a bogus "no backend" state.
     */
    private suspend fun repoOrThrow(): SocialRepository {
        social?.let { return it }
        val cfg = FirebaseRest.effectiveConfig(settings.settings.first().firebaseConfig)
            ?: throw SocialException(SocialFailure.NoBackend)
        return SocialRepository(FirebaseRest(cfg), settings).also { social = it }
    }

    private fun failureMessage(e: Throwable): Msg = when (e) {
        is SocialException -> when (val f = e.failure) {
            SocialFailure.NoAuth, SocialFailure.NoBackend -> Msg(R.string.profile_need_firebase)
            SocialFailure.UsernameTaken -> Msg(R.string.edit_username_taken)
            is SocialFailure.Message -> Msg(R.string.profile_error_with_text, listOf(f.text))
        }
        is app.votify.mobile.data.FirebaseRestException ->
            Msg(R.string.profile_error_with_text, listOf(e.message.orEmpty().ifEmpty { e.code }))
        else -> Msg(R.string.profile_error_generic)
    }

    // ------------------------------------------------------------ viewing

    /** Loads the own profile ([target] null/blank) or a foreign one (uid or @username). */
    fun load(target: String?) {
        targetId = target?.trim().orEmpty()
        viewModelScope.launch { loadInternal(targetId) }
    }

    fun refresh() {
        viewModelScope.launch { loadInternal(targetId) }
    }

    /** Reload without the fullscreen spinner (used when returning to the screen). */
    fun refreshQuiet() {
        if (_state.value.loading) return
        viewModelScope.launch { loadInternal(targetId, quiet = true) }
    }

    private suspend fun loadInternal(target: String?, quiet: Boolean = false) {
        val acc = settings.account.first()
        val repo = runCatching { repoOrThrow() }.getOrNull()
        if (repo == null || acc == null || !acc.isFirebase || acc.uid.isBlank()) {
            _state.update { it.copy(loading = false, noBackend = true) }
            return
        }
        _state.update {
            it.copy(
                loading = if (quiet) it.loading else true,
                notFound = false,
                noBackend = false,
                myUid = acc.uid,
            )
        }
        try {
            val clean = target.orEmpty()
            val profile: PublicProfile? = when {
                clean.isEmpty() -> repo.fetchPublicProfile(acc.uid)
                else -> repo.fetchPublicProfile(clean) ?: repo.fetchProfileByUsername(clean)
            }
            if (profile == null && clean.isNotEmpty()) {
                _state.update { it.copy(loading = false, notFound = true) }
                return
            }
            val own = profile == null || profile.uid == acc.uid
            val fs = if (own) FollowState.NONE else repo.getFollowState(profile!!.uid)
            _state.update { it.copy(loading = false, profile = profile, isOwn = own, followState = fs) }
            if (own) {
                loadRequests()
                loadFriends()
                recomputeShowcaseDirty()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.update { it.copy(loading = false) }
            emit(failureMessage(e))
        }
    }

    // ------------------------------------------------------------ search

    fun onSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q, searchDone = false) }
        searchJob?.cancel()
        val clean = SocialValidate.searchPrefix(q)
        // Typed 2+ chars but nothing searchable (e.g. Cyrillic): hint at latin-only.
        val latinHint = SocialValidate.normalizeUsername(q).length >= 2 && clean.length < 2
        if (clean.length < 2) {
            _state.update { it.copy(searching = false, searchResults = emptyList(), searchLatinHint = latinHint) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            _state.update { it.copy(searching = true, searchLatinHint = false) }
            try {
                val repo = repoOrThrow()
                val users = repo.searchUsers(clean)
                _state.update { it.copy(searching = false, searchResults = users, searchDone = true, searchLatinHint = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(searching = false, searchDone = true, searchLatinHint = false) }
            }
        }
    }

    // ------------------------------------------------------------ follow actions

    private fun busyOp(op: suspend () -> Unit) {
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true) }
            try {
                op()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit(failureMessage(e))
            } finally {
                _state.update { it.copy(actionBusy = false) }
            }
        }
    }

    fun follow() = busyOp {
        val repo = repoOrThrow()
        val target = _state.value.profile ?: return@busyOp
        val result = repo.follow(target)
        _state.update { it.copy(followState = result) }
        emit(Msg(if (result == FollowState.FOLLOWING) R.string.profile_followed else R.string.profile_request_sent))
        refreshViewed()
    }

    fun unfollow() = busyOp {
        val repo = repoOrThrow()
        val target = _state.value.profile ?: return@busyOp
        repo.unfollow(target.uid)
        emit(Msg(R.string.profile_unfollowed))
        refreshViewed()
    }

    fun cancelOutgoing() = busyOp {
        val repo = repoOrThrow()
        val target = _state.value.profile ?: return@busyOp
        repo.cancelRequest(target.uid)
        emit(Msg(R.string.profile_request_cancelled))
        refreshViewed()
    }

    fun acceptRequest(uid: String) = busyOp {
        val repo = repoOrThrow()
        repo.acceptRequest(uid)
        emit(Msg(R.string.profile_request_accepted))
        loadRequests()
        loadFriends()
        refreshViewed()
    }

    fun declineRequest(uid: String) = busyOp {
        val repo = repoOrThrow()
        repo.declineRequest(uid)
        emit(Msg(R.string.profile_request_declined))
        loadRequests()
    }

    fun cancelRequest(uid: String) = busyOp {
        val repo = repoOrThrow()
        repo.cancelRequest(uid)
        emit(Msg(R.string.profile_request_cancelled))
        loadRequests()
    }

    fun removeFollower(uid: String) = busyOp {
        val repo = repoOrThrow()
        repo.removeFollower(uid)
        emit(Msg(R.string.profile_follower_removed))
        loadFriends()
        refreshViewed()
        refreshSheet()
    }

    fun unfollowUid(uid: String) = busyOp {
        val repo = repoOrThrow()
        repo.unfollow(uid)
        emit(Msg(R.string.profile_unfollowed))
        loadFriends()
        refreshViewed()
        refreshSheet()
    }

    private suspend fun refreshViewed() {
        val repo = social ?: return
        val st = _state.value
        val current = st.profile
        val fresh = if (current == null) {
            repo.fetchPublicProfile(st.myUid)
        } else {
            repo.fetchPublicProfile(current.uid)
        }
        val fs = if (fresh == null || fresh.uid == st.myUid || st.isOwn) {
            FollowState.NONE
        } else {
            repo.getFollowState(fresh.uid)
        }
        _state.update { it.copy(profile = fresh ?: current, followState = fs) }
        recomputeShowcaseDirty()
    }

    // ------------------------------------------------------------ requests + friends

    private suspend fun myUidOrAccount(): String =
        _state.value.myUid.ifEmpty { settings.account.first()?.uid.orEmpty() }

    private suspend fun loadRequests() {
        val repo = social ?: return
        val me = myUidOrAccount()
        if (me.isEmpty()) return
        _state.update { it.copy(requestsLoading = true) }
        try {
            val inc = repo.fetchUsers(repo.listIncomingRequestUids(me))
            val out = repo.fetchUsers(repo.listOutgoingRequestUids(me))
            _state.update { it.copy(incoming = inc, outgoing = out) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Secondary section — a failure must not sink the whole screen.
        } finally {
            _state.update { it.copy(requestsLoading = false) }
        }
    }

    /** Mutual-follow preview for the own profile. */
    private suspend fun loadFriends() {
        val repo = social ?: return
        val me = myUidOrAccount()
        if (me.isEmpty() || !_state.value.isOwn) return
        try {
            val followers = repo.listFollowerUids(me).toSet()
            if (followers.isEmpty()) {
                _state.update { it.copy(friends = emptyList()) }
                return
            }
            val mutual = repo.listFollowingUids(me).toSet().intersect(followers).take(20).toList()
            _state.update { it.copy(friends = repo.fetchUsers(mutual)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    // ------------------------------------------------------------ follower sheets

    fun openFollowers() = openSheet(SheetKind.FOLLOWERS, R.string.profile_followers_title)

    fun openFollowing() = openSheet(SheetKind.FOLLOWING, R.string.profile_following_title)

    private fun openSheet(kind: SheetKind, title: Int) {
        _state.update { it.copy(sheet = SheetData(title, emptyList(), true, kind)) }
        viewModelScope.launch { refreshSheet() }
    }

    fun closeSheet() {
        _state.update { it.copy(sheet = null) }
    }

    private suspend fun refreshSheet() {
        val current = _state.value.sheet ?: return
        val repo = social ?: return
        val target = _state.value.profile ?: return
        try {
            val uids = when (current.kind) {
                SheetKind.FOLLOWERS -> repo.listFollowerUids(target.uid)
                SheetKind.FOLLOWING -> repo.listFollowingUids(target.uid)
            }
            _state.update { it.copy(sheet = current.copy(users = repo.fetchUsers(uids), loading = false)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.update { it.copy(sheet = current.copy(loading = false)) }
            emit(failureMessage(e))
        }
    }

    // ------------------------------------------------------------ showcase

    fun publishShowcase() {
        if (_state.value.publishing) return
        viewModelScope.launch {
            _state.update { it.copy(publishing = true) }
            try {
                val repo = repoOrThrow()
                repo.ensurePublicSnapshot()
                repo.syncShowcase(showcaseFromPlaylists(_state.value.myPlaylists))
                refreshViewed()
                emit(Msg(R.string.profile_showcase_published))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit(failureMessage(e))
            } finally {
                _state.update { it.copy(publishing = false) }
            }
        }
    }

    private fun showcaseFromPlaylists(lists: List<PlaylistSummary>): List<ShowcaseItem> =
        lists.take(SocialValidate.SHOWCASE_LIMIT).map { p ->
            ShowcaseItem(
                name = p.name.take(60),
                count = p.trackCount.coerceAtLeast(0),
                cover = p.cover?.takeIf { SocialValidate.isSafeHttpUrl(it) }?.take(2048).orEmpty(),
            )
        }

    private fun recomputeShowcaseDirty() {
        val st = _state.value
        val mine = showcaseFromPlaylists(st.myPlaylists)
        val pub = st.profile?.showcase ?: emptyList()
        _state.update { it.copy(showcaseDirty = mine != pub) }
    }

    // ------------------------------------------------------------ editor

    fun startEdit() {
        if (_state.value.edit != null) return
        _state.update { it.copy(edit = EditState()) }
        viewModelScope.launch {
            try {
                val repo = repoOrThrow()
                val acc = settings.account.first() ?: throw SocialException(SocialFailure.NoAuth)
                val data = repo.fetchOwnSocialData()
                val fallbackName = data.displayName
                    .ifEmpty { acc.username }
                    .ifEmpty { acc.email.substringBefore("@") }
                _state.update {
                    it.copy(
                        edit = EditState(
                            loading = false,
                            name = fallbackName,
                            username = data.username,
                            usernameClean = data.username,
                            usernameStatus = if (data.username.isEmpty()) {
                                UsernameStatusUi.Idle
                            } else {
                                UsernameStatusUi.Mine
                            },
                            originalUsername = data.username,
                            about = data.about,
                            telegram = data.links.telegram,
                            soundcloud = data.links.soundcloud,
                            vk = data.links.vk,
                            isPrivate = data.isPrivate,
                            avatar = data.avatar,
                            frame = data.frame,
                            email = data.email.ifEmpty { acc.email },
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val noBackend = e is SocialException &&
                    (e.failure == SocialFailure.NoBackend || e.failure == SocialFailure.NoAuth)
                _state.update { it.copy(edit = EditState(loading = false, noBackend = noBackend)) }
                emit(failureMessage(e))
            }
        }
    }

    fun editField(
        name: String? = null,
        username: String? = null,
        about: String? = null,
        telegram: String? = null,
        soundcloud: String? = null,
        vk: String? = null,
        isPrivate: Boolean? = null,
        avatar: String? = null,
        frame: String? = null,
    ) {
        _state.update { st ->
            val e = st.edit ?: return@update st
            var next = e
            name?.let { next = next.copy(name = SocialValidate.sanitizeDisplayName(it)) }
            username?.let { raw ->
                val clean = SocialValidate.normalizeUsername(raw)
                val check = SocialValidate.validateUsername(clean)
                val status = when {
                    clean.isEmpty() -> UsernameStatusUi.Idle
                    clean == next.originalUsername -> UsernameStatusUi.Mine
                    !check.ok -> UsernameStatusUi.Invalid
                    else -> UsernameStatusUi.Checking
                }
                next = next.copy(
                    username = raw,
                    usernameClean = clean,
                    usernameStatus = status,
                    usernameError = check.error,
                )
            }
            about?.let { next = next.copy(about = SocialValidate.sanitizeBio(it)) }
            telegram?.let { next = next.copy(telegram = it.take(SocialValidate.LINK_MAX)) }
            soundcloud?.let { next = next.copy(soundcloud = it.take(SocialValidate.LINK_MAX)) }
            vk?.let { next = next.copy(vk = it.take(SocialValidate.LINK_MAX)) }
            isPrivate?.let { next = next.copy(isPrivate = it) }
            avatar?.let { next = next.copy(avatar = it) }
            frame?.let { next = next.copy(frame = it) }
            st.copy(edit = next)
        }
        if (username != null) debounceUsernameCheck()
    }

    private fun debounceUsernameCheck() {
        usernameJob?.cancel()
        val clean = _state.value.edit?.usernameClean.orEmpty()
        val orig = _state.value.edit?.originalUsername.orEmpty()
        if (clean.isEmpty() || clean == orig || !SocialValidate.validateUsername(clean).ok) return
        usernameJob = viewModelScope.launch {
            delay(500)
            try {
                val repo = runCatching { repoOrThrow() }.getOrNull() ?: return@launch
                if (_state.value.edit?.usernameClean != clean) return@launch
                val status = when (repo.checkUsername(clean)) {
                    is SocialRepository.UsernameStatus.Available -> UsernameStatusUi.Ok
                    is SocialRepository.UsernameStatus.Mine -> UsernameStatusUi.Mine
                    is SocialRepository.UsernameStatus.Taken -> UsernameStatusUi.Taken
                    is SocialRepository.UsernameStatus.Invalid -> UsernameStatusUi.Invalid
                }
                if (_state.value.edit?.usernameClean == clean) {
                    _state.update { st -> st.copy(edit = st.edit?.copy(usernameStatus = status)) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep the Checking state — saving re-validates anyway.
            }
        }
    }

    fun saveEdit(onDone: () -> Unit) {
        val e = _state.value.edit ?: return
        if (e.saving || e.loading) return
        viewModelScope.launch {
            _state.update { st -> st.copy(edit = st.edit?.copy(saving = true)) }
            suspend fun abort(msg: Msg) {
                emit(msg)
                _state.update { st -> st.copy(edit = st.edit?.copy(saving = false)) }
            }
            try {
                val repo = repoOrThrow()
                val acc = settings.account.first() ?: throw SocialException(SocialFailure.NoAuth)
                val clean = SocialValidate.normalizeUsername(e.usernameClean)
                if (clean.isNotEmpty()) {
                    val check = SocialValidate.validateUsername(clean)
                    if (!check.ok) {
                        _state.update { st ->
                            st.copy(
                                edit = st.edit?.copy(
                                    usernameStatus = UsernameStatusUi.Invalid,
                                    usernameError = check.error,
                                ),
                            )
                        }
                        abort(Msg(usernameErrorString(check.error)))
                        return@launch
                    }
                    if (clean != e.originalUsername) {
                        when (repo.checkUsername(clean)) {
                            is SocialRepository.UsernameStatus.Taken -> {
                                _state.update { st ->
                                    st.copy(edit = st.edit?.copy(usernameStatus = UsernameStatusUi.Taken))
                                }
                                abort(Msg(R.string.edit_username_taken))
                                return@launch
                            }
                            is SocialRepository.UsernameStatus.Invalid -> {
                                abort(Msg(R.string.edit_username_invalid))
                                return@launch
                            }
                            else -> Unit
                        }
                    }
                }
                val displayName = SocialValidate.sanitizeDisplayName(e.name)
                    .ifEmpty { acc.username }
                    .ifEmpty { acc.email.substringBefore("@") }
                    .ifEmpty { "User" }
                if (!SocialValidate.isSafeAvatar(e.avatar)) {
                    abort(Msg(R.string.edit_avatar_failed))
                    return@launch
                }
                // Claim first: a concurrent take aborts the save before anything is written.
                // A cleared handle stays reserved in the registry (matches the desktop app).
                if (clean.isNotEmpty() && clean != e.originalUsername) {
                    val claimed = repo.claimUsername(acc.uid, clean, e.originalUsername)
                    if (!claimed) {
                        _state.update { st ->
                            st.copy(edit = st.edit?.copy(usernameStatus = UsernameStatusUi.Taken))
                        }
                        abort(Msg(R.string.edit_username_taken))
                        return@launch
                    }
                }
                val data = OwnSocialData(
                    displayName = displayName,
                    username = clean,
                    avatar = e.avatar,
                    about = SocialValidate.sanitizeBio(e.about),
                    links = SocialValidate.sanitizeLinks(e.telegram, e.soundcloud, e.vk),
                    isPrivate = e.isPrivate,
                    frame = e.frame,
                    email = e.email.ifEmpty { acc.email },
                )
                val saved = repo.saveAccountProfile(acc.uid, data)
                _state.update { st ->
                    st.copy(
                        profile = saved,
                        isOwn = true,
                        myUid = acc.uid,
                        edit = st.edit?.copy(
                            saving = false,
                            originalUsername = saved.username,
                            username = saved.username,
                            usernameClean = saved.username,
                            usernameStatus = if (saved.username.isEmpty()) {
                                UsernameStatusUi.Idle
                            } else {
                                UsernameStatusUi.Mine
                            },
                        ),
                    )
                }
                emit(Msg(R.string.profile_saved))
                onDone()
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                abort(failureMessage(ex))
            }
        }
    }

    fun usernameErrorString(error: SocialValidate.UsernameError?): Int = when (error) {
        SocialValidate.UsernameError.TOO_SHORT -> R.string.edit_username_short
        SocialValidate.UsernameError.TOO_LONG -> R.string.edit_username_long
        SocialValidate.UsernameError.BAD_CHARS -> R.string.edit_username_badchars
        SocialValidate.UsernameError.RESERVED -> R.string.edit_username_reserved
        else -> R.string.edit_username_invalid
    }

    // ------------------------------------------------------------ session

    fun logout() {
        viewModelScope.launch {
            settings.clearAccount()
            api.authToken = null
            emit(Msg(R.string.toast_logged_out))
        }
    }
}
