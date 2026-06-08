package com.neuropulse.tv.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.screen.ChannelBrowserScreen
import com.neuropulse.tv.ui.screen.DirectPlayerScreen
import com.neuropulse.tv.ui.screen.EpgResolverScreen
import com.neuropulse.tv.ui.screen.HomeEpgScreen
import com.neuropulse.tv.ui.screen.MultiviewPlaceholderScreen
import com.neuropulse.tv.ui.screen.PlayerScreen
import com.neuropulse.tv.ui.screen.RecordingsScreen
import com.neuropulse.tv.ui.screen.SearchScreen
import com.neuropulse.tv.ui.screen.SeriesBrowserScreen
import com.neuropulse.tv.ui.screen.SettingsScreen
@Composable
fun AppNavHost(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onSwitchProfile: () -> Unit = {}
) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route

    val isHome = current?.startsWith("home") == true

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isHome) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopNavButton("Home", false) { navController.navigate(Routes.Home.route) }
                TopNavButton("Browser", current?.startsWith("browser") == true) { navController.navigate(Routes.Browser.route) }
                TopNavButton("Search", current?.startsWith("search") == true) { navController.navigate(Routes.Search.route) }
                TopNavButton("Series", current?.startsWith("series") == true) { navController.navigate(Routes.Series.route) }
                TopNavButton("Recordings", current?.startsWith("recordings") == true) { navController.navigate(Routes.Recordings.route) }
                TopNavButton("Settings", current?.startsWith("settings") == true) { navController.navigate(Routes.Settings.route) }
                TopNavButton("Profiles", false) { onSwitchProfile() }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Routes.Home.route) {
                HomeEpgScreen(
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onNavigateSearch = { navController.navigate(Routes.Search.route) },
                    onNavigateRecordings = { navController.navigate(Routes.Recordings.route) },
                    onNavigateSettings = { navController.navigate(Routes.Settings.route) },
                    onNavigateProfile = onSwitchProfile
                )
            }
            composable(Routes.Browser.route) {
                ChannelBrowserScreen(
                    onPlayChannel = { navController.navigate(Routes.Player.build(it)) },
                    onMultiview = { navController.navigate(Routes.Multiview.route) }
                )
            }
            composable(Routes.Search.route) {
                SearchScreen(onPlayChannel = { navController.navigate(Routes.Player.build(it)) })
            }
            composable(Routes.Recordings.route) {
                RecordingsScreen()
            }
            composable(Routes.Series.route) {
                SeriesBrowserScreen(onPlayUrl = { url, title -> navController.navigate(Routes.DirectPlayer.build(title, url)) })
            }
            composable(Routes.Settings.route) {
                SettingsScreen(
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

@Composable
private fun TopNavButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(if (selected) "[$title]" else title) }
}
