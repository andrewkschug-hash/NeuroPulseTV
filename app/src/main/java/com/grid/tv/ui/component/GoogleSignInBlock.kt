package com.grid.tv.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.BuildConfig
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@Composable
fun SettingsGoogleSignInButton(
    supabaseClient: SupabaseClient,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
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

    SettingsFocusButton(
        text = "Sign in with Google",
        onClick = {
            viewModel.clearError()
            viewModel.onGoogleSignInStarted()
            googleSignIn.startFlow()
        },
        focusRequester = focusRequester,
        modifier = modifier,
        enabled = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    )
}

@Composable
fun GoogleSignInBlock(
    supabaseClient: SupabaseClient,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    buttonLabel: String = "Continue with Google",
    fillMaxWidthFraction: Float = 1f,
    focusRequester: FocusRequester = remember { FocusRequester() },
    requestInitialFocus: Boolean = false,
    enabled: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
) {
    var focused by remember { mutableStateOf(false) }

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

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val shape = RoundedCornerShape(10.dp)
    GridFocusSurface(
        onClick = {
            viewModel.clearError()
            viewModel.onGoogleSignInStarted()
            googleSignIn.startFlow()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth(fillMaxWidthFraction)
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
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
                text = buttonLabel,
                color = if (enabled) Color.White else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
