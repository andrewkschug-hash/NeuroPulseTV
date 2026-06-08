package com.neuropulse.tv.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
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
import com.neuropulse.tv.ui.viewmodel.ProfileViewModel

@Composable
fun AppNavHost(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit
) {
    val navController = rememberNavController()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    var showProfiles by remember { mutableStateOf(false) }
    val current = navController.currentBackStackEntryAsState().value?.destination?.route

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopNavButton("Home", current?.startsWith("home") == true) { navController.navigate(Routes.Home.route) }
            TopNavButton("Browser", current?.startsWith("browser") == true) { navController.navigate(Routes.Browser.route) }
            TopNavButton("Search", current?.startsWith("search") == true) { navController.navigate(Routes.Search.route) }
            TopNavButton("Series", current?.startsWith("series") == true) { navController.navigate(Routes.Series.route) }
            TopNavButton("Recordings", current?.startsWith("recordings") == true) { navController.navigate(Routes.Recordings.route) }
            TopNavButton("Settings", current?.startsWith("settings") == true) { navController.navigate(Routes.Settings.route) }
            TopNavButton("Profiles", false) { showProfiles = true }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Routes.Home.route) {
                HomeEpgScreen(
                    onWatchChannel = { navController.navigate(Routes.Player.build(it)) },
                    onOpenEpg = { navController.navigate(Routes.Browser.route) },
                    onPlayUrl = { url, title -> navController.navigate(Routes.DirectPlayer.build(title, url)) },
                    onOpenSeries = { navController.navigate(Routes.Series.route) }
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

        if (showProfiles) {
            AlertDialog(
                onDismissRequest = { showProfiles = false },
                title = { Text("Switch Profile") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.forEach { p ->
                            Button(onClick = {
                                profileViewModel.switchProfile(p.id)
                                showProfiles = false
                            }) { Text(p.name) }
                        }
                    }
                },
                confirmButton = { Button(onClick = { showProfiles = false }) { Text("Close") } }
            )
        }
    }
}

@Composable
private fun TopNavButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(if (selected) "[$title]" else title) }
}
