package app.votify.mobile.ui

import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.launch
import android.content.Intent
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import app.votify.mobile.data.AudioQuality
import app.votify.mobile.data.Settings
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.Track
import app.votify.mobile.ui.account.AccountEvent
import app.votify.mobile.ui.account.WelcomeOverlay
import app.votify.mobile.ui.account.AccountScreen
import app.votify.mobile.ui.account.AccountViewModel
import app.votify.mobile.ui.account.EditProfileScreen
import app.votify.mobile.ui.account.ProfileScreen
import app.votify.mobile.ui.account.ProfileViewModel
import app.votify.mobile.ui.artist.ArtistScreen
import app.votify.mobile.ui.artist.ArtistViewModel
import app.votify.mobile.ui.components.MiniPlayer
import app.votify.mobile.ui.components.MiniStyle
import app.votify.mobile.ui.components.PlaylistPickerSheet
import app.votify.mobile.ui.components.QueueSheet
import app.votify.mobile.ui.components.TrackMenuSheet
import app.votify.mobile.ui.home.HomeScreen
import app.votify.mobile.ui.home.HomeViewModel
import app.votify.mobile.ui.library.FavoritesScreen
import app.votify.mobile.ui.library.HistoryScreen
import app.votify.mobile.ui.library.ImportEvent
import app.votify.mobile.ui.library.ImportScreen
import app.votify.mobile.ui.library.ImportViewModel
import app.votify.mobile.ui.library.LibraryScreen
import app.votify.mobile.ui.library.LibraryViewModel
import app.votify.mobile.ui.library.PlaylistScreen
import app.votify.mobile.ui.player.PlayerScreen
import app.votify.mobile.ui.player.PlayerVisuals
import app.votify.mobile.ui.player.PlayerViewModel
import app.votify.mobile.ui.search.SearchScreen
import app.votify.mobile.ui.search.SearchViewModel
import app.votify.mobile.ui.trending.TrendingScreen
import app.votify.mobile.ui.trending.TrendingViewModel
import app.votify.mobile.ui.workshop.WorkshopEvent
import app.votify.mobile.ui.workshop.WorkshopScreen
import app.votify.mobile.ui.workshop.WorkshopViewModel
import app.votify.mobile.ui.settings.SettingsEvent
import app.votify.mobile.ui.settings.SettingsScreen
import app.votify.mobile.ui.settings.ArtworkSettingsScreen
import app.votify.mobile.ui.settings.AudioSettingsScreen
import app.votify.mobile.ui.settings.BackgroundsScreen
import app.votify.mobile.ui.settings.GeneralSettingsScreen
import app.votify.mobile.ui.settings.InterfaceSettingsScreen
import app.votify.mobile.ui.settings.PlayerSettingsScreen
import app.votify.mobile.ui.settings.PresetsScreen
import app.votify.mobile.ui.settings.ProxySettingsScreen
import app.votify.mobile.ui.settings.StorageSettingsScreen
import app.votify.mobile.ui.settings.SwipeSettingsScreen
import app.votify.mobile.ui.settings.SettingsViewModel
import app.votify.mobile.data.parseCustomPrefs
import app.votify.mobile.ui.theme.VotifyColors
import app.votify.mobile.ui.theme.parseWorkshopSpec
import app.votify.mobile.ui.theme.VotifyTheme

/** Dotify-style nav icons (design/screens/home.html): rounded house, magnifier, folder. */
private enum class Tab(val route: String, val label: Int, val icon: Int, val iconSelected: Int) {
    Home("home", R.string.nav_home, R.drawable.ic_nav_home_outline, R.drawable.ic_nav_home_filled),
    Search("search", R.string.nav_search, R.drawable.ic_nav_search_outline, R.drawable.ic_nav_search_filled),
    Library("library", R.string.nav_library, R.drawable.ic_nav_library_outline, R.drawable.ic_nav_library_filled),
}

