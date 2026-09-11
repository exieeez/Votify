package app.votify.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.WaveLang
import app.votify.mobile.data.WaveMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WaveSource { Personal, Generic, Popular }

data class HomeUiState(
    /** Tracks of the current wave tab — the queue started by the wave card. */
    val wave: List<Track> = emptyList(),
    /** Whether the wave was built from the user's history or from generic recommendations. */
    val waveSource: WaveSource = WaveSource.Generic,
    /** The artists the wave was seeded with (for the caption under the hero). */
    val seeds: List<String> = emptyList(),
    /** Active wave pill (persisted — the screen reopens on the same tab). */
    val waveMode: WaveMode = WaveMode.ForYou,
    /** Wave language from «Настроить» (default = Ukrainian). */
    val waveLang: WaveLang = WaveLang.Ukrainian,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Distinct recently played tracks — drives the «Недавние» card. */
    val recent: StateFlow<List<Track>> =
        library.recent(limit = 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteCount: StateFlow<Int> =
        library.favoriteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Текст текущей песни — одна строка под «Моей волной». */
    private val _lyrics = MutableStateFlow<app.votify.mobile.data.Lyrics?>(null)
    val lyrics: StateFlow<app.votify.mobile.data.Lyrics?> = _lyrics

    init {
        // First emission carries the persisted mode/lang (initial load);
        // later ones mean the user retuned the wave in «Настроить» — reload it.
        viewModelScope.launch {
            var first = true
            settingsRepo.settings.collect { s ->
                val st = _state.value
                if (first || s.waveMode != st.waveMode || s.waveLang != st.waveLang) {
                    first = false
                    _state.update { it.copy(waveMode = s.waveMode, waveLang = s.waveLang) }
                    loadWave(s.waveMode, s.waveLang)
                }
            }
        }
    }

    /** Грузим текст песни для строки на главной; неудача — просто пустая строка. */
    fun loadLyrics(track: Track?) {
        viewModelScope.launch {
            _lyrics.value = null
            if (track == null) return@launch
            _lyrics.value = runCatching { music.lyrics(track.title, track.artist) }
                .getOrNull()
                ?.let { app.votify.mobile.data.Lyrics.from(it) }
        }
    }

    /** Таблеточки под карточкой волны: режим сохраняется — экран откроется на нём же. */
    fun setMode(mode: WaveMode) {
        if (mode == _state.value.waveMode) return
        viewModelScope.launch { settingsRepo.setWaveMode(mode) }
        // Перезагрузка приедет сама через collect выше.
    }

    fun refresh() {
        val st = _state.value
        loadWave(st.waveMode, st.waveLang)
    }

    private fun loadWave(mode: WaveMode, lang: WaveLang) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            if (mode == WaveMode.Popular) {
                loadPopular()
            } else {
                loadForYou(lang)
            }
        }
    }

    /** «Популярные»: мировые хиты + проверенные оригиналы топ-артистов. */
    private suspend fun loadPopular() {
        val tracks = runCatching { music.popular(limit = 24) }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                wave = tracks,
                waveSource = WaveSource.Popular,
                seeds = emptyList(),
                isLoading = false,
                error = null,
            )
        }
        music.preload(tracks.take(2).map { t -> t.id })
    }

    /**
     * «Для вас»: seeded by what the user actually listens to.
     *  - artist seeds  = top artists from the play history (by play count)
     *  - track seeds   = "artist title" of the most recent listens (keeps the wave anchored)
     *  - exclude       = the recent tracks themselves, so the wave brings *new* music
     * Falls back to the language-aware recommendations when there is no history yet.
     */
    private suspend fun loadForYou(lang: WaveLang) {
        val settings = runCatching { settingsRepo.settings.first() }.getOrNull()
        val excludeListened = settings?.waveExcludeListened ?: true
        val recentTracks = runCatching { library.recent(limit = 30).first() }.getOrDefault(emptyList())
        val playlistArtists = runCatching { library.playlistArtists(limit = 8) }.getOrDefault(emptyList())
        // Wave seeds: what the user PLAYS (history) + what they COLLECT (playlists, favorites).
        val artistSeeds = (runCatching { library.topArtists(limit = 6) }.getOrDefault(emptyList()) + playlistArtists)
            .distinct()
            .take(6)
        val favorites = runCatching { library.favorites.first() }.getOrDefault(emptyList())

        // Якоря — то, что человек реально слушал/любит: по ним ищем похожее.
        val trackSeeds = (favorites.take(4) + recentTracks.take(8))
            .distinctBy { it.id }
            .map { "${it.artist} ${it.title}".trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)

        val personal = artistSeeds.isNotEmpty() || trackSeeds.isNotEmpty()
        val exclude = if (excludeListened) recentTracks.map { it.id } else emptyList()

        val result = if (personal) {
            runCatching {
                music.customWave(
                    artistSeeds = artistSeeds,
                    trackSeeds = trackSeeds,
                    exclude = exclude,
                    limit = 24,
                    lang = lang,
                )
            }.recoverCatching { music.recommendations(limit = 20, lang = lang) }
        } else {
            runCatching { music.recommendations(limit = 20, lang = lang) }
        }

        result
            .onSuccess { tracks ->
                val wave = if (tracks.isEmpty() && personal) {
                    runCatching { music.recommendations(limit = 20, lang = lang) }.getOrDefault(emptyList())
                } else {
                    tracks
                }
                _state.update {
                    it.copy(
                        // Персональную волну не перемешиваем: она уже отранжирована
                        // по похожести. Общие рекомендации мешаем — там порядок случаен.
                        wave = if (personal && wave.isNotEmpty()) wave else wave.shuffled(),
                        waveSource = if (personal && wave.isNotEmpty()) WaveSource.Personal else WaveSource.Generic,
                        seeds = artistSeeds,
                        isLoading = false,
                    )
                }
                music.preload(wave.take(2).map { t -> t.id })
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "error") }
            }
    }
}
