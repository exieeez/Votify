package app.votify.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WaveSource { Personal, Generic }

data class HomePlaylistItem(
    val id: Long = 0,
    val name: String,
    val subtitle: String,
    val cover: String?,
    val isLocal: Boolean = false,
)

data class HomeArtistItem(
    val name: String,
    val followers: String,
    val avatarUrl: String?,
)

data class HomeReleaseItem(
    val title: String,
    val artist: String,
    val cover: String?,
    val track: Track? = null,
)

data class HomeUiState(
    /** Tracks shown as orbit bubbles around the big Play and used as the wave queue. */
    val wave: List<Track> = emptyList(),
    val forYouTracks: List<Track> = emptyList(),
    val forYouPlaylists: List<HomePlaylistItem> = emptyList(),
    val forYouArtists: List<HomeArtistItem> = emptyList(),
    val forYouReleases: List<HomeReleaseItem> = emptyList(),
    /** Whether the wave was built from the user's history or from generic recommendations. */
    val waveSource: WaveSource = WaveSource.Generic,
    /** The artists the wave was seeded with (for the caption under the hero). */
    val seeds: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
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
        refresh()
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

    /**
     * «Моя волна»: seeded by what the user actually listens to.
     *  - artist seeds  = top artists from the play history (by play count)
     *  - track seeds   = "artist title" of the most recent listens (keeps the wave anchored)
     *  - exclude       = the recent tracks themselves, so the wave brings *new* music
     * Falls back to the generic /api/recommendations when there is no history yet.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
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

            val result = if (personal) {
                runCatching {
                    music.customWave(
                        artistSeeds = artistSeeds,
                        trackSeeds = trackSeeds,
                        exclude = recentTracks.map { it.id },
                        limit = 24,
                    )
                }.recoverCatching { music.recommendations(limit = 20) }
            } else {
                runCatching { music.recommendations(limit = 20) }
            }

            result
                .onSuccess { tracks ->
                    val wave = if (tracks.isEmpty() && personal) {
                        runCatching { music.recommendations(limit = 20) }.getOrDefault(emptyList())
                    } else {
                        tracks
                    }

                    // For You tracks: recommended tracks
                    val forYouTracks = if (wave.size > 8) wave.drop(8) else wave

                    // Playlists: user playlists from DB + curated aesthetic playlists
                    val localPlaylists = runCatching { library.playlists.first() }.getOrDefault(emptyList())
                    val playlistItems = localPlaylists.map { pl ->
                        HomePlaylistItem(
                            id = pl.id,
                            name = pl.name,
                            subtitle = "${pl.trackCount} треков",
                            cover = pl.cover,
                            isLocal = true,
                        )
                    } + curatedPlaylists

                    // Artists: user top artists + underground favorites
                    val userArtists = artistSeeds.map { name ->
                        HomeArtistItem(
                            name = name,
                            followers = "В вашей медиатеке",
                            avatarUrl = wave.firstOrNull { it.artist.contains(name, true) }?.cover,
                        )
                    }
                    val allArtists = (userArtists + curatedArtists).distinctBy { it.name.lowercase() }

                    // Releases: derive from wave tracks or curated
                    val releaseItems = wave.take(6).map { t ->
                        HomeReleaseItem(
                            title = t.title,
                            artist = t.artist,
                            cover = t.cover,
                            track = t,
                        )
                    }.ifEmpty { curatedReleases }

                    _state.update {
                        it.copy(
                            wave = if (personal && wave.isNotEmpty()) wave else wave.shuffled(),
                            forYouTracks = forYouTracks,
                            forYouPlaylists = playlistItems,
                            forYouArtists = allArtists,
                            forYouReleases = releaseItems,
                            waveSource = if (personal && wave.isNotEmpty()) WaveSource.Personal else WaveSource.Generic,
                            seeds = artistSeeds,
                            isLoading = false,
                        )
                    }
                    music.preload(wave.take(2).map { t -> t.id })
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "error",
                            forYouPlaylists = curatedPlaylists,
                            forYouArtists = curatedArtists,
                            forYouReleases = curatedReleases,
                        )
                    }
                }
        }
    }

    fun playSearch(query: String, onPlay: (List<Track>, Int) -> Unit) {
        viewModelScope.launch {
            val tracks = runCatching { music.search(query, limit = 16) }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) {
                onPlay(tracks, 0)
            }
        }
    }

    companion object {
        private val curatedPlaylists = listOf(
            HomePlaylistItem(
                name = "🤍🖤🤍",
                subtitle = "homie",
                cover = "https://i.scdn.co/image/ab67616d0000b2738a0f9b6b801a61c56b7c9360",
            ),
            HomePlaylistItem(
                name = "Магнит",
                subtitle = "курящих нет",
                cover = "https://i.scdn.co/image/ab67616d0000b273d6b0521e1d3550e5033c46e3",
            ),
            HomePlaylistItem(
                name = "жост...",
                subtitle = "Milly",
                cover = "https://i.scdn.co/image/ab67616d0000b27361be526c8b9d31198fb9622d",
            ),
            HomePlaylistItem(
                name = "DARK DRIFT",
                subtitle = "Votify",
                cover = "https://i.scdn.co/image/ab67616d0000b27376c764a2c5b367fc9b03ef88",
            ),
        )

        private val curatedArtists = listOf(
            HomeArtistItem(
                name = "ONDA ANDAR",
                followers = "59.8K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebc1fbf0465a3c1a851bb8d929",
            ),
            HomeArtistItem(
                name = "zhanulka",
                followers = "48.1K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5eb1d279cfbb6e300170a75d506",
            ),
            HomeArtistItem(
                name = "madk1d",
                followers = "72.4K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebb6b95b8d0cbbcfcb92a2a01d",
            ),
            HomeArtistItem(
                name = "ICEGERGERT",
                followers = "125K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebff2dbe2cfa3065a319409893",
            ),
            HomeArtistItem(
                name = "Friendly Thug 52",
                followers = "210K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebcb53aeb3f76023253b27b9ef",
            ),
            HomeArtistItem(
                name = "Toxi$",
                followers = "315K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebd74f514b7410313a96898d9a",
            ),
            HomeArtistItem(
                name = "VILLIAN",
                followers = "38.2K подписчиков",
                avatarUrl = "https://i.scdn.co/image/ab6761610000e5eb6046e7f77f0d0ecceb6e147e",
            ),
        )

        private val curatedReleases = listOf(
            HomeReleaseItem(
                title = "Мало",
                artist = "AVICH",
                cover = "https://i.scdn.co/image/ab67616d0000b273ba95a32ec484dfce047c32e5",
            ),
            HomeReleaseItem(
                title = "БУНКЕР",
                artist = "ktsukoma",
                cover = "https://i.scdn.co/image/ab67616d0000b273f608f654b4231b2682976d8b",
            ),
            HomeReleaseItem(
                title = "VIP",
                artist = "ARTEM SHILOVETS",
                cover = "https://i.scdn.co/image/ab67616d0000b273415cf2aeaf5513fa096dfc8e",
            ),
            HomeReleaseItem(
                title = "HEAVY METAL 2",
                artist = "163ONMYNECK",
                cover = "https://i.scdn.co/image/ab67616d0000b2734e56598586f1eef811559ee5",
            ),
            HomeReleaseItem(
                title = "GLORY HILL",
                artist = "Bushido Zho",
                cover = "https://i.scdn.co/image/ab67616d0000b273ef3ec3a06ad3776602c114f0",
            ),
        )
    }
}
