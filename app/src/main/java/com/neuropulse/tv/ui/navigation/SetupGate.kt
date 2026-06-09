package com.neuropulse.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var hadPlaylistsOnStart by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(playlists) {
        if (hadPlaylistsOnStart == null) {
            hadPlaylistsOnStart = playlists.isNotEmpty()
        }
    }

    when (hadPlaylistsOnStart) {
        true -> LaunchedEffect(Unit) { onComplete() }
        false -> OnboardingScreen(
            onComplete = onComplete,
            onSkip = onComplete
        )
        null -> Unit
    }
}
