package com.grid.tv.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.ui.component.UpdateAvailableDialog
import com.grid.tv.ui.screen.ProfilePickerScreen
import com.grid.tv.ui.screen.SplashScreen
import com.grid.tv.ui.viewmodel.UpdatePromptState
import com.grid.tv.ui.viewmodel.UpdateViewModel

private enum class AppPhase { Splash, Auth, Profile, Setup, Main }

@Composable
fun AppRoot(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit
) {
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updatePrompt by updateViewModel.promptState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        updateViewModel.checkOnLaunchIfNeeded()
    }

    if (updatePrompt is UpdatePromptState.Available) {
        val info = (updatePrompt as UpdatePromptState.Available).info
        UpdateAvailableDialog(
            info = info,
            onUpdate = {
                updateViewModel.clearPrompt()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            onDismiss = { updateViewModel.dismissPrompt(info.latestVersion) }
        )
    }

    var phase by rememberSaveable { mutableStateOf(AppPhase.Splash) }

    when (phase) {
        AppPhase.Splash -> SplashScreen(onFinished = { phase = AppPhase.Auth })
        AppPhase.Auth -> AuthGate(onAuthenticated = { phase = AppPhase.Profile })
        AppPhase.Profile -> ProfilePickerScreen(onProfileSelected = { phase = AppPhase.Setup })
        AppPhase.Setup -> SetupGate(onComplete = { phase = AppPhase.Main })
        AppPhase.Main -> MainContentGate(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onSwitchProfile = { phase = AppPhase.Profile },
            onRestartToOnboarding = { phase = AppPhase.Splash },
            onSignOut = { phase = AppPhase.Auth }
        )
    }
}
