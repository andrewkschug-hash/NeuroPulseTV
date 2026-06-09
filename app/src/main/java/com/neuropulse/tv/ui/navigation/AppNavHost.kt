package com.neuropulse.tv.ui.navigation

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
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.GridNavIconRow
import com.neuropulse.tv.ui.component.ProfileMenuDropdown
import com.neuropulse.tv.ui.screen.DirectPlayerScreen
import com.neuropulse.tv.ui.screen.EpgResolverScreen
import com.neuropulse.tv.ui.screen.HomeEpgScreen
import com.neuropulse.tv.ui.screen.MultiviewPlaceholderScreen
import com.neuropulse.tv.ui.screen.PlayerScreen
import com.neuropulse.tv.ui.screen.SplitViewScreen
import com.neuropulse.tv.ui.screen.WhatsNewScreen
import com.neuropulse.tv.ui.screen.RecordingsScreen
import com.neuropulse.tv.ui.screen.SeriesBrowserScreen
import com.neuropulse.tv.ui.screen.SettingsScreen
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.ProfileViewModel
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel

@Composable
fun AppNavHost(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onSwitchProfile: () -> Unit = {}
) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val profileInitials = profiles.firstOrNull()?.name?.take(2)?.uppercase() ?: "?"
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
        current?.startsWith("settings") == true

    val hideGlobalTopNav = current?.startsWith("player/") == true ||
        current?.startsWith("direct-player/") == true

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasEmbeddedTopBar && !hideGlobalTopNav) {
            val selectedTab = when {
                current?.startsWith("recordings") == true -> EpgNavTab.Recordings
                else -> EpgNavTab.Home
            }
            var profileMenuOpen by remember { mutableStateOf(false) }
            var profileMenuFocusIndex by remember { mutableIntStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EpgLayout.TopBarHeight)
                    .background(EpgColors.Background)
                    .onPreviewKeyEvent { event ->
                        if (!profileMenuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                profileMenuFocusIndex = (profileMenuFocusIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionDown -> {
                                profileMenuFocusIndex = (profileMenuFocusIndex + 1).coerceAtMost(1)
                                true
                            }
                            Key.Back, Key.Escape -> {
                                profileMenuOpen = false
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                profileMenuOpen = false
                                when (profileMenuFocusIndex) {
                                    0 -> onSwitchProfile()
                                    1 -> navController.navigate(Routes.Settings.route) { launchSingleTop = true }
                                }
                                true
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
                    profileFocused = false,
                    onTabSelected = { tab ->
                        when (tab) {
                            EpgNavTab.Home -> navController.navigate(Routes.Home.route) {
                                popUpTo(Routes.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                            EpgNavTab.Search -> navController.navigate(Routes.Home.route) {
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
                    onSwitchAccounts = onSwitchProfile,
                    onOpenSettings = {
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
                val openFavorites by entry.savedStateHandle
                    .getStateFlow("openFavorites", false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(openFavorites) {
                    if (openFavorites) {
                        entry.savedStateHandle["openFavorites"] = false
                    }
                }
                HomeEpgScreen(
                    openFavoritesInitially = openFavorites,
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onNavigateRecordings = { navController.navigate(Routes.Recordings.route) },
                    onNavigateSettings = { navController.navigate(Routes.Settings.route) },
                    onNavigateProfile = onSwitchProfile,
                    onNavigateSeries = { seriesId ->
                        navController.navigate(Routes.Series.build(seriesId))
                    },
                    onPlayVod = { url, title ->
                        navController.navigate(Routes.DirectPlayer.build(title, url))
                    },
                    onPlayCatchup = { title, url ->
                        navController.navigate(Routes.DirectPlayer.build(title, url))
                    },
                    profileInitials = profileInitials
                )
            }
            composable(Routes.Recordings.route) {
                RecordingsScreen(
                    profileInitials = profileInitials,
                    onNavigateHome = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateSettings = { navController.navigate(Routes.Settings.route) { launchSingleTop = true } },
                    onOpenFavorites = { navigateToFavorites() },
                    onNavigateProfile = onSwitchProfile,
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onPlayRecording = { title, url ->
                        navController.navigate(Routes.DirectPlayer.build(title, url))
                    }
                )
            }
            composable(
                route = Routes.Series.route,
                arguments = listOf(navArgument("seriesId") { type = NavType.LongType; defaultValue = -1L })
            ) {
                val seriesId = it.arguments?.getLong("seriesId") ?: -1L
                SeriesBrowserScreen(
                    initialSeriesId = seriesId.takeIf { it > 0 },
                    onPlayUrl = { url, title -> navController.navigate(Routes.DirectPlayer.build(title, url)) },
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
                    onOpenFavorites = { navigateToFavorites() },
                    onSwitchProfile = onSwitchProfile,
                    onPickLocalFile = onPickLocalFile,
                    onPickTiviMateZip = onPickTiviMateZip,
                    onOpenEpgResolver = { navController.navigate(Routes.EpgResolver.route) }
                )
            }
            composable(Routes.EpgResolver.route) {
                EpgResolverScreen()
            }
            composable(Routes.Multiview.route) {
                MultiviewPlaceholderScreen()
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
                    navArgument("title") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) {
                DirectPlayerScreen(
                    title = android.net.Uri.decode(it.arguments?.getString("title") ?: "Video"),
                    url = android.net.Uri.decode(it.arguments?.getString("url") ?: ""),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
