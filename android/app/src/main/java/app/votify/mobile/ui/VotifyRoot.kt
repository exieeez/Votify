package app.votify.mobile.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import app.votify.mobile.ui.components.MiniPlayer
import app.votify.mobile.ui.home.HomeScreen
import app.votify.mobile.ui.home.HomeViewModel
import app.votify.mobile.ui.library.LibraryScreen
import app.votify.mobile.ui.player.PlayerScreen
import app.votify.mobile.ui.search.SearchScreen
import app.votify.mobile.ui.search.SearchViewModel
import app.votify.mobile.ui.theme.VotifyColors

private enum class Tab(val route: String, val label: Int, val icon: ImageVector, val iconSelected: ImageVector) {
    Home("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Search("search", R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    Library("library", R.string.nav_library, Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
}

@Composable
fun VotifyRoot() {
    val app = VotifyApp.instance
    val player = app.player
    val playerState by player.state.collectAsStateWithLifecycle()

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

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content must clear: nav bar (72) + mini-player (64 + 8 gap) + gesture inset.
    val hasMini = playerState.current != null
    val bottomClearance = 72.dp + (if (hasMini) 72.dp else 0.dp) + navBarInset
    val contentPadding = PaddingValues(top = statusBar.calculateTopPadding(), bottom = bottomClearance)

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
                            onClick = { playerExpanded = true },
                            onPlayPause = player::togglePlayPause,
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
                        onPlay = { tracks, i -> player.play(tracks, i) },
                        onOpenSearch = { navController.navigate(Tab.Search.route) { launchSingleTop = true } },
                    )
                }
                composable(Tab.Search.route) {
                    SearchScreen(
                        viewModel = searchVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onPlay = { tracks, i -> player.play(tracks, i) },
                    )
                }
                composable(Tab.Library.route) {
                    LibraryScreen(contentPadding = contentPadding)
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
                onCollapse = { playerExpanded = false },
                onPlayPause = player::togglePlayPause,
                onNext = { player.next() },
                onPrevious = player::previous,
                onSeek = player::seekTo,
                onToggleShuffle = player::toggleShuffle,
                onCycleRepeat = player::cycleRepeat,
            )
        }
    }
}

/** 72dp nav bar: #121212 trough, white pill indicator behind the active icon, no labels. */
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

/** Builds our ViewModels with the app-scoped API client. */
private class AppViewModelFactory(private val app: VotifyApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app.api) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(app.api) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
