package app.votify.mobile.ui.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.FirebaseConfig
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.data.WorkshopThemeDoc
import app.votify.mobile.data.WorkshopThemeUiSpec
import app.votify.mobile.data.CustomPrefs
import app.votify.mobile.data.parseCustomPrefs
import app.votify.mobile.data.toJson
import app.votify.mobile.ui.theme.AmberPalette
import app.votify.mobile.ui.theme.AzurePalette
import app.votify.mobile.ui.theme.EmeraldPalette
import app.votify.mobile.ui.theme.GraphitePalette
import app.votify.mobile.ui.theme.OledBlackPalette
import app.votify.mobile.ui.theme.RosePalette
import app.votify.mobile.ui.theme.VioletPalette
import app.votify.mobile.ui.theme.WorkshopThemeSpec
import app.votify.mobile.ui.theme.parseWorkshopSpec
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class WorkshopUiState(
    /** Raw Firebase Web Config JSON draft (editable, never prefilled with the real key). */
    val configDraft: String = "",
    val firebaseReady: Boolean = false,
    val firebaseProject: String = "",
    val community: List<WorkshopThemeDoc> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val publishing: Boolean = false,
    /** Accent hex currently applied through the Workshop (for the dot checkmark). */
    val appliedAccent: String? = null,
    /** Background image URL currently applied ("" = none). */
    val appliedBackgroundUrl: String = "",
)

sealed interface WorkshopEvent {
    data class Applied(val name: String) : WorkshopEvent
    data class Published(val title: String) : WorkshopEvent
    data class ConfigSaved(val fromServer: Boolean) : WorkshopEvent
    data object ConfigInvalid : WorkshopEvent
    data object BackgroundSet : WorkshopEvent
    data object BackgroundCleared : WorkshopEvent
    data object BadUrl : WorkshopEvent
    data object BadColor : WorkshopEvent
    /** Free-form snackbar text (delete/unlink results). */
    data class Message(val text: String) : WorkshopEvent
    data object Deleted : WorkshopEvent
    data object Unlinked : WorkshopEvent
}

/**
 * «Мастерская тем»: local accent picker + community themes from the user's Firebase project
 * (collection `workshopThemes`, schema v1 — see firestore.rules). Firebase config comes from
 * the PC server (/api/firebase/config) or is pasted manually, then works fully standalone.
 */
