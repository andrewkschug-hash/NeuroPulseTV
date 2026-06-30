package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.tv.material3.Text
import com.grid.tv.BuildConfig
import com.grid.tv.ui.component.GridBrandWordmark
import com.grid.tv.ui.component.GridOutlinedButton
import com.grid.tv.ui.component.GoogleSignInBlock
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.SkipSignInDialog
import com.grid.tv.ui.component.rememberTvFocusChain
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.tvFocusChainNavigation
import com.grid.tv.ui.component.tvVerticalDpadNavigation
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import io.github.jan.supabase.SupabaseClient

@Composable
fun LoginScreen(
    supabaseClient: SupabaseClient,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val focusButtonCount = if (googleConfigured) 2 else 1
    val googleButtonIndex = 0
    val skipButtonIndex = if (googleConfigured) 1 else 0
    val focusChain = rememberTvFocusChain(count = focusButtonCount, startIndex = 0)
    val googleFocusRequester = if (googleConfigured) {
        focusChain.requesters[googleButtonIndex]
    } else {
        remember { androidx.compose.ui.focus.FocusRequester() }
    }
    var showSkipDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated || uiState is AuthUiState.Guest) {
            onAuthenticated()
        }
    }

    LaunchedEffect(showSkipDialog, googleConfigured) {
        if (!showSkipDialog && (uiState is AuthUiState.Unauthenticated || uiState is AuthUiState.Error)) {
            val target = if (googleConfigured) googleButtonIndex else skipButtonIndex
            focusChain.requesters[target].requestFocusSafelyAfterLayout()
        }
    }

    ScreenBackHandler(
        onNavigateBack = { },
        onBackPressed = {
            if (showSkipDialog) {
                showSkipDialog = false
                true
            } else {
                true
            }
        }
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
                text = "Sign in to sync across devices",
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .tvVerticalDpadNavigation(
                                chain = focusChain,
                                onBack = { showSkipDialog = true },
                                isEditing = { showSkipDialog },
                                onDismissEditing = { showSkipDialog = false }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusGroup(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!googleConfigured) {
                                Text(
                                    text = "Google Sign-In is not configured yet. Add GOOGLE_WEB_CLIENT_ID to .env and rebuild.",
                                    color = Color(0xFFFFB020),
                                    fontFamily = DmSansFamily,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                )
                            }

                            GoogleSignInBlock(
                                supabaseClient = supabaseClient,
                                viewModel = viewModel,
                                fillMaxWidthFraction = 1f,
                                focusRequester = googleFocusRequester,
                                requestInitialFocus = googleConfigured && !showSkipDialog,
                                enabled = googleConfigured && !showSkipDialog,
                                chain = if (googleConfigured) focusChain else null,
                                chainIndex = googleButtonIndex,
                                onNavigateBack = { showSkipDialog = true }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GridOutlinedButton(
                                text = "Skip for now",
                                onClick = { showSkipDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusChain.requesters[skipButtonIndex])
                                    .onFocusChanged {
                                        if (it.isFocused) focusChain.onItemFocused(skipButtonIndex)
                                    }
                                    .tvFocusChainNavigation(
                                        chain = focusChain,
                                        index = skipButtonIndex,
                                        onBack = { showSkipDialog = true }
                                    ),
                                height = 56.dp,
                                shape = RoundedCornerShape(10.dp),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                enabled = !showSkipDialog
                            )
                        }
                    }

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

                is AuthUiState.Authenticated, is AuthUiState.Guest -> Unit
            }
        }

        if (showSkipDialog) {
            SkipSignInDialog(
                onDismiss = { showSkipDialog = false },
                onConfirm = {
                    showSkipDialog = false
                    viewModel.confirmSkipForNow()
                }
            )
        }
    }
}
