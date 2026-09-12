package app.votify.mobile.ui.home

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.SettingsRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.WaveLang
import app.votify.mobile.data.WaveMode
import app.votify.mobile.data.WaveStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
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
    /** Wave hero style from Settings → Interface (pure UI, no reload). */
    val waveStyle: WaveStyle = WaveStyle.Orbit,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Distinct recently played tracks — drives the «Недавние» card. */
    val recent: StateFlow<List<Track>> =
        library.recent(limit = 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteCount: StateFlow<Int> =
        library.favoriteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Цвет шара «Моей волны» в стиле matugen: средний цвет обоев фона,
     * усиленный до сочного оттенка. null = обоев нет, шар красится
     * в цвет поверхности темы.
     */
    val orbColor: StateFlow<Color?> = settingsRepo.settings
        .map { it.backgroundUrl.ifBlank { null } }
        .distinctUntilChanged()
        .mapLatest { url -> if (url == null) null else sampleBackgroundColor(appContext, url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Текст текущей песни — одна строка под «Моей волной». */
    private val _lyrics = MutableStateFlow<app.votify.mobile.data.Lyrics?>(null)
    val lyrics: StateFlow<app.votify.mobile.data.Lyrics?> = _lyrics

    init {
        // First emission carries the persisted mode/lang (initial load);
        // later mode/lang changes reload the wave, a style change only restyles it.
        viewModelScope.launch {
            var first = true
            settingsRepo.settings.collect { s ->
                val st = _state.value
                val tuneChanged = first || s.waveMode != st.waveMode || s.waveLang != st.waveLang
                if (tuneChanged || s.waveStyle != st.waveStyle) {
                    _state.update { it.copy(waveMode = s.waveMode, waveLang = s.waveLang, waveStyle = s.waveStyle) }
                    if (tuneChanged) {
                        first = false
                        loadWave(s.waveMode, s.waveLang)
                    }
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

/**
 * Matugen-lite: грузим обои через Coil, берём средний цвет 8×8 и делаем его
 * сочным (насыщенность минимум 0.6, яркость 0.45–0.7 — белый текст читается).
 * Любая неудача (нет сети, битый файл, GIF) = null, шар красится темой.
 */
private suspend fun sampleBackgroundColor(context: Context, url: String): Color? =
    withContext(Dispatchers.IO) {
        runCatching {
            val loader = coil.ImageLoader(context)
            val req = coil.request.ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val bmp = (loader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)
                ?.bitmap ?: return@runCatching null
            val small = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 8, true)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            for (x in 0 until 8) for (y in 0 until 8) {
                val px = small.getPixel(x, y)
                if (android.graphics.Color.alpha(px) < 128) continue
                r += android.graphics.Color.red(px)
                g += android.graphics.Color.green(px)
                b += android.graphics.Color.blue(px)
                n++
            }
            if (n == 0L) return@runCatching null
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(
                android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()),
                hsv,
            )
            hsv[1] = maxOf(hsv[1], 0.6f)
            hsv[2] = hsv[2].coerceIn(0.45f, 0.7f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }.getOrDefault(null)
    }
