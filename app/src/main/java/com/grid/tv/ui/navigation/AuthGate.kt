package com.grid.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.MainActivity
import com.grid.tv.di.SupabaseEntryPoint
import com.grid.tv.ui.screen.LoginScreen
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import dagger.hilt.android.EntryPointAccessors

@Composable
fun AuthGate(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val supabaseClient = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SupabaseEntryPoint::class.java
    ).supabaseClient()
    val activity = context as? MainActivity

    DisposableEffect(activity) {
        activity?.registerAuthDeepLinkHandler {
            viewModel.refreshSession()
        }
        onDispose { activity?.clearAuthDeepLinkHandler() }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated) {
            onAuthenticated()
        }
    }

    when (uiState) {
        AuthUiState.Checking -> AuthLoadingScreen()
        is AuthUiState.Authenticated -> AuthLoadingScreen()
        else -> LoginScreen(
            supabaseClient = supabaseClient,
            onAuthenticated = onAuthenticated,
            viewModel = viewModel
        )
    }
}

@Composable
private fun AuthLoadingScreen() {
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