private object Routes {
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val PROFILE_EDIT = "account/edit"
    const val USER = "user/{id}"
    const val IMPORT = "import"
    const val PLAYLIST = "playlist/{id}"
    const val ARTIST = "artist/{name}"
    const val TRENDING = "trending"
    const val WORKSHOP = "workshop"
    const val GENERAL = "settings/general"
    const val AUDIO = "settings/audio"
    const val STORAGE = "settings/storage"
    const val SWIPES = "settings/swipes"
    const val INTERFACE = "settings/interface"
    const val PLAYER = "settings/player"
    const val ARTWORK = "settings/artwork"
    const val BACKGROUNDS = "settings/backgrounds"
    const val PRESETS = "settings/presets"
    const val PROXY = "settings/proxy"

    fun playlist(id: Long) = "playlist/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun user(id: String) = "user/${Uri.encode(id)}"
}

/** Сайт проекта — открывается по логотипу в шапке главной. */
private const val VOTIFY_SITE = "https://votify-gamma.vercel.app/"

private fun openSite(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(VOTIFY_SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun VotifyRoot() {
    val app = VotifyApp.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())

    VotifyTheme(theme = settings.theme, customThemeJson = settings.customTheme, prefs = parseCustomPrefs(settings.customPrefs)) {
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
    val accountVm: AccountViewModel = viewModel(factory = factory)
    val importVm: ImportViewModel = viewModel(factory = factory)
    val trendingVm: TrendingViewModel = viewModel(factory = factory)
    val workshopVm: WorkshopViewModel = viewModel(factory = factory)

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
                is SettingsEvent.ServerSaved ->
                    if (e.url.isEmpty()) snackbar.showSnackbar(context.getString(R.string.toast_server_reset))
                    else snackbar.showSnackbar(context.getString(R.string.toast_server_saved, e.url))
                SettingsEvent.ServerInvalid -> snackbar.showSnackbar(context.getString(R.string.toast_server_invalid))
                SettingsEvent.LoggedOut -> snackbar.showSnackbar(context.getString(R.string.toast_logged_out))
                is SettingsEvent.Message -> snackbar.showSnackbar(e.text)
                is SettingsEvent.PresetApplied -> snackbar.showSnackbar(context.getString(R.string.preset_applied, e.name))
            }
        }
    }

    // Account: welcome snackbar after login/registration/password reset.
    LaunchedEffect(accountVm) {
        accountVm.events.collect { e ->
            when (e) {
                is AccountEvent.LoggedIn -> {
                    snackbar.showSnackbar(context.getString(R.string.toast_account_login, e.displayName))
                    if (currentDestination?.route == Routes.ACCOUNT) navController.popBackStack()
                }
                AccountEvent.LoggedOut -> snackbar.showSnackbar(context.getString(R.string.toast_logged_out))
                AccountEvent.DataRestored -> snackbar.showSnackbar(context.getString(R.string.sync_auto_restored))
                AccountEvent.DataSaved -> snackbar.showSnackbar(context.getString(R.string.sync_auto_saved))
            }
        }
    }

    // Workshop: snackbars for applied/published themes and Firebase config state.
    LaunchedEffect(workshopVm) {
        workshopVm.events.collect { e ->
            when (e) {
                is WorkshopEvent.Applied -> snackbar.showSnackbar(context.getString(R.string.toast_theme_applied, e.name))
                is WorkshopEvent.Published -> snackbar.showSnackbar(context.getString(R.string.toast_theme_published, e.title))
                is WorkshopEvent.ConfigSaved -> snackbar.showSnackbar(
                    context.getString(
                        if (e.fromServer) R.string.toast_firebase_from_server else R.string.toast_firebase_saved,
                    ),
                )
                WorkshopEvent.ConfigInvalid -> snackbar.showSnackbar(context.getString(R.string.toast_firebase_invalid))
                WorkshopEvent.BackgroundSet -> snackbar.showSnackbar(context.getString(R.string.toast_bg_applied))
                WorkshopEvent.BackgroundCleared -> snackbar.showSnackbar(context.getString(R.string.toast_bg_cleared))
                WorkshopEvent.BadUrl -> snackbar.showSnackbar(context.getString(R.string.toast_bg_bad_url))
                WorkshopEvent.BadColor -> snackbar.showSnackbar(context.getString(R.string.toast_accent_invalid))
                is WorkshopEvent.Message -> snackbar.showSnackbar(e.text)
                WorkshopEvent.Deleted -> snackbar.showSnackbar(context.getString(R.string.workshop_deleted))
                WorkshopEvent.Unlinked -> snackbar.showSnackbar(context.getString(R.string.workshop_unlinked))
            }
        }
    }

    // Import: snackbar + open the freshly created playlist.
    LaunchedEffect(importVm) {
        importVm.events.collect { e ->
            when (e) {
                is ImportEvent.Imported -> {
                    snackbar.showSnackbar(context.getString(R.string.toast_imported, e.count, e.total, e.name))
                    navController.navigate(Routes.playlist(e.playlistId)) { launchSingleTop = true }
                }
            }
        }
    }

    /** Android share sheet: track title + artist and a YouTube/origin link. */
    val shareTrack: (Track) -> Unit = { track ->
        val link = when {
            track.id.length == 11 && !track.id.startsWith("sc_") -> "https://youtu.be/${track.id}"
            track.url.isNotBlank() -> track.url
            else -> ""
        }
        val text = buildString {
            append(track.title)
            if (track.artist.isNotBlank() && track.artist != "Unknown") append(" — ").append(track.artist)
            if (link.isNotBlank()) append("\n").append(link)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)))
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

    // Profile screens resolve their own messages and hand plain text to the shared snackbar.
    val uiScope = rememberCoroutineScope()
    val showMsg: (String) -> Unit = { m -> uiScope.launch { snackbar.showSnackbar(m) } }

    // App background: any image URL (incl. animated GIF/WebP) behind the whole app.
    // Independent of the theme — changing any setting must never wipe the background.
    // (Legacy fallback: backgrounds used to live inside the workshop spec only.)
    val bgSpec = parseWorkshopSpec(settings.customTheme)
    val workshopBgUrl = settings.backgroundUrl.ifBlank {
        if (settings.theme == AppTheme.Workshop) bgSpec.backgroundUrl else ""
    }
    // Локальный кэш фона: ссылки из Discord протухают за ~сутки — показываем
    // сохранённый файл, который переживает и смерть ссылки, и переустановку.
    var bgCachedFile by remember(workshopBgUrl) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(workshopBgUrl) {
        bgCachedFile = if (workshopBgUrl.isBlank()) null
        else app.votify.mobile.data.BackgroundCache.cachedFile(app, workshopBgUrl)
            ?: app.votify.mobile.data.BackgroundCache.ensureCached(app, workshopBgUrl)
        app.votify.mobile.data.BackgroundCache.prune(app, workshopBgUrl)
    }
    val bgModel: Any? = bgCachedFile ?: workshopBgUrl.ifBlank { null }
    // Fine-tuning: Настройки → Фон («затемнение»/«размытие», prefs.bgDim/bgBlur).
    // Defaults (dim 35, blur 0) match the previous hard-coded look; the workshop
    // spec sliders were the only tuning before and are now overridden here.
    val bgPrefs = parseCustomPrefs(settings.customPrefs)
    val bgBlurDp = bgPrefs.bgBlur.coerceIn(0, 60)
    val bgDimAlpha = bgPrefs.bgDim.coerceIn(0, 92) / 100f
    // Как широкий ПК-фон ложится на экран телефона: обрезать по экрану, показать
    // целиком (с полями) или растянуть — выбирается в Настройки → Библиотека → фон.
    val bgScale = bgPrefs.bgScale.coerceIn(1f, 5f)
    val bgContentScale = when (bgPrefs.bgFit) {
        1 -> androidx.compose.ui.layout.ContentScale.Fit
        2 -> androidx.compose.ui.layout.ContentScale.FillBounds
        else -> androidx.compose.ui.layout.ContentScale.Crop
    }

    // imePadding: with edge-to-edge the keyboard would cover the bottom nav — on the search
    // screen that made it impossible to return to Home without the system back gesture.
    Box(Modifier.fillMaxSize().imePadding().background(VotifyColors.SurfaceBase)) {
        if (bgModel != null) {
            coil.compose.AsyncImage(
                model = bgModel,
                contentDescription = null,
                contentScale = bgContentScale,
                // Кадрирование: широкий ПК-фон можно сдвинуть и приблизить под экран телефона.
                alignment = androidx.compose.ui.BiasAlignment(
                    bgPrefs.bgOffsetX.coerceIn(-1f, 1f),
                    bgPrefs.bgOffsetY.coerceIn(-1f, 1f),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .scale(bgScale)
                    .blur(bgBlurDp.dp),
            )
            // Dim the background so text on cards stays readable (Настройки → Фон → Затемнение).
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = bgDimAlpha),
                ),
            )
        }
        Scaffold(
            containerColor = if (bgModel != null) androidx.compose.ui.graphics.Color.Transparent else VotifyColors.SurfaceBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = hasMini, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        val cp = parseCustomPrefs(settings.customPrefs)
                        MiniPlayer(
                            state = playerState,
                            isFavorite = currentIsFavorite,
                            onClick = { playerExpanded = true },
                            onPlayPause = player::togglePlayPause,
                            onToggleFavorite = { playerState.current?.let(libraryVm::toggleFavorite) },
                            onSwipeLeft = {
                                when (cp.miniSwipeLeft) {
                                    "previous" -> player.previous()
                                    "queue" -> libraryVm.openQueue()
                                    else -> player.next()
                                }
                            },
                            onSwipeRight = {
                                when (cp.miniSwipeRight) {
                                    "next" -> player.next()
                                    "queue" -> libraryVm.openQueue()
                                    else -> player.previous()
                                }
                            },
                            style = MiniStyle(
                                pillShape = cp.miniCorners != "rounded",
                                roundCover = cp.miniCoverShape == "circle",
                                ringProgress = cp.miniProgress == "ring",
                                barProgress = cp.miniProgress == "bar",
                                showLike = cp.miniButtons == "both",
                                filledPlay = cp.miniButtonStyle != "outline",
                                artworkTint = cp.miniBg == "artwork",
                            ),
                        )
                    }
                    if (hasMini) Spacer(Modifier.height(8.dp))
                    VotifyNavBar(
                        selected = Tab.entries.firstOrNull { t -> currentDestination?.hierarchy?.any { it.route == t.route } == true } ?: Tab.Home,
                        onSelect = { tab ->
                            // No saveState/restoreState: a saved stack could contain pushed
                            // settings screens, and tapping the tab would drop the user right
                            // back into them. Always return to the clean tab root instead.
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                                restoreState = false
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
                        playerState = playerState,
                        onSeek = player::seekTo,
                        iosSlider = parseCustomPrefs(settings.customPrefs).sliderStyle == "ios",
                        onOpenSearch = { navController.navigate(Tab.Search.route) { launchSingleTop = true } },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                        onOpenFavorites = { navController.navigate(Routes.FAVORITES) { launchSingleTop = true } },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onOpenAccount = { navController.navigate(Routes.ACCOUNT) { launchSingleTop = true } },
                        onOpenTrending = { navController.navigate(Routes.TRENDING) { launchSingleTop = true } },
                        onOpenSite = { openSite(context) },
                    )
                }
                composable(Tab.Search.route) {
                    SearchScreen(
                        viewModel = searchVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onPlay = play,
                        onMore = openMenu,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
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
                        onImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
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
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onOpenGeneral = { navController.navigate(Routes.GENERAL) { launchSingleTop = true } },
                        onOpenAudio = { navController.navigate(Routes.AUDIO) { launchSingleTop = true } },
                        onOpenStorage = { navController.navigate(Routes.STORAGE) { launchSingleTop = true } },
                        onOpenSwipe = { navController.navigate(Routes.SWIPES) { launchSingleTop = true } },
                        onOpenInterface = { navController.navigate(Routes.INTERFACE) { launchSingleTop = true } },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                        onOpenArtwork = { navController.navigate(Routes.ARTWORK) { launchSingleTop = true } },
                        onOpenBackgrounds = { navController.navigate(Routes.BACKGROUNDS) { launchSingleTop = true } },
                        onOpenPresets = { navController.navigate(Routes.PRESETS) { launchSingleTop = true } },
                        onOpenWorkshop = { navController.navigate(Routes.WORKSHOP) { launchSingleTop = true } },
                        onOpenProxy = { navController.navigate(Routes.PROXY) { launchSingleTop = true } },
                    )
                }
                composable(Routes.GENERAL) {
                    GeneralSettingsScreen(
                        viewModel = settingsVm,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate(Routes.ACCOUNT) { launchSingleTop = true } },
                    )
                }
                composable(Routes.AUDIO) {
                    AudioSettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.STORAGE) {
                    StorageSettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.SWIPES) {
                    SwipeSettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.INTERFACE) {
                    InterfaceSettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYER) {
                    PlayerSettingsScreen(
                        settingsVm,
                        contentPadding,
                        onBack = { navController.popBackStack() },
                        onOpenPresets = { navController.navigate(Routes.PRESETS) { launchSingleTop = true } },
                    )
                }
                composable(Routes.ARTWORK) {
                    ArtworkSettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.BACKGROUNDS) {
                    BackgroundsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.PRESETS) {
                    PresetsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.PROXY) {
                    ProxySettingsScreen(settingsVm, contentPadding, onBack = { navController.popBackStack() })
                }
                composable(Routes.TRENDING) {
                    TrendingScreen(
                        viewModel = trendingVm,
                        currentTrackId = playerState.current?.id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPlay = play,
                        onMore = openMenu,
                        onOpenArtist = openArtist,
                    )
                }
                composable(Routes.WORKSHOP) {
                    WorkshopScreen(
                        viewModel = workshopVm,
                        currentTheme = settings.theme,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onPickBuiltIn = { theme ->
                            settingsVm.setTheme(theme)
                            if (theme != AppTheme.Workshop) settingsVm.clearCustomTheme()
                        },
                    )
                }
                composable(Routes.ACCOUNT) {
                    // Logged out: sign-in form; logged in: the full profile page.
                    val account by app.settings.account.collectAsStateWithLifecycle(initialValue = null)
                    if (account == null) {
                        AccountScreen(
                            viewModel = accountVm,
                            contentPadding = contentPadding,
                            onBack = { navController.popBackStack() },
                        )
                    } else {
                        val profileVm: ProfileViewModel = viewModel(factory = factory)
                        ProfileScreen(
                            viewModel = profileVm,
                            targetId = null,
                            contentPadding = contentPadding,
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(Routes.PROFILE_EDIT) { launchSingleTop = true } },
                            onOpenUser = { id -> navController.navigate(Routes.user(id)) { launchSingleTop = true } },
                            onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) { launchSingleTop = true } },
                            onPlayTracks = play,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                            onMessage = showMsg,
                        )
                    }
                }
                composable(Routes.PROFILE_EDIT) {
                    val profileVm: ProfileViewModel = viewModel(factory = factory)
                    EditProfileScreen(
                        viewModel = profileVm,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onMessage = showMsg,
                    )
                }
                composable(Routes.USER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val profileVm: ProfileViewModel = viewModel(factory = factory)
                    ProfileScreen(
                        viewModel = profileVm,
                        targetId = id,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.PROFILE_EDIT) { launchSingleTop = true } },
                        onOpenUser = { other -> navController.navigate(Routes.user(other)) { launchSingleTop = true } },
                        onOpenPlaylist = { pid -> navController.navigate(Routes.playlist(pid)) { launchSingleTop = true } },
                        onPlayTracks = play,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onMessage = showMsg,
                    )
                }
                composable(Routes.IMPORT) {
                    ImportScreen(
                        viewModel = importVm,
                        contentPadding = contentPadding,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerExpanded && playerState.current != null,
            enter = slideInVertically(tween(180)) { it } + fadeIn(tween(140)),
            exit = slideOutVertically(tween(150)) { it } + fadeOut(tween(110)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LaunchedEffect(playerState.current?.id) { playerVm.ensureLyrics() }
            val cp = parseCustomPrefs(settings.customPrefs)
            PlayerScreen(
                state = playerState,
                artworkStyle = settings.artworkStyle,
                background = settings.playerBackground,
                visuals = PlayerVisuals(
                    titleLeft = cp.titleAlign == "left",
                    pillPlayButton = cp.playButtonStyle == "pill",
                    infoChip = cp.infoChip,
                    gifArtwork = cp.gifArtwork,
                    artworkAnimation = cp.artworkAnimation,
                    themedSliderHex = if (cp.themeApplySlider && settings.customTheme.isNotBlank()) parseWorkshopSpec(settings.customTheme).primary else "",
                    artBlur = cp.artBlur,
                    artDim = cp.artDim,
                    gifAlways = cp.themeArtworkAlways,
                    artworkEffect = cp.artworkEffect,
                    artworkInside = cp.artworkInside,
                    accentFromArt = cp.accentFromArt,
                    swipeNavigation = cp.playerSwipes,
                    iosSlider = cp.sliderStyle != "classic",
                ),
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
                onShare = { playerState.current?.let(shareTrack) },
                onOpenMenu = { playerState.current?.let(libraryVm::openMenu) },
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
            downloaded = m.downloaded,
            onDownload = {
                libraryVm.downloadTrack(m.track)
                libraryVm.closeMenu()
            },
            onRemoveDownload = {
                libraryVm.removeDownload(m.track)
                libraryVm.closeMenu()
            },
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

    // First launch: offer to sign in or continue as a guest (shown once).
    if (!parseCustomPrefs(settings.customPrefs).welcomeDone) {
        WelcomeOverlay(
            onSignIn = {
                settingsVm.updatePrefs { it.copy(welcomeDone = true) }
                navController.navigate(Routes.ACCOUNT) { launchSingleTop = true }
            },
            onGuest = { settingsVm.updatePrefs { it.copy(welcomeDone = true) } },
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
                icon = {
                    Icon(
                        painter = painterResource(if (isSelected) tab.iconSelected else tab.icon),
                        contentDescription = stringResource(tab.label),
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VotifyColors.TextPrimary,
                    unselectedIconColor = VotifyColors.TextMuted,
                    // No white pill behind the active icon — the icon itself goes bright.
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
}

/** Builds our ViewModels with the app-scoped singletons. */
private class AppViewModelFactory(private val app: VotifyApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app.music, app.library, app.settings, app) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(app.music) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(app.library, app.player, app.music) as T
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(app.music, app.player, app.library) as T
        modelClass.isAssignableFrom(ArtistViewModel::class.java) -> ArtistViewModel(app.music) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app.api, app.settings, app.library, app) as T
        modelClass.isAssignableFrom(AccountViewModel::class.java) -> AccountViewModel(app.api, app.settings, app.music) as T
        modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(app.api, app.settings, app.library) as T
        modelClass.isAssignableFrom(ImportViewModel::class.java) -> ImportViewModel(app.music, app.library) as T
        modelClass.isAssignableFrom(TrendingViewModel::class.java) -> TrendingViewModel(app.music, app.settings) as T
        modelClass.isAssignableFrom(WorkshopViewModel::class.java) -> WorkshopViewModel(app.settings, app.api, app.music) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
