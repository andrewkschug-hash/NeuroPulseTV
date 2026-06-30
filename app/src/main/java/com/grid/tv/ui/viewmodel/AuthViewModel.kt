package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.AuthAccount
import com.grid.tv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object Unauthenticated : AuthUiState
    /** User chose to skip sign-in; app runs locally without cloud sync. */
    data object Guest : AuthUiState
    data class Authenticated(val userId: String) : AuthUiState
    data class SigningIn(val providerLabel: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _signedInAccount = MutableStateFlow<AuthAccount?>(null)
    val signedInAccount: StateFlow<AuthAccount?> = _signedInAccount.asStateFlow()

    private var signInTimeoutJob: Job? = null

    val isSignedIn: Boolean
        get() = _uiState.value is AuthUiState.Authenticated

    init {
        observeAuthState()
        refreshSession()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { isAuthenticated ->
                if (isAuthenticated && shouldCompleteSignIn()) {
                    completeSignIn()
                }
            }
        }
    }

    private fun shouldCompleteSignIn(): Boolean {
        return when (_uiState.value) {
            is AuthUiState.SigningIn,
            is AuthUiState.Error,
            AuthUiState.Unauthenticated -> true
            else -> false
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            if (_uiState.value !is AuthUiState.SigningIn) {
                _uiState.value = AuthUiState.Checking
            }
            runCatching {
                val account = authRepository.getCurrentAccount()
                if (account != null) {
                    authRepository.setSkippedSignIn(false)
                    runCatching { authRepository.ensureCloudProfile() }
                    _signedInAccount.value = account
                    _uiState.value = AuthUiState.Authenticated(account.userId)
                } else if (authRepository.hasSkippedSignIn()) {
                    _signedInAccount.value = null
                    _uiState.value = AuthUiState.Guest
                } else if (_uiState.value !is AuthUiState.SigningIn) {
                    _signedInAccount.value = null
                    _uiState.value = AuthUiState.Unauthenticated
                }
            }.onFailure { error ->
                if (_uiState.value !is AuthUiState.SigningIn) {
                    _uiState.value = AuthUiState.Error(
                        error.message ?: "We couldn't verify your session. Please try again."
                    )
                }
            }
        }
    }

    fun onOAuthSessionEstablished() {
        viewModelScope.launch {
            repeat(15) {
                if (completeSignIn()) return@launch
                delay(200)
            }
            if (_uiState.value is AuthUiState.SigningIn) {
                clearSigningInTimeout()
                _uiState.value = AuthUiState.Error("Sign-in was interrupted. Please try again.")
            }
        }
    }

    fun confirmSkipForNow() {
        authRepository.setSkippedSignIn(true)
        _signedInAccount.value = null
        _uiState.value = AuthUiState.Guest
    }

    fun onGoogleSignInStarted() {
        beginSigningIn("Google")
    }

    fun onSignInResumed() {
        if (_uiState.value is AuthUiState.SigningIn) {
            onOAuthSessionEstablished()
        }
    }

    private fun beginSigningIn(providerLabel: String) {
        _uiState.value = AuthUiState.SigningIn(providerLabel)
        signInTimeoutJob?.cancel()
        signInTimeoutJob = viewModelScope.launch {
            delay(SIGN_IN_TIMEOUT_MS)
            if (_uiState.value is AuthUiState.SigningIn) {
                _uiState.value = AuthUiState.Error("Sign-in timed out. Please try again.")
            }
        }
    }

    private fun clearSigningInTimeout() {
        signInTimeoutJob?.cancel()
        signInTimeoutJob = null
    }

    fun onGoogleSignInSuccess() {
        viewModelScope.launch {
            if (!completeSignIn()) {
                onOAuthSessionEstablished()
            }
        }
    }

    private suspend fun completeSignIn(): Boolean {
        if (_uiState.value is AuthUiState.Authenticated) return true
        return runCatching {
            clearSigningInTimeout()
            authRepository.setSkippedSignIn(false)
            val account = authRepository.getCurrentAccount()
                ?: return false
            runCatching { authRepository.ensureCloudProfile() }
            _signedInAccount.value = account
            _uiState.value = AuthUiState.Authenticated(account.userId)
            true
        }.getOrElse { error ->
            clearSigningInTimeout()
            _uiState.value = AuthUiState.Error(
                error.message ?: "We couldn't finish signing you in. Please try again."
            )
            false
        }
    }

    fun onGoogleSignInCancelled() {
        clearSigningInTimeout()
        if (authRepository.hasSkippedSignIn()) {
            _uiState.value = AuthUiState.Guest
        } else {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    fun onGoogleSignInFailed(message: String) {
        clearSigningInTimeout()
        _uiState.value = AuthUiState.Error(message)
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = if (authRepository.hasSkippedSignIn()) {
                AuthUiState.Guest
            } else {
                AuthUiState.Unauthenticated
            }
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _signedInAccount.value = null
            _uiState.value = AuthUiState.Unauthenticated
            onComplete()
        }
    }

    fun handleOAuthCallback(@Suppress("UNUSED_PARAMETER") url: String) {
        onOAuthSessionEstablished()
    }

    fun startOAuthFallback() {
        viewModelScope.launch {
            beginSigningIn("Google")
            runCatching {
                authRepository.signInWithOAuth(com.grid.tv.domain.model.OAuthProviderId.Google)
            }.onFailure { error ->
                clearSigningInTimeout()
                _uiState.value = AuthUiState.Error(
                    error.message ?: "Google sign-in failed. Please try again."
                )
            }
        }
    }

    companion object {
        private const val SIGN_IN_TIMEOUT_MS = 120_000L
    }
}
