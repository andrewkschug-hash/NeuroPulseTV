package com.grid.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.grid.tv.ui.screen.onboarding.OnboardingScreen
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.OnboardingViewModel

private enum class SetupGateState { Loading, HasConnection, NeedsOnboarding }

@Composable
fun SetupGate(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var gateState by remember { mutableStateOf(SetupGateState.Loading) }

    LaunchedEffect(Unit) {
        gateState = if (viewModel.hasActiveConnection()) {
            SetupGateState.HasConnection
        } else {
            SetupGateState.NeedsOnboarding
        }
    }

    when (gateState) {
        SetupGateState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading…",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp
                )
            }
        }
        SetupGateState.HasConnection -> LaunchedEffect(Unit) { onComplete() }
        SetupGateState.NeedsOnboarding -> OnboardingScreen(
            onComplete = onComplete,
            onSkip = onComplete
        )
    }
}
