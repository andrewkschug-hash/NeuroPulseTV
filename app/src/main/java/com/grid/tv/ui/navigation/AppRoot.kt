package com.grid.tv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.grid.tv.data.db.AppDatabaseHolder
import com.grid.tv.ui.screen.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun AppRoot(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit
) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val splashReady = rememberSplashReady()

    Box(modifier = Modifier.fillMaxSize()) {
        HomeShell(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onRestartToOnboarding = {},
            onSignOut = {}
        )

        if (showSplash) {
            SplashScreen(
                isReady = splashReady,
                onFinished = { showSplash = false }
            )
        }
    }
}

@Composable
private fun rememberSplashReady(): Boolean {
    var ready by remember { mutableStateOf(AppDatabaseHolder.isReady()) }
    LaunchedEffect(Unit) {
        while (!ready) {
            delay(32)
            ready = AppDatabaseHolder.isReady()
        }
    }
    return ready
}
