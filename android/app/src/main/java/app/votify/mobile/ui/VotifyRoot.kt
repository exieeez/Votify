package app.votify.mobile.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import app.votify.mobile.data.AudioQuality
import app.votify.mobile.data.Settings
import app.votify.mobile.data.Track
import app.votify.mobile.ui.artist.ArtistScreen
import app.votify.mobile.ui.artist.ArtistViewModel
import app.votify.mobile.ui.components.MiniPlayer
import app.votify.mobile.ui.components.PlaylistPickerSheet
import app.votify.mobile.ui.components.QueueSheet
import app.votify.mobile.ui.components.TrackMenuSheet
import app.votify.mobile.ui.home.HomeScreen
import app.votify.mobile.ui.home.HomeViewModel
import app.votify.mobile.ui.library.FavoritesScreen
import app.votify.mobile.ui.library.HistoryScreen
import app.votify.mobile.ui.library.LibraryScreen
import app.votify.mobile.ui.library.LibraryViewModel
import app.votify.mobile.ui.library.PlaylistScreen
import app.votify.mobile.ui.player.PlayerScreen
import app.votify.mobile.ui.player.PlayerViewModel
import app.votify.mobile.ui.search.SearchScreen
import app.votify.mobile.ui.search.SearchViewModel
import app.votify.mobile.ui.settings.SettingsEvent
import app.votify.mobile.ui.settings.SettingsScreen
import app.votify.mobile.ui.settings.SettingsViewModel
import app.votify.mobile.ui.theme.VotifyColors
import app.votify.mobile.ui.theme.VotifyTheme

private enum class Tab(val route: String, val label: Int, val icon: ImageVector, val iconSelected: ImageVector) {
    Home("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Search("search", R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    Library("library", R.string.nav_library, Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
}

private object Routes {
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{id}"
    const val ARTIST = "artist/{name}"

    fun playlist(id: Long) = "playlist/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
}

@Composable
fun VotifyRoot() {
    val app = VotifyApp.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())

    VotifyTheme(theme = settings.theme) {
        VotifyScaffold(app = app, settings = settings)
    }
}

