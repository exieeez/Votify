package app.votify.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.BuildConfig
import android.content.Context
import app.votify.mobile.data.Account
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.CustomPrefs
import app.votify.mobile.data.NamedPreset
import app.votify.mobile.ui.theme.WorkshopThemeSpec
import app.votify.mobile.data.parseCustomPrefs
import app.votify.mobile.data.toAppTheme
import app.votify.mobile.data.toJson
import app.votify.mobile.ui.theme.AmberPalette
import app.votify.mobile.ui.theme.AzurePalette
import app.votify.mobile.ui.theme.EmeraldPalette
import app.votify.mobile.ui.theme.GraphitePalette
import app.votify.mobile.ui.theme.OledBlackPalette
import app.votify.mobile.ui.theme.RosePalette
import app.votify.mobile.ui.theme.VioletPalette
import app.votify.mobile.ui.theme.parseWorkshopSpec
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.AudioQuality
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.PlayerBackground
import app.votify.mobile.data.Settings
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.WaveLang
import app.votify.mobile.data.WaveMode
import app.votify.mobile.data.CloudSync
import app.votify.mobile.data.FirebaseRest
import app.votify.mobile.data.VotifyApi
import app.votify.mobile.VotifyApp
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class ServerStatus { Unknown, Checking, Online, Offline, Embedded }

data class SettingsUiState(
    /** Quality as the server reports it; null until the first successful GET. */
    val serverQuality: AudioQuality? = null,
    val qualitySyncing: Boolean = false,
    val qualityOffline: Boolean = false,
    val server: ServerStatus = ServerStatus.Unknown,
    val serverName: String = "",
    val serverVersion: String = "",
)

sealed interface SettingsEvent {
    data class QualitySaved(val quality: AudioQuality) : SettingsEvent
    data object QualityFailed : SettingsEvent
    data class ServerSaved(val url: String) : SettingsEvent
    data object ServerInvalid : SettingsEvent
    data object LoggedOut : SettingsEvent
    /** Free-form snackbar text (sync, updates, backgrounds…). */
    data class Message(val text: String) : SettingsEvent
    data class PresetApplied(val name: String) : SettingsEvent
}

/**
 * Settings: standalone («Без сервера») or backed by the user's own PC server («Свой сервер»).
 * Standalone is the default — search/streaming/lyrics run on the phone, so audio quality is a
 * purely local choice; in server mode it is synced with the backend like before.
 */
