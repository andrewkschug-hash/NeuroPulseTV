package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.BuildConfig
import com.grid.tv.ui.component.GridBrandWordmark
import com.grid.tv.ui.component.GridFocusSurface
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.tvFocusBorder
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@Composable
fun LoginScreen(
    supabaseClient: SupabaseClient,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleButtonFocus = remember { FocusRequester() }
    val googleConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    val googleSignIn = supabaseClient.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                NativeSignInResult.ClosedByUser -> viewModel.onGoogleSignInCancelled()
                is NativeSignInResult.Error -> viewModel.onGoogleSignInFailed(
                    result.message ?: "Google sign-in failed. Please try again."
                )
                is NativeSignInResult.NetworkError -> viewModel.onGoogleSignInFailed(
                    "Network error during sign-in. Check your connection and try again."
                )
                is NativeSignInResult.Success -> viewModel.onGoogleSignInSuccess()
            }
        }
    )

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated) {
            onAuthenticated()
        }
    }

    LaunchedEffect(Unit) {
        googleButtonFocus.requestFocusSafelyAfterLayout()
    }

    ScreenBackHandler(
        onNavigateBack = { },
        onBackPressed = { true }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050508),
                        Color(0xFF101018),
                        Color(0xFF050508)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GridBrandWordmark(
                fontSize = 56.sp,
                letterSpacing = 18.sp,
                dividerWidth = 180.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Sign in to start watching",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            when (val state = uiState) {
                is AuthUiState.Checking, is AuthUiState.SigningIn -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = EpgColors.Accent,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = if (state is AuthUiState.SigningIn) {
                                "Signing in with ${state.providerLabel}…"
                            } else {
                                "Checking your session…"
                            },
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        )
                    }
                }

                is AuthUiState.Unauthenticated, is AuthUiState.Error -> {
                    var focused by remember { mutableStateOf(false) }

                    if (!googleConfigured) {
                        Text(
                            text = "Google Sign-In is not configured yet. Add GOOGLE_WEB_CLIENT_ID to .env and rebuild.",
                            color = Color(0xFFFFB020),
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    GoogleSignInButton(
                        enabled = googleConfigured,
                        focused = focused,
                        onFocused = { focused = it },
                        focusRequester = googleButtonFocus,
                        onClick = {
                            viewModel.clearError()
                            viewModel.onGoogleSignInStarted()
                            googleSignIn.startFlow()
                        }
                    )

                    if (state is AuthUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Color(0xFFFF8A80),
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                }

                is AuthUiState.Authenticated -> Unit
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    focused: Boolean,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(0.42f)
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocused(it.isFocused) }
            .tvFocusBorder(focused = focused, shape = shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1C1C28),
            focusedContainerColor = Color(0xFF242433),
            disabledContainerColor = Color(0xFF14141C)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Continue with Google",
                color = if (enabled) Color.White else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
