package com.grid.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.MainActivity
import com.grid.tv.di.SupabaseEntryPoint
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.screen.LoginScreen
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.SupabaseClient

@Composable
fun AuthGate(
    onAuthenticated: () -> Unit,
    blocking: Boolean = true,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val supabaseProvider = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SupabaseEntryPoint::class.java
        ).supabaseClientProvider()
    }
    val activity = context as? MainActivity

    LaunchedEffect(supabaseProvider.isConfigured) {
        if (!supabaseProvider.isConfigured) {
            viewModel.confirmSkipForNow()
        }
    }

    var supabaseClient by remember { mutableStateOf<SupabaseClient?>(null) }
    LaunchedEffect(supabaseProvider) {
        supabaseClient = withContext(Dispatchers.IO) {
            supabaseProvider.clientOrNull()
        }
    }

    DisposableEffect(activity) {
        activity?.registerAuthDeepLinkHandler {
            viewModel.onOAuthSessionEstablished()
        }
        onDispose { activity?.clearAuthDeepLinkHandler() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onSignInResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated || uiState is AuthUiState.Guest) {
            onAuthenticated()
        }
    }

    when (uiState) {
        AuthUiState.Checking -> if (blocking) AuthLoadingScreen()
        is AuthUiState.Authenticated,
        is AuthUiState.Guest -> if (blocking) AuthLoadingScreen()
        else -> {
            val client = supabaseClient
            if (client != null) {
                LoginScreen(
                    supabaseClient = client,
                    onAuthenticated = onAuthenticated,
                    viewModel = viewModel
                )
            } else {
                LocalOnlyAuthScreen(
                    onContinue = { viewModel.confirmSkipForNow() }
                )
            }
        }
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

@Composable
private fun LocalOnlyAuthScreen(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "GRID",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 28.sp
            )
            Text(
                text = "Cloud sign-in is not configured.\nContinuing in local mode.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            GlowFocusButton(onClick = onContinue) {
                Text("Continue")
            }
        }
    }
}