class WorkshopViewModel(
    private val settingsRepo: SettingsRepository,
    private val api: VotifyApi,
    private val music: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopUiState())
    val state: StateFlow<WorkshopUiState> = _state

    /** Customization prefs (placement toggles / artwork blur+dim / gifArtwork). */
    val prefs: StateFlow<CustomPrefs> = settingsRepo.settings
        .map { parseCustomPrefs(it.customPrefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomPrefs())

    /** Current app background (details-screen backdrop fallback). */
    val appBackgroundUrl: StateFlow<String> = settingsRepo.settings
        .map { it.backgroundUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** uid of the signed-in account (theme ownership). */
    val accountUid: StateFlow<String?> = settingsRepo.account
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The workshop color spec currently applied (details-screen sliders). */
    val activeSpec: StateFlow<WorkshopThemeSpec> = settingsRepo.settings
        .map { s -> if (s.customTheme.isNotBlank()) parseWorkshopSpec(s.customTheme) else WorkshopThemeSpec() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopThemeSpec())

    fun updatePrefs(transform: (CustomPrefs) -> CustomPrefs) {
        viewModelScope.launch {
            val cur = settingsRepo.settings.first()
            settingsRepo.setCustomPrefs(parseCustomPrefs(cur.customPrefs).let(transform).toJson())
        }
    }

    private val _events = MutableSharedFlow<WorkshopEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkshopEvent> = _events

    private val json = Json { ignoreUnknownKeys = true }

    private var rest: FirebaseRest? = null

    init {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            val cfg = FirebaseRest.effectiveConfig(s.firebaseConfig)
            val spec = if (s.theme == AppTheme.Workshop && s.customTheme.isNotBlank()) {
                runCatching { json.decodeFromString<WorkshopThemeSpec>(s.customTheme) }.getOrNull()
            } else null
            _state.update {
                it.copy(
                    configDraft = "",
                    firebaseReady = cfg != null,
                    firebaseProject = cfg?.projectId ?: "",
                    appliedAccent = spec?.primary?.uppercase(),
                    appliedBackgroundUrl = spec?.backgroundUrl ?: "",
                )
            }
            if (cfg != null) {
                rest = FirebaseRest(cfg)
                refreshThemes()
            }
        }
    }

    fun onConfigDraftChange(v: String) = _state.update { it.copy(configDraft = v) }

    /** Save a pasted Firebase Web Config (JSON). */
    fun saveConfig(raw: String) {
        val cfg = FirebaseRest.parseConfig(raw)
        if (cfg == null) {
            _events.tryEmit(WorkshopEvent.ConfigInvalid)
            return
        }
        viewModelScope.launch {
            settingsRepo.setFirebaseConfig(raw.trim())
            rest = FirebaseRest(cfg)
            _state.update { it.copy(firebaseReady = true, firebaseProject = cfg.projectId) }
            _events.tryEmit(WorkshopEvent.ConfigSaved(fromServer = false))
            refreshThemes()
        }
    }

    /** Pull /api/firebase/config from the user's PC server (server mode only). */
    fun fetchConfigFromServer() {
        viewModelScope.launch {
            if (!music.isServerMode) {
                _state.update { it.copy(error = "Сервер не подключён — вставьте конфиг вручную") }
                return@launch
            }
            runCatching { api.firebaseConfig() }
                .onSuccess { raw ->
                    val cfg = FirebaseRest.parseConfig(raw)
                    if (cfg == null) {
                        _state.update { it.copy(error = "На сервере не настроен Firebase (firebase-config.json)") }
                    } else {
                        settingsRepo.setFirebaseConfig(raw.trim())
                        rest = FirebaseRest(cfg)
                        _state.update { it.copy(firebaseReady = true, firebaseProject = cfg.projectId, error = null) }
                        _events.tryEmit(WorkshopEvent.ConfigSaved(fromServer = true))
                        refreshThemes()
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "error") } }
        }
    }

    fun refreshThemes() {
        val client = rest ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { client.listThemes() }
                .onSuccess { themes -> _state.update { it.copy(loading = false, community = themes) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
        }
    }

    /** Apply a community theme locally. */
    fun applyTheme(doc: WorkshopThemeDoc) {
        viewModelScope.launch { applyThemeNow(doc) }
    }

    private suspend fun applyThemeNow(doc: WorkshopThemeDoc) {
        settingsRepo.setCustomTheme(json.encodeToString(WorkshopThemeSpec.serializer(), doc.theme.toSpec()))
        settingsRepo.setTheme(AppTheme.Workshop)
        _events.tryEmit(WorkshopEvent.Applied(doc.title))
    }

    /**
     * Theme placement toggles (Детальная настройка темы): background / player artwork /
     * slider accent. Each writes its pref and applies (or clears) immediately.
     */
    fun setThemeBackground(doc: WorkshopThemeDoc, on: Boolean) {
        updatePrefs { it.copy(themeApplyBackground = on) }
        viewModelScope.launch {
            if (on) {
                settingsRepo.setBackgroundUrl(doc.theme.backgroundUrl)
                applyThemeNow(doc)
            } else {
                settingsRepo.setBackgroundUrl("")
                settingsRepo.setCustomTheme("")
                settingsRepo.setTheme(AppTheme.OledBlack)
            }
        }
    }

    fun setThemeArtwork(doc: WorkshopThemeDoc, on: Boolean) {
        updatePrefs { it.copy(themeApplyArtwork = on, gifArtwork = if (on) doc.theme.backgroundUrl else "") }
    }

    /** «Слайдер» = настройка слайдера плеера: красит только прогресс-бар плеера в цвет темы. */
    fun setThemeSlider(doc: WorkshopThemeDoc, on: Boolean) {
        updatePrefs { it.copy(themeApplySlider = on) }
    }

    /**
     * Fine-tuning sliders of the details screen: transform the applied spec (applying the
     * doc first if no workshop theme is active).
     */
    fun transformSpec(doc: WorkshopThemeDoc, transform: (WorkshopThemeSpec) -> WorkshopThemeSpec) {
        viewModelScope.launch {
            val cur = settingsRepo.settings.first()
            val base = if (cur.customTheme.isNotBlank()) parseWorkshopSpec(cur.customTheme) else doc.theme.toSpec()
            settingsRepo.setCustomTheme(json.encodeToString(WorkshopThemeSpec.serializer(), transform(base)))
            settingsRepo.setTheme(AppTheme.Workshop)
        }
    }

    /** «Удалить»: unlink locally; if the theme is the user's own — delete it from Firestore. */
    fun deleteTheme(doc: WorkshopThemeDoc) {
        viewModelScope.launch {
            val cur = settingsRepo.settings.first()
            if (cur.backgroundUrl == doc.theme.backgroundUrl) {
                settingsRepo.setBackgroundUrl("")
                settingsRepo.setCustomTheme("")
                settingsRepo.setTheme(AppTheme.OledBlack)
            }
            val acct = settingsRepo.account.first()
            if (acct != null && acct.isFirebase && acct.uid == doc.ownerId) {
                val cfg = FirebaseRest.effectiveConfig(cur.firebaseConfig)
                if (cfg != null) {
                    val client = FirebaseRest(cfg)
                    val token = runCatching { client.refreshIdToken(acct.refreshToken) }.getOrNull() ?: acct.token
                    runCatching { client.deleteWorkshopTheme(token, doc.id) }
                        .onSuccess {
                            _state.update { st -> st.copy(community = st.community.filterNot { it.id == doc.id }) }
                            _events.tryEmit(WorkshopEvent.Deleted)
                        }
                        .onFailure { e -> _events.tryEmit(WorkshopEvent.Message(e.message ?: "error")) }
                    return@launch
                }
            }
            _events.tryEmit(WorkshopEvent.Unlinked)
        }
    }

    /** Apply a custom accent on top of the current spec (keeps the background). */
    fun applyAccent(hex: String) {
        val accent = normalizeHex(hex) ?: run {
            _events.tryEmit(WorkshopEvent.BadColor)
            return
        }
        viewModelScope.launch {
            val spec = currentSpecModel().copy(primary = accent)
            settingsRepo.setCustomTheme(json.encodeToString(WorkshopThemeSpec.serializer(), spec))
            settingsRepo.setTheme(AppTheme.Workshop)
            _state.update { it.copy(appliedAccent = accent) }
            _events.tryEmit(WorkshopEvent.Applied("Своя тема"))
        }
    }

    /** Set an app-wide background image (any image URL, incl. animated GIF/WebP). */
    fun applyBackground(url: String) {
        val clean = url.trim().take(2048)
        if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            _events.tryEmit(WorkshopEvent.BadUrl)
            return
        }
        viewModelScope.launch {
            val spec = currentSpecModel().copy(backgroundUrl = clean)
            settingsRepo.setCustomTheme(json.encodeToString(WorkshopThemeSpec.serializer(), spec))
            settingsRepo.setTheme(AppTheme.Workshop)
            _state.update { it.copy(appliedBackgroundUrl = clean, appliedAccent = spec.primary.uppercase()) }
            _events.tryEmit(if (clean.isBlank()) WorkshopEvent.BackgroundCleared else WorkshopEvent.BackgroundSet)
        }
    }

    /** Publish the currently applied theme to the community list (requires a Firebase account). */
    fun publish(title: String, description: String) {
        val client = rest ?: return
        if (_state.value.publishing) return
        val cleanTitle = title.trim()
        if (cleanTitle.length !in 3..60) {
            _state.update { it.copy(error = "Название темы — от 3 до 60 символов") }
            return
        }
        if (description.length > 240) {
            _state.update { it.copy(error = "Описание — не больше 240 символов") }
            return
        }
        viewModelScope.launch {
            val account = settingsRepo.account.first()
            if (account == null || !account.isFirebase) {
                _state.update { it.copy(error = "Сначала войдите в аккаунт (Настройки → Аккаунт)") }
                return@launch
            }
            _state.update { it.copy(publishing = true, error = null) }
            val spec = currentSpec()
            runCatching { client.publishTheme(account.token, account.uid, account.username.ifBlank { account.email }.take(40), cleanTitle, description.trim(), spec) }
                .onSuccess {
                    _state.update { it.copy(publishing = false) }
                    _events.tryEmit(WorkshopEvent.Published(cleanTitle))
                    refreshThemes()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(publishing = false, error = e.message ?: "error") }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** The spec that would be published: the applied custom theme or the current built-in. */
    private suspend fun currentSpec(): WorkshopThemeUiSpec = currentSpecModel().toUiSpec()

    /** Same as [currentSpec] but as the persistable model (background URL included). */
    private suspend fun currentSpecModel(): WorkshopThemeSpec {
        val settings = settingsRepo.settings.first()
        if (settings.customTheme.isNotBlank()) {
            runCatching { json.decodeFromString<WorkshopThemeSpec>(settings.customTheme) }.getOrNull()
                ?.let { return if (it.backgroundUrl.isBlank()) it.copy(backgroundUrl = settings.backgroundUrl) else it }
        }
        val palette = when (settings.theme) {
            AppTheme.Graphite -> GraphitePalette
            AppTheme.Violet -> VioletPalette
            AppTheme.Azure -> AzurePalette
            AppTheme.Emerald -> EmeraldPalette
            AppTheme.Amber -> AmberPalette
            AppTheme.Rose -> RosePalette
            else -> OledBlackPalette
        }
        return WorkshopThemeSpec(
            primary = palette.primary.toHex(),
            background = palette.surfaceBase.toHex(),
            text = palette.textPrimary.toHex(),
            cards = palette.surfaceContainer.toHex(),
            borders = palette.borderSubtle.toHex(),
            focus = palette.borderProminent.toHex(),
        )
    }

    private fun WorkshopThemeUiSpec.toSpec() = WorkshopThemeSpec(
        primary, background, text, cards, borders, focus, mode,
        backgroundPreset, backgroundUrl, cornerRadius, uiTransparency, backgroundBlur, particles, fontFamily,
    )

    private fun WorkshopThemeSpec.toUiSpec() = WorkshopThemeUiSpec(
        primary, background, text, cards, borders, focus, mode,
        backgroundPreset, backgroundUrl, cornerRadius, uiTransparency, backgroundBlur, particles, fontFamily,
    )

    private fun androidx.compose.ui.graphics.Color.toHex(): String =
        "#%06X".format(toArgb() and 0xFFFFFF)

    private fun normalizeHex(hex: String): String? {
        val v = hex.trim()
        if (!v.matches(Regex("^#?([0-9a-fA-F]{6})$"))) return null
        return "#" + v.removePrefix("#").uppercase()
    }
}
