package com.grid.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GridNavIconRow
import com.grid.tv.ui.component.ProfileMenuDropdown
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.ui.screen.DirectPlayerScreen
import com.grid.tv.ui.screen.EpgResolverScreen
import com.grid.tv.ui.screen.HomeEpgScreen
import com.grid.tv.ui.screen.MultiViewScreen
import com.grid.tv.ui.screen.PlayerScreen
import com.grid.tv.ui.screen.SplitViewScreen
import com.grid.tv.ui.screen.WhatsNewScreen
import com.grid.tv.ui.screen.RecordingsScreen
import com.grid.tv.ui.screen.SettingsScreen
import com.grid.tv.ui.screen.VodHubScreen
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.ProfileViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import com.grid.tv.util.profileInitials

@Composable
fun AppNavHost(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onSwitchProfile: () -> Unit = {},
    onRestartToOnboarding: () -> Unit = {}
) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()
    val profileInitials = activeProfile?.let { profileInitials(it.name) } ?: "?"
    val profileAvatarColor = activeProfile?.avatarColor ?: DEFAULT_PROFILE_AVATAR_COLOR
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var showWhatsNew by remember { mutableStateOf(false) }

    fun navigateToFavorites() {
        val homeRoute = Routes.Home.route
        if (navController.currentDestination?.route?.startsWith("home") == true) {
            navController.currentBackStackEntry?.savedStateHandle?.set("openFavorites", true)
        } else {
            navController.navigate(homeRoute) {
                popUpTo(homeRoute) { inclusive = false }
                launchSingleTop = true
            }
            runCatching {
                navController.getBackStackEntry(homeRoute).savedStateHandle.set("openFavorites", true)
            }
        }
    }

    @Composable
    fun RoutedVodHub(
        initialTab: Int,
        initialSeriesId: Long? = null,
        onBack: () -> Unit
    ) {
        VodHubScreen(
            initialTab = initialTab,
            initialSeriesId = initialSeriesId,
            profileInitials = profileInitials,
            profileAvatarColor = profileAvatarColor,
            onPlayMovie = { title, url, resume ->
                navController.navigate(Routes.DirectPlayer.build(title, url, resume = resume))
            },
            onPlayUrl = { title, url, resume ->
                navController.navigate(Routes.DirectPlayer.build(title, url, resume = resume))
            },
            onNavigateHome = {
                navController.navigate(Routes.Home.route) {
                    popUpTo(Routes.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateRecordings = {
                navController.navigate(Routes.Recordings.route) { launchSingleTop = true }
            },
            onNavigateSettings = {
                navController.navigate(Routes.Settings.route) { launchSingleTop = true }
            },
            onNavigateVod = { tab ->
                navController.navigate(Routes.VodHub.build(tab)) { launchSingleTop = true }
            },
            onOpenFavorites = { navigateToFavorites() },
            onNavigateProfile = onSwitchProfile,
            onBack = onBack
        )
    }

    LaunchedEffect(Unit) {
        showWhatsNew = settingsViewModel.shouldShowWhatsNew()
    }

    if (showWhatsNew) {
        WhatsNewScreen(
            version = "2.1.0",
            onDismiss = {
                settingsViewModel.markWhatsNewSeen()
                showWhatsNew = false
            }
        )
        return
    }

    val hasEmbeddedTopBar = current?.startsWith("home") == true ||
        current?.startsWith("recordings") == true ||
        current?.startsWith("settings") == true ||
        current?.startsWith("vod/") == true ||
        current?.startsWith("movies") == true ||
        current?.startsWith("series/") == true

    val hideGlobalTopNav = current?.startsWith("player/") == true ||
        current?.startsWith("direct-player/") == true

    val canNavigateBack = navController.previousBackStackEntry != null
    BackHandler(enabled = !canNavigateBack) {
        // Keep the app open when back is pressed on the root TV guide.
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasEmbeddedTopBar && !hideGlobalTopNav) {
            val selectedTab = when {
                current?.startsWith("vod/") == true ||
                    current?.startsWith("movies") == true ||
                    current?.startsWith("series/") == true -> EpgNavTab.Vod
                current?.startsWith("recordings") == true -> EpgNavTab.Recordings
                else -> EpgNavTab.Guide
            }
            var profileMenuOpen by remember { mutableStateOf(false) }
            var profileMenuFocusIndex by remember { mutableIntStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EpgColors.Background)
                    .onPreviewKeyEvent { event ->
                        if (!profileMenuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                profileMenuOpen = false
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                                profileMenuOpen = false
                                false
                            }
                            else -> false
                        }
                    }
            ) {
                GridNavIconRow(
                    selectedTab = selectedTab,
                    focusedIndex = -1,
                    navFocused = false,
                    profileInitials = profileInitials,
                    profileAvatarColor = profileAvatarColor,
                    profileFocused = false,
                    onTabSelected = { tab ->
                        when (tab) {
                            EpgNavTab.Guide, EpgNavTab.Home -> navController.navigate(Routes.Home.route) {
                                popUpTo(Routes.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                            EpgNavTab.Search -> navController.navigate(Routes.Home.route) {
                                launchSingleTop = true
                            }
                            EpgNavTab.Vod, EpgNavTab.Movies -> navController.navigate(
                                Routes.VodHub.build(initialTab = 0)
                            ) {
                                launchSingleTop = true
                            }
                            EpgNavTab.Series -> navController.navigate(
                                Routes.VodHub.build(initialTab = 1)
                            ) {
                                launchSingleTop = true
                            }
                            EpgNavTab.Recordings -> navController.navigate(Routes.Recordings.route) {
                                launchSingleTop = true
                            }
                            EpgNavTab.Favorites -> navigateToFavorites()
                            EpgNavTab.Settings -> navController.navigate(Routes.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onProfileClick = {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                ProfileMenuDropdown(
                    expanded = profileMenuOpen,
                    focusedIndex = profileMenuFocusIndex,
                    onDismiss = { profileMenuOpen = false },
                    onSwitchAccounts = {
                        profileMenuOpen = false
                        onSwitchProfile()
                    },
                    onOpenSettings = {
                        profileMenuOpen = false
                        navController.navigate(Routes.Settings.route) { launchSingleTop = true }
                    }
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.weight(1f),
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { -it } },
            popEnterTransition = { slideInHorizontally { -it } },
            popExitTransition = { slideOutHorizontally { it } }
        ) {
            composable(Routes.Home.route) { entry ->
                val viewModel: HomeEpgViewModel = hiltViewModel()
                val recordingViewModel: RecordingViewModel = hiltViewModel()
                val searchViewModel: SearchViewModel = hiltViewModel()
                val openFavorites by entry.savedStateHandle
                    .getStateFlow("openFavorites", false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(openFavorites) {
                    if (openFavorites) {
                        entry.savedStateHandle["openFavorites"] = false
                    }
                }
                HomeEpgScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
                    searchViewModel = searchViewModel,
                    openFavoritesInitially = openFavorites,
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onNavigateRecordings = { navController.navigate(Routes.Recordings.route) },
                    onNavigateSettings = { navController.navigate(Routes.Settings.route) },
                    onNavigateProfile = onSwitchProfile,
                    onNavigateVod = { tab ->
                        navController.navigate(Routes.VodHub.build(tab)) { launchSingleTop = true }
                    },
                    onNavigateSeries = { seriesId ->
                        navController.navigate(Routes.VodHub.build(initialTab = 1, seriesId = seriesId)) {
                            launchSingleTop = true
                        }
                    },
                    onPlayVod = { url, title, resume ->
                        navController.navigate(Routes.DirectPlayer.build(title, url, resume = resume))
                    },
                    onPlayCatchup = { title, url ->
                        navController.navigate(Routes.DirectPlayer.build(title, url))
                    },
                    onResumeContinueWatching = { item ->
                        VodPlaybackHelper.stageContinueWatching(item)
                        navController.navigate(
                            Routes.DirectPlayer.build(
                                title = item.title,
                                url = item.streamUrl,
                                resume = true
                            )
                        )
                    },
                    profileInitials = profileInitials,
                    profileAvatarColor = profileAvatarColor
                )
            }
            composable(Routes.Movies.route) {
                RoutedVodHub(initialTab = 0, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.VodHub.route,
                arguments = listOf(
                    navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("seriesId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val initialTab = entry.arguments?.getInt("initialTab") ?: 0
                val seriesId = entry.arguments?.getLong("seriesId") ?: -1L
                RoutedVodHub(
                    initialTab = initialTab,
                    initialSeriesId = seriesId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Recordings.route) {
                RecordingsScreen(
                    profileInitials = profileInitials,
                    profileAvatarColor = profileAvatarColor,
                    onNavigateHome = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateSettings = { navController.navigate(Routes.Settings.route) { launchSingleTop = true } },
                    onNavigateVod = { tab ->
                        navController.navigate(Routes.VodHub.build(tab)) { launchSingleTop = true }
                    },
                    onOpenFavorites = { navigateToFavorites() },
                    onNavigateProfile = onSwitchProfile,
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onPlayRecording = { title, url, recordingId, recordedAt, resume ->
                        navController.navigate(
                            Routes.DirectPlayer.build(
                                title = title,
                                url = url,
                                recordingId = recordingId,
                                recordedAt = recordedAt,
                                resume = resume
                            )
                        )
                    }
                )
            }
            composable(
                route = Routes.Series.route,
                arguments = listOf(navArgument("seriesId") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val seriesId = entry.arguments?.getLong("seriesId") ?: -1L
                RoutedVodHub(
                    initialTab = 1,
                    initialSeriesId = seriesId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Settings.route) {
                SettingsScreen(
                    profileInitials = profileInitials,
                    onNavigateHome = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateRecordings = {
                        navController.navigate(Routes.Recordings.route) { launchSingleTop = true }
                    },
                    onNavigateVod = { tab ->
                        navController.navigate(Routes.VodHub.build(tab)) { launchSingleTop = true }
                    },
                    onOpenFavorites = { navigateToFavorites() },
                    onSwitchProfile = onSwitchProfile,
                    onBack = { navController.popBackStack() },
                    onPickLocalFile = onPickLocalFile,
                    onPickTiviMateZip = onPickTiviMateZip,
                    onOpenEpgResolver = { navController.navigate(Routes.EpgResolver.route) },
                    onRestartToOnboarding = onRestartToOnboarding
                )
            }
            composable(Routes.EpgResolver.route) {
                EpgResolverScreen()
            }
            composable(
                route = Routes.Multiview.route,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType; defaultValue = 0L })
            ) {
                MultiViewScreen(
                    seedChannelId = it.arguments?.getLong("channelId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.Player.route,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType })
            ) {
                PlayerScreen(
                    channelId = it.arguments?.getLong("channelId") ?: 0,
                    onBack = { navController.popBackStack() },
                    onOpenSplit = { id -> navController.navigate(Routes.SplitView.build(id)) },
                    onNavigateGuide = { navController.popBackStack() },
                    onNavigateRecordings = {
                        navController.navigate(Routes.Recordings.route) { launchSingleTop = true }
                    },
                    onNavigateSettings = {
                        navController.navigate(Routes.Settings.route) { launchSingleTop = true }
                    }
                )
            }
            composable(
                route = Routes.SplitView.route,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType })
            ) {
                SplitViewScreen(
                    primaryChannelId = it.arguments?.getLong("channelId") ?: 0,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DirectPlayer.route,
                arguments = listOf(
                    navArgument("recordingId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("recordedAt") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("resume") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) {
                DirectPlayerScreen(
                    recordingId = it.arguments?.getLong("recordingId") ?: 0L,
                    recordedAt = it.arguments?.getLong("recordedAt") ?: 0L,
                    resume = (it.arguments?.getInt("resume") ?: 0) == 1,
                    title = android.net.Uri.decode(it.arguments?.getString("title") ?: "Video"),
                    url = android.net.Uri.decode(it.arguments?.getString("url") ?: ""),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
