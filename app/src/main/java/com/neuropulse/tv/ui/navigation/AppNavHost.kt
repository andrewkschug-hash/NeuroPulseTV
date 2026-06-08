package com.neuropulse.tv.ui.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
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
    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val isRecordingActive by recordingViewModel.isRecordingActive.collectAsStateWithLifecycle()
    val profileInitials = profiles.firstOrNull()?.name?.take(2)?.uppercase() ?: "?"
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var showWhatsNew by remember { mutableStateOf(false) }

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

    val isHome = current?.startsWith("home") == true
    val selectedTab = when {
        current?.startsWith("settings") == true -> EpgNavTab.Settings
        current?.startsWith("recordings") == true -> EpgNavTab.Recordings
        else -> EpgNavTab.Home
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isHome) {
            GridNavIconRow(
                selectedTab = selectedTab,
                focusedIndex = -1,
                navFocused = false,
                profileInitials = profileInitials,
                profileFocused = false,
                isRecordingActive = isRecordingActive,
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
                        EpgNavTab.Settings -> navController.navigate(Routes.Settings.route) {
                            launchSingleTop = true
                        }
                        EpgNavTab.Profile -> onSwitchProfile()
                    }
                },
                onProfileClick = onSwitchProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EpgLayout.TopBarHeight)
                    .background(EpgColors.Background)
                    .padding(horizontal = 16.dp)
            )
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
            composable(Routes.Home.route) {
                HomeEpgScreen(
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
                RecordingsScreen()
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
                    onPickLocalFile = onPickLocalFile,
                    onPickTiviMateZip = onPickTiviMateZip,
                    onOpenEpgResolver = { navController.navigate(Routes.EpgResolver.route) },
                    onBack = { navController.popBackStack() }
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
                    onOpenSplit = { id -> navController.navigate(Routes.SplitView.build(id)) }
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