@Composable
private fun VotifyScaffold(app: VotifyApp, settings: Settings) {
    val player = app.player
    val playerState by player.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    var playerExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playerState.error) {
        playerState.error?.let {
            snackbar.showSnackbar(it)
            player.clearError()
        }
    }

    val factory = remember { AppViewModelFactory(app) }
    val homeVm: HomeViewModel = viewModel(factory = factory)
    val searchVm: SearchViewModel = viewModel(factory = factory)
    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playerVm: PlayerViewModel = viewModel(factory = factory)
    val artistVm: ArtistViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

    val favoriteIds by libraryVm.favoriteIds.collectAsStateWithLifecycle()
    val currentId = playerState.current?.id
    val currentIsFavorite = currentId != null && favoriteIds.contains(currentId)
    val playlists by libraryVm.playlists.collectAsStateWithLifecycle()
    val menu by libraryVm.menu.collectAsStateWithLifecycle()
    val picker by libraryVm.playlistPicker.collectAsStateWithLifecycle()
    val queueOpen by libraryVm.queueOpen.collectAsStateWithLifecycle()
    val lyricsVisible by playerVm.lyricsVisible.collectAsStateWithLifecycle()
    val lyrics by playerVm.lyrics.collectAsStateWithLifecycle()

    // Snackbar messages from library actions (resolved to localized text here).
    LaunchedEffect(libraryVm) {
        libraryVm.messages.collect { m ->
            snackbar.showSnackbar(context.getString(m.id, *m.args.toTypedArray()))
        }
    }
    LaunchedEffect(settingsVm) {
        settingsVm.events.collect { e ->
            when (e) {
                is SettingsEvent.QualitySaved -> snackbar.showSnackbar(
                    context.getString(
                        R.string.toast_quality_saved,
                        context.getString(
                            when (e.quality) {
                                AudioQuality.Low -> R.string.settings_quality_low
                                AudioQuality.Medium -> R.string.settings_quality_medium
                                AudioQuality.High -> R.string.settings_quality_high
                            },
                        ),
                    ),
                )
                SettingsEvent.QualityFailed -> snackbar.showSnackbar(context.getString(R.string.toast_quality_failed))
            }
        }
    }

    // "Show lyrics over artwork" preference: open the player straight into lyrics mode.
    LaunchedEffect(playerExpanded, settings.showLyricsOverArtwork) {
        if (playerExpanded && settings.showLyricsOverArtwork && !playerVm.lyricsVisible.value) playerVm.toggleLyrics()
    }

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content must clear: nav bar (72) + mini-player (64 + 8 gap) + gesture inset.
    val hasMini = playerState.current != null
    val bottomClearance = 72.dp + (if (hasMini) 72.dp else 0.dp) + navBarInset
    val contentPadding = PaddingValues(top = statusBar.calculateTopPadding(), bottom = bottomClearance)

    val play: (List<Track>, Int) -> Unit = { tracks, i -> player.play(tracks, i) }
    val openArtist: (String) -> Unit = { name ->
        playerExpanded = false
        libraryVm.closeMenu()
        navController.navigate(Routes.artist(name)) { launchSingleTop = true }
    }
    val openMenu: (Track) -> Unit = { libraryVm.openMenu(it) }

    Box(Modifier.fillMaxSize().background(VotifyColors.SurfaceBase)) {
        Scaffold(
            containerColor = VotifyColors.SurfaceBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = hasMini, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        MiniPlayer(
                            state = playerState,
                            isFavorite = currentIsFavorite,
                            onClick = { playerExpanded = true },
                            onPlayPause = player::togglePlayPause,
                            onToggleFavorite = { playerState.current?.let(libraryVm::toggleFavorite) },
                            onSwipeLeft = { if (settings.miniPlayerSwipeChangesTrack) player.next() else libraryVm.openQueue() },
                            onSwipeRight = { if (settings.miniPlayerSwipeChangesTrack) player.previous() else libraryVm.openQueue() },
                        )
                    }
                    if (hasMini) Spacer(Modifier.height(8.dp))
                    VotifyNavBar(
                        selected = Tab.entries.firstOrNull { t -> currentDestination?.hierarchy?.any { it.route == t.route } == true } ?: Tab.Home,
                        onSelect = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { _ ->
            NavHost(navController, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize()) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        viewModel = homeVm,
                        contentPadding = contentPadding,
                        onPlay = play,
                        onOpenSearch = { navController.navigate(Tab.Search.route) { launchSingleTop = true } },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                        onOpenFavorites = { navController.navigate(Routes.FAVORITES) { launchSingleTop = true } },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(Tab.Search.route) {
                    SearchScreen(
                        viewModel = searchVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onPlay = play,
                        onMore = openMenu,
                    )
                }
                composable(Tab.Library.route) {
                    LibraryScreen(
                        viewModel = libraryVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onPlay = play,
                        onOpenFavorites = { navController.navigate(Routes.FAVORITES) { launchSingleTop = true } },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) { launchSingleTop = true } },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        viewModel = libraryVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPlay = play,
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        viewModel = libraryVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPlay = play,
                    )
                }
                composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: return@composable
                    PlaylistScreen(
                        viewModel = libraryVm,
                        playlistId = id,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPlay = play,
                    )
                }
                composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                    val name = entry.arguments?.getString("name").orEmpty()
                    ArtistScreen(
                        viewModel = artistVm,
                        name = name,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPlay = play,
                        onMore = openMenu,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = settingsVm,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerExpanded && playerState.current != null,
            enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerScreen(
                state = playerState,
                artworkStyle = settings.artworkStyle,
                isFavorite = currentIsFavorite,
                lyricsVisible = lyricsVisible,
                lyrics = lyrics,
                onCollapse = { playerExpanded = false },
                onPlayPause = player::togglePlayPause,
                onNext = { player.next() },
                onPrevious = player::previous,
                onSeek = player::seekTo,
                onSeekToMs = player::seekToMs,
                onToggleShuffle = player::toggleShuffle,
                onCycleRepeat = player::cycleRepeat,
                onToggleFavorite = { playerState.current?.let(libraryVm::toggleFavorite) },
                onToggleLyrics = playerVm::toggleLyrics,
                onAddToPlaylist = { playerState.current?.let(libraryVm::openPlaylistPicker) },
                onOpenQueue = libraryVm::openQueue,
                onOpenArtist = openArtist,
            )
        }
    }

    // ---- Global sheets ----

    menu?.let { m ->
        TrackMenuSheet(
            track = m.track,
            isFavorite = favoriteIds.contains(m.track.id),
            playlistId = m.playlistId,
            onDismiss = libraryVm::closeMenu,
            onToggleFavorite = {
                libraryVm.toggleFavorite(m.track)
                libraryVm.closeMenu()
            },
            onPlayNext = {
                libraryVm.playNext(m.track)
                libraryVm.closeMenu()
            },
            onAddToQueue = {
                libraryVm.addToQueue(listOf(m.track))
                libraryVm.closeMenu()
            },
            onAddToPlaylist = { libraryVm.openPlaylistPicker(m.track) },
            onRemoveFromPlaylist = {
                m.playlistId?.let { libraryVm.removeFromPlaylist(it, m.track.id) }
                libraryVm.closeMenu()
            },
            onOpenArtist = { openArtist(m.track.artist) },
        )
    }

    picker?.let { track ->
        PlaylistPickerSheet(
            track = track,
            playlists = playlists,
            onDismiss = libraryVm::closePlaylistPicker,
            onPick = { p -> libraryVm.addToPlaylist(p.id, track) },
            onCreate = { name -> libraryVm.createPlaylist(name, thenAdd = track) },
        )
    }

    if (queueOpen) {
        QueueSheet(
            queue = playerState.queue,
            currentIndex = playerState.currentIndex,
            onDismiss = libraryVm::closeQueue,
            onPick = { i ->
                libraryVm.skipToQueueItem(i)
                libraryVm.closeQueue()
            },
        )
    }
}

/** 72dp nav bar: base trough, white pill indicator behind the active icon, no labels. */
@Composable
private fun VotifyNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = VotifyColors.SurfaceBase,
        contentColor = VotifyColors.TextMuted,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.iconSelected else tab.icon, stringResource(tab.label)) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VotifyColors.OnPrimary,
                    unselectedIconColor = VotifyColors.TextMuted,
                    indicatorColor = VotifyColors.Primary,
                ),
            )
        }
    }
}

/** Builds our ViewModels with the app-scoped singletons. */
private class AppViewModelFactory(private val app: VotifyApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app.api, app.library) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(app.api) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(app.library, app.player) as T
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(app.api, app.player, app.library) as T
        modelClass.isAssignableFrom(ArtistViewModel::class.java) -> ArtistViewModel(app.api) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app.api, app.settings, app.library) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
