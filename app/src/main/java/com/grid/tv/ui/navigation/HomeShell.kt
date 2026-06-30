package com.grid.tv.ui.navigation

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.ui.screen.ProfilePickerScreen
import com.grid.tv.ui.screen.onboarding.OnboardingScreen
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import com.grid.tv.ui.viewmodel.OnboardingViewModel
import com.grid.tv.ui.viewmodel.ProfileViewModel

/**
 * Single lazy-loading home shell: main navigation always composes underneath;
 * auth, profile, and onboarding appear as overlays when needed.
 */
@Composable
fun HomeShell(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onRestartToOnboarding: () -> Unit,
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val profilesReady by profileViewModel.profilesReady.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()

    var forceProfilePicker by rememberSaveable { mutableStateOf(false) }
    var setupResolved by rememberSaveable { mutableStateOf(false) }
    var needsOnboarding by rememberSaveable { mutableStateOf(false) }

    val isPastAuth = authState is AuthUiState.Authenticated || authState is AuthUiState.Guest
    val showAuthOverlay = authState is AuthUiState.Unauthenticated ||
        authState is AuthUiState.Error ||
        authState is AuthUiState.SigningIn

    val showProfileOverlay = isPastAuth &&
        profilesReady &&
        (forceProfilePicker || activeProfile == null)

    LaunchedEffect(isPastAuth, profilesReady, activeProfile, forceProfilePicker) {
        if (!isPastAuth || !profilesReady || activeProfile == null || forceProfilePicker) return@LaunchedEffect
        if (!setupResolved) {
            needsOnboarding = !onboardingViewModel.hasActiveConnection()
            setupResolved = true
        }
    }

    val showOnboardingOverlay = isPastAuth &&
        profilesReady &&
        activeProfile != null &&
        !forceProfilePicker &&
        setupResolved &&
        needsOnboarding

    val onSwitchProfile: () -> Unit = { forceProfilePicker = true }

    val onProfileSelected: () -> Unit = {
        forceProfilePicker = false
        setupResolved = false
    }

    val onOnboardingComplete: () -> Unit = {
        needsOnboarding = false
        setupResolved = true
    }

    val onRestartOnboarding: () -> Unit = {
        needsOnboarding = true
        setupResolved = true
        onRestartToOnboarding()
    }

    val onSignOutRequested: () -> Unit = {
        authViewModel.signOut {
            forceProfilePicker = false
            setupResolved = false
            needsOnboarding = false
            onSignOut()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainContentGate(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onSwitchProfile = onSwitchProfile,
            onRestartToOnboarding = onRestartOnboarding,
            onSignOut = onSignOutRequested
        )

        if (showAuthOverlay) {
            AuthGate(
                onAuthenticated = {},
                blocking = false
            )
        }

        if (!showAuthOverlay && showProfileOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background)
            ) {
                ProfilePickerScreen(onProfileSelected = onProfileSelected)
            }
        }

        if (!showAuthOverlay && !showProfileOverlay && showOnboardingOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background)
            ) {
                OnboardingScreen(
                    onComplete = onOnboardingComplete,
                    onSkip = onOnboardingComplete
                )
            }
        }
    }
}
