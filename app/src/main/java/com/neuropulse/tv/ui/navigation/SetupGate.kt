package com.neuropulse.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuropulse.tv.ui.screen.onboarding.OnboardingScreen
import com.neuropulse.tv.ui.viewmodel.OnboardingViewModel

@Composable
fun SetupGate(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    if (playlists.isNotEmpty()) {
        LaunchedEffect(Unit) { onComplete() }
    } else {
        OnboardingScreen(onComplete = onComplete)
    }
}
