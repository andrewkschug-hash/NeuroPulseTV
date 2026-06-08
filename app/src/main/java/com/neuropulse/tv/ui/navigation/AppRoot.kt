package com.neuropulse.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.neuropulse.tv.ui.screen.ProfilePickerScreen
import com.neuropulse.tv.ui.screen.SplashScreen

private enum class AppPhase { Splash, Profile, Setup, Main }

@Composable
fun AppRoot(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit
) {
    var phase by rememberSaveable { mutableStateOf(AppPhase.Splash) }

    when (phase) {
        AppPhase.Splash -> SplashScreen(onFinished = { phase = AppPhase.Profile })
        AppPhase.Profile -> ProfilePickerScreen(onProfileSelected = { phase = AppPhase.Setup })
        AppPhase.Setup -> SetupGate(onComplete = { phase = AppPhase.Main })
        AppPhase.Main -> AppNavHost(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onSwitchProfile = { phase = AppPhase.Profile }
        )
    }
}