class SettingsViewModel(
    private val api: VotifyApi,
    private val settingsRepo: SettingsRepository,
    private val library: LibraryRepository,
    private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** The workshop color spec currently in effect (customTheme or the active theme's defaults). */
    val colorSpec: StateFlow<WorkshopThemeSpec> = settingsRepo.settings
        .map { s -> if (s.customTheme.isNotBlank()) parseWorkshopSpec(s.customTheme) else defaultSpec(s.theme) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopThemeSpec())

    /** The Dotify customization blob (Пресеты/Свайпы/Плеер/Интерфейс/…). */
    val prefs: StateFlow<CustomPrefs> = settingsRepo.settings
        .map { parseCustomPrefs(it.customPrefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomPrefs())

    private val _storage = MutableStateFlow(StorageStats())
    val storage: StateFlow<StorageStats> = _storage

    /** True = standalone mode (no server). */
    val embedded: StateFlow<Boolean> = settingsRepo.settings
        .map { it.serverUrl.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Signed-in account; null while logged out. */
    val account: StateFlow<Account?> =
        settingsRepo.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The backend address currently in effect (only meaningful in server mode). */
    val apiBase: StateFlow<String> = settingsRepo.settings
        .map { it.serverUrl.ifBlank { BuildConfig.API_BASE_URL } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, api.baseUrl.toString().trimEnd('/'))

    val favoriteCount: StateFlow<Int> =
        library.favoriteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val historyCount: StateFlow<Int> =
        library.historyCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events

    init {
        viewModelScope.launch {
            val isServer = settingsRepo.settings.first().serverUrl.isNotBlank()
            if (isServer) {
                syncQualityFromServer()
                checkServer()
            } else {
                _state.update { it.copy(server = ServerStatus.Embedded) }
            }
        }
    }

    /** GET /api/network/settings — the server is the source of truth for stream quality. */
    fun syncQualityFromServer() {
        if (embedded.value) return
        viewModelScope.launch {
            _state.update { it.copy(qualitySyncing = true) }
            runCatching { api.networkSettings() }
                .onSuccess { cfg ->
                    val q = AudioQuality.fromKey(cfg.audioQuality)
                    settingsRepo.setAudioQuality(q)
                    _state.update { it.copy(serverQuality = q, qualitySyncing = false, qualityOffline = false) }
                }
                .onFailure { _state.update { it.copy(qualitySyncing = false, qualityOffline = true) } }
        }
    }

    /**
     * Audio quality: in server mode POST it to the backend, in standalone mode it just picks
     * which stream bitrate the phone resolves.
     */
    fun setAudioQuality(q: AudioQuality) {
        if (embedded.value) {
            viewModelScope.launch {
                settingsRepo.setAudioQuality(q)
                _events.tryEmit(SettingsEvent.QualitySaved(q))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(qualitySyncing = true) }
            runCatching { api.setAudioQuality(q.key) }
                .onSuccess { cfg ->
                    val applied = AudioQuality.fromKey(cfg.audioQuality)
                    settingsRepo.setAudioQuality(applied)
                    _state.update { it.copy(serverQuality = applied, qualitySyncing = false, qualityOffline = false) }
                    _events.tryEmit(SettingsEvent.QualitySaved(applied))
                }
                .onFailure {
                    _state.update { it.copy(qualitySyncing = false, qualityOffline = true) }
                    _events.tryEmit(SettingsEvent.QualityFailed)
                }
        }
    }

    fun checkServer() {
        if (embedded.value) {
            _state.update { it.copy(server = ServerStatus.Embedded) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(server = ServerStatus.Checking) }
            runCatching { api.health() }
                .onSuccess { h ->
                    _state.update { it.copy(server = if (h.ok) ServerStatus.Online else ServerStatus.Offline, serverName = h.name, serverVersion = h.version) }
                }
                .onFailure { _state.update { it.copy(server = ServerStatus.Offline) } }
        }
    }

    /**
     * Apply a new backend address (Settings → «Сервер»). An empty value switches back to
     * standalone mode («Без сервера»). The change is applied immediately, then health + quality
     * are re-synced from the new server.
     */
    fun saveServerUrl(raw: String) {
        val candidate = raw.trim().ifBlank { BuildConfig.API_BASE_URL }
        val parsed = runCatching { api.setBaseUrl(candidate) }
        if (parsed.isFailure) {
            _events.tryEmit(SettingsEvent.ServerInvalid)
            return
        }
        val url = parsed.getOrThrow().toString().trimEnd('/')
        viewModelScope.launch {
            settingsRepo.setServerUrl(raw.trim()) // blank → standalone mode
            _events.tryEmit(SettingsEvent.ServerSaved(if (raw.isBlank()) "" else url))
            if (raw.isBlank()) {
                _state.update { it.copy(server = ServerStatus.Embedded, serverQuality = null, qualityOffline = false) }
            } else {
                checkServer()
                syncQualityFromServer()
            }
        }
    }

    /** Sign out: drop the stored token so requests go out anonymous again. */
    fun logout() {
        viewModelScope.launch {
            settingsRepo.clearAccount()
            api.authToken = null
            _events.tryEmit(SettingsEvent.LoggedOut)
        }
    }

    fun setTheme(v: AppTheme) = launchSave { settingsRepo.setTheme(v) }
    fun clearCustomTheme() = launchSave { settingsRepo.setCustomTheme("") }
    fun setArtworkStyle(v: ArtworkStyle) = launchSave { settingsRepo.setArtworkStyle(v) }
    fun setPlayerBackground(v: PlayerBackground) = launchSave { settingsRepo.setPlayerBackground(v) }
    fun setShowLyricsOverArtwork(v: Boolean) = launchSave { settingsRepo.setShowLyricsOverArtwork(v) }
    fun setWaveLang(v: WaveLang) = launchSave { settingsRepo.setWaveLang(v) }
    fun setWaveMode(v: WaveMode) = launchSave { settingsRepo.setWaveMode(v) }
    fun setWaveExcludeListened(v: Boolean) = launchSave { settingsRepo.setWaveExcludeListened(v) }
    fun setMiniPlayerSwipeChangesTrack(v: Boolean) = launchSave { settingsRepo.setMiniPlayerSwipeChangesTrack(v) }
    fun setVolumeButtonsSkip(v: Boolean) = launchSave { settingsRepo.setVolumeButtonsSkip(v) }

    fun clearHistory() = launchSave {
        library.clearHistory()
        _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.storage_history_cleared)))
        refreshStorage()
    }

    // ------------------------------------------------------------ Dotify customization

    fun setLanguage(key: String) {
        if (key == "en") {
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.settings_language_soon)))
        }
    }

    /** Update one or more customization fields; the theme mapping is re-applied automatically. */
    fun updatePrefs(transform: (CustomPrefs) -> CustomPrefs) = launchSave {
        val cur = parseCustomPrefs(settingsRepo.settings.first().customPrefs)
        val next = transform(cur)
        settingsRepo.setCustomPrefs(next.toJson())
        settingsRepo.setTheme(next.toAppTheme())
    }

    /** Шрифты/прозрачность apply instantly through VotifyTheme; nothing else to do. */

    // ------------------------------------------------------------ Хранилище

    data class StorageStats(
        val files: Int = 0,
        val bytes: Long = 0,
        val percent: Int = 0,
        val offline: Int = 0,
        val texts: Int = 0,
        val fav: Int = 0,
        val hist: Int = 0,
        val pls: Int = 0,
    )

    data class DownloadItem(val file: String, val title: String, val artist: String, val bytes: Long)

    fun refreshStorage() {
        viewModelScope.launch {
            val fav = library.favoriteCount.first()
            val hist = library.historyCount.first()
            val pls = library.playlists.first().size
            val bytes = withContext(Dispatchers.IO) { dirSize(appContext.cacheDir) }
            val (dlCount, dlBytes) = app.votify.mobile.VotifyApp.instance.music.downloadStats()
            val files = fav + hist + pls
            val texts = app.votify.mobile.VotifyApp.instance.music.lyricsCacheCount()
            _storage.value = StorageStats(
                files = files,
                bytes = bytes + dlBytes,
                offline = dlCount,
                texts = texts,
                fav = fav,
                hist = hist,
                pls = pls,
                percent = if (files == 0 && bytes == 0L) 0 else maxOf(1, (bytes / 524_288L).toInt().coerceAtMost(100)),
            )
        }
    }

    /** Remove all offline music downloads. */
    fun clearDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                java.io.File(appContext.filesDir, "downloads").deleteRecursively()
            }
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.storage_downloads_cleared)))
            refreshStorage()
        }
    }

    /** Offline copies with best-effort titles (for the storage sheet). */
    suspend fun downloads(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val dir = java.io.File(appContext.filesDir, "downloads")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".audio") }
            ?.sortedByDescending { it.length() } ?: return@withContext emptyList()
        files.map { f ->
            val id = f.name.removeSuffix(".audio")
            val meta = runCatching { library.trackById(id) }.getOrNull()
            DownloadItem(f.name, meta?.title ?: id, meta?.artist.orEmpty(), f.length())
        }
    }

    /** Remove one offline copy (validated file name — no path traversal). */
    fun deleteDownload(file: String) {
        viewModelScope.launch {
            if ("/" in file || "\\" in file || ".." in file || !file.endsWith(".audio")) return@launch
            withContext(Dispatchers.IO) {
                runCatching { java.io.File(java.io.File(appContext.filesDir, "downloads"), file).delete() }
            }
            refreshStorage()
        }
    }

    fun clearLyricsCache() {
        viewModelScope.launch {
            app.votify.mobile.VotifyApp.instance.music.clearLyricsCache()
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.storage_texts_cleared)))
            refreshStorage()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            library.clearAllData()
            withContext(Dispatchers.IO) {
                appContext.cacheDir.deleteRecursively()
                coil.Coil.imageLoader(appContext).diskCache?.clear()
                java.io.File(appContext.filesDir, "backgrounds").deleteRecursively()
                java.io.File(appContext.filesDir, "downloads").deleteRecursively()
                java.io.File(appContext.filesDir, "lyrics").deleteRecursively()
            }
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.storage_cleared)))
            refreshStorage()
        }
    }

    private fun dirSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // ------------------------------------------------------------ Синхронизация (свой сервер)

    /** Signed-in Firebase account with a freshly refreshed idToken, or null. */
    private suspend fun firebaseClient(acct: Account): Pair<FirebaseRest, String>? {
        val cfg = FirebaseRest.effectiveConfig(settingsRepo.settings.first().firebaseConfig) ?: return null
        val client = FirebaseRest(cfg)
        val token = runCatching { client.refreshIdToken(acct.refreshToken) }.getOrNull() ?: acct.token
        return client to token
    }

    fun pushSync() {
        viewModelScope.launch {
            val acct = settingsRepo.account.first()
            if (acct == null) {
                _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_need_login)))
                return@launch
            }
            if (acct.isFirebase) {
                val sync = CloudSync(VotifyApp.instance.database, settingsRepo)
                val pair = firebaseClient(acct)
                if (pair == null) {
                    _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_need_server)))
                    return@launch
                }
                val (client, token) = pair
                runCatching { client.pushUserSync(token, acct.uid, sync.toJson(sync.exportBlob())) }
                    .onSuccess { _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_pushed))) }
                    .onFailure { e -> _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_failed, e.message ?: ""))) }
                return@launch
            }
            runCatching { api.pushSync(prefs.value.toJson()) }
                .onSuccess { _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_pushed))) }
                .onFailure { e -> _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_failed, e.message ?: ""))) }
        }
    }

    fun pullSync() {
        viewModelScope.launch {
            val acct = settingsRepo.account.first()
            if (acct == null) {
                _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_need_login)))
                return@launch
            }
            if (acct.isFirebase) {
                val sync = CloudSync(VotifyApp.instance.database, settingsRepo)
                val pair = firebaseClient(acct)
                if (pair == null) {
                    _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_need_server)))
                    return@launch
                }
                val (client, token) = pair
                runCatching { client.pullUserSync(token, acct.uid) }
                    .onSuccess { raw ->
                        val blob = raw?.let { sync.fromJson(it) }
                        if (blob != null) {
                            sync.importBlob(blob)
                            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_pulled)))
                        } else {
                            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_empty)))
                        }
                    }
                    .onFailure { e -> _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_failed, e.message ?: ""))) }
                return@launch
            }
            runCatching { api.pullSync() }
                .onSuccess { data ->
                    val pulled = parseCustomPrefs(data.settings)
                    updatePrefs { cur -> pulled.copy(presets = cur.presets, backgrounds = pulled.backgrounds.ifEmpty { cur.backgrounds }) }
                    _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_pulled)))
                }
                .onFailure { e -> _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.sync_failed, e.message ?: ""))) }
        }
    }

    // ------------------------------------------------------------ Проверка обновлений

    fun checkUpdates() {
        viewModelScope.launch {
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.update_checking)))
            val latest = withContext(Dispatchers.IO) {
                runCatching {
                    val url = java.net.URL("https://api.github.com/repos/exieeez/Votify/releases/tags/android-debug")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.inputStream.bufferedReader().readText()
                }.getOrNull()
            } ?: run {
                _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.update_check_failed)))
                return@launch
            }
            val updatedAt = Regex("\"updatedAt\":\"([^\"]+)\"").find(latest)?.groupValues?.get(1) ?: ""
            val newer = runCatching {
                java.time.Instant.parse(updatedAt) > java.time.Instant.parse(BuildConfig.BUILD_TIME)
            }.getOrDefault(false)
            _events.tryEmit(
                if (newer) {
                    SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.update_available))
                } else {
                    SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.update_latest))
                },
            )
        }
    }

    // ------------------------------------------------------------ Пресеты

    fun createPreset(name: String) {
        val clean = name.trim().take(40).ifEmpty { return }
        updatePrefs { cur ->
            val snapshot = cur.copy(presets = emptyList()) // snapshot without the list itself
            cur.copy(presets = cur.presets + NamedPreset(clean, snapshot))
        }
    }

    fun applyPreset(preset: NamedPreset) {
        updatePrefs { cur -> preset.prefs.copy(presets = cur.presets) }
        _events.tryEmit(SettingsEvent.PresetApplied(preset.name))
    }

    fun deletePreset(name: String) = updatePrefs { cur -> cur.copy(presets = cur.presets.filterNot { it.name == name }) }

    fun importPreset(json: String) {
        val preset = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<NamedPreset>(json.trim())
        }.getOrNull()
        if (preset == null || preset.name.isBlank()) {
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.preset_bad_json)))
            return
        }
        updatePrefs { cur -> cur.copy(presets = cur.presets + NamedPreset(preset.name.take(40), preset.prefs.copy(presets = emptyList()))) }
        _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.preset_imported, preset.name)))
    }

    fun exportPreset(preset: NamedPreset): String =
        kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(NamedPreset.serializer(), preset)

    // ------------------------------------------------------------ Фоны (Библиотека кастомизации)

    fun addBackground(url: String) {
        val clean = url.trim().take(2048)
        if (!clean.startsWith("http://") && !clean.startsWith("https://") && !clean.startsWith("/")) {
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.toast_bg_bad_url)))
            return
        }
        updatePrefs { cur -> cur.copy(backgrounds = (cur.backgrounds + clean).distinct()) }
    }

    fun removeBackground(url: String) = updatePrefs { cur -> cur.copy(backgrounds = cur.backgrounds - url) }

    /** Remove a saved background from the gallery — and clear it if it is the active one. */
    fun deleteBackground(url: String) {
        updatePrefs { cur -> cur.copy(backgrounds = cur.backgrounds - url) }
        viewModelScope.launch {
            if (settingsRepo.settings.first().backgroundUrl == url) applyBackground("")
        }
    }

    /** Затемнение фоновой картинки, % (0..92). */
    fun setBackgroundDim(v: Int) = updatePrefs { cur -> cur.copy(bgDim = v.coerceIn(0, 92)) }

    /** Размытие фоновой картинки, dp (0..60). */
    fun setBackgroundBlur(v: Int) = updatePrefs { cur -> cur.copy(bgBlur = v.coerceIn(0, 60)) }

    /** Масштаб фона (1..5) — обрезка широкого ПК-фона под экран телефона. */
    fun setBackgroundScale(v: Float) = updatePrefs { cur -> cur.copy(bgScale = v.coerceIn(1f, 5f)) }

    /** Сдвиг кадра фона: -1..1 по каждой оси. */
    fun setBackgroundOffsetX(v: Float) = updatePrefs { cur -> cur.copy(bgOffsetX = v.coerceIn(-1f, 1f)) }

    fun setBackgroundOffsetY(v: Float) = updatePrefs { cur -> cur.copy(bgOffsetY = v.coerceIn(-1f, 1f)) }

    /** Как фон ложится на экран: 0 — заполнить (с обрезкой), 1 — целиком, 2 — растянуть. */
    fun setBackgroundFit(v: Int) = updatePrefs { cur -> cur.copy(bgFit = v.coerceIn(0, 2)) }

    /** Сбросить кадрирование: масштаб 1 и кадр по центру. */
    fun resetBackgroundCrop() = updatePrefs { cur -> cur.copy(bgScale = 1f, bgOffsetX = 0f, bgOffsetY = 0f) }

    /** Apply a saved background (or "" to clear). Own setting field — the theme is untouched. */
    fun applyBackground(url: String) {
        viewModelScope.launch {
            val clean = url.trim()
            settingsRepo.setBackgroundUrl(clean)
            // Keep the workshop-spec mirror in sync (cloud sync carries customTheme).
            val cur = settingsRepo.settings.first()
            if (cur.customTheme.isNotBlank()) {
                val spec = parseWorkshopSpec(cur.customTheme).copy(backgroundUrl = clean)
                settingsRepo.setCustomTheme(
                    kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(WorkshopThemeSpec.serializer(), spec),
                )
            }
            _events.tryEmit(
                SettingsEvent.Message(
                    appContext.getString(if (clean.isBlank()) app.votify.mobile.R.string.toast_bg_cleared else app.votify.mobile.R.string.toast_bg_applied),
                ),
            )
        }
    }

    /**
     * Manual interface colors (hex strings) over the applied background — or a standalone
     * custom palette when no background is set. Only the provided keys are overridden.
     */
    fun setCustomColors(colors: Map<String, String>) {
        viewModelScope.launch {
            val cur = settingsRepo.settings.first()
            val base = if (cur.customTheme.isNotBlank()) parseWorkshopSpec(cur.customTheme) else defaultSpec(cur.theme)
            val spec = colors.entries.fold(base) { acc, (k, v) ->
                when (k) {
                    "primary" -> acc.copy(primary = v)
                    "background" -> acc.copy(background = v)
                    "text" -> acc.copy(text = v)
                    "cards" -> acc.copy(cards = v)
                    "borders" -> acc.copy(borders = v)
                    else -> acc
                }
            }
            settingsRepo.setCustomTheme(
                kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(WorkshopThemeSpec.serializer(), spec),
            )
            settingsRepo.setTheme(AppTheme.Workshop)
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.colors_applied)))
        }
    }

    /** Quick palette swatch — swaps the palette; the background lives on its own and stays. */
    fun applyPalettePreset(preset: AppTheme) {
        viewModelScope.launch {
            settingsRepo.setTheme(preset)
            settingsRepo.setCustomTheme("")
            _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.colors_applied)))
        }
    }

    /** Copies a picked image into filesDir/backgrounds and adds it to the library. */
    fun importBackgroundFile(uri: android.net.Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(appContext.filesDir, "backgrounds").apply { mkdirs() }
                    val ext = appContext.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
                    val file = java.io.File(dir, "bg_${System.currentTimeMillis()}.$ext")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    file.absolutePath
                }.getOrNull()
            }
            if (saved == null) {
                _events.tryEmit(SettingsEvent.Message(appContext.getString(app.votify.mobile.R.string.bg_import_failed)))
            } else {
                addBackground(saved)
            }
        }
    }

    private fun defaultSpec(theme: AppTheme) = when (theme) {
        AppTheme.Graphite -> GraphitePalette
        AppTheme.Violet -> VioletPalette
        AppTheme.Azure -> AzurePalette
        AppTheme.Emerald -> EmeraldPalette
        AppTheme.Amber -> AmberPalette
        AppTheme.Rose -> RosePalette
        else -> OledBlackPalette
    }.let {
        WorkshopThemeSpec(
            primary = "#%06X".format(it.primary.toArgbCompat() and 0xFFFFFF),
            background = "#%06X".format(it.surfaceBase.toArgbCompat() and 0xFFFFFF),
            text = "#%06X".format(it.textPrimary.toArgbCompat() and 0xFFFFFF),
            cards = "#%06X".format(it.surfaceContainer.toArgbCompat() and 0xFFFFFF),
            borders = "#%06X".format(it.borderSubtle.toArgbCompat() and 0xFFFFFF),
            focus = "#%06X".format(it.borderProminent.toArgbCompat() and 0xFFFFFF),
        )
    }

    private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int =
        (255 shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
