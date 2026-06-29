package com.grid.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.grid.tv.ui.screen.onboarding.OnboardingScreen
import com.grid.tv.ui.viewmodel.OnboardingViewModel

private enum class SetupGateState { HasConnection, NeedsOnboarding }

@Composable
fun SetupGate(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var gateState by remember { mutableStateOf<SetupGateState?>(null) }

    LaunchedEffect(Unit) {
        gateState = if (viewModel.hasActiveConnection()) {
            SetupGateState.HasConnection
        } else {
            SetupGateState.NeedsOnboarding
        }
    }

    when (gateState) {
        null, SetupGateState.HasConnection -> {
            if (gateState == SetupGateState.HasConnection) {
                LaunchedEffect(Unit) { onComplete() }
            }
        }
        SetupGateState.NeedsOnboarding -> OnboardingScreen(
            onComplete = onComplete,
            onSkip = onComplete
        )
    }
}
