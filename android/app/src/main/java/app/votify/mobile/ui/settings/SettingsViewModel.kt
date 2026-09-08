package app.votify.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.AudioQuality
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Settings
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.VotifyApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServerStatus { Unknown, Checking, Online, Offline }

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
}

class SettingsViewModel(
    private val api: VotifyApi,
    private val settingsRepo: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val favoriteCount: StateFlow<Int> =
        library.favoriteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val historyCount: StateFlow<Int> =
        library.historyCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events

    val apiBase: String = api.baseUrl.toString().trimEnd('/')

    init {
        syncQualityFromServer()
        checkServer()
    }

    /** GET /api/network/settings — the server is the source of truth for stream quality. */
    fun syncQualityFromServer() {
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

    /** POST /api/network/settings { audioQuality } and mirror the result locally. */
    fun setAudioQuality(q: AudioQuality) {
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
        viewModelScope.launch {
            _state.update { it.copy(server = ServerStatus.Checking) }
            runCatching { api.health() }
                .onSuccess { h ->
                    _state.update { it.copy(server = if (h.ok) ServerStatus.Online else ServerStatus.Offline, serverName = h.name, serverVersion = h.version) }
                }
                .onFailure { _state.update { it.copy(server = ServerStatus.Offline) } }
        }
    }

    fun setTheme(v: AppTheme) = launchSave { settingsRepo.setTheme(v) }
    fun setArtworkStyle(v: ArtworkStyle) = launchSave { settingsRepo.setArtworkStyle(v) }
    fun setShowLyricsOverArtwork(v: Boolean) = launchSave { settingsRepo.setShowLyricsOverArtwork(v) }
    fun setMiniPlayerSwipeChangesTrack(v: Boolean) = launchSave { settingsRepo.setMiniPlayerSwipeChangesTrack(v) }
    fun setVolumeButtonsSkip(v: Boolean) = launchSave { settingsRepo.setVolumeButtonsSkip(v) }

    fun clearHistory() = launchSave { library.clearHistory() }

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
