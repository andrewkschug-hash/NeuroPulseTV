package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object Unauthenticated : AuthUiState
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

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Checking
            runCatching {
                val account = authRepository.getCurrentAccount()
                if (account != null) {
                    authRepository.ensureCloudProfile()
                    _uiState.value = AuthUiState.Authenticated(account.userId)
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(
                    error.message ?: "We couldn't verify your session. Please try again."
                )
            }
        }
    }

    fun onGoogleSignInStarted() {
        _uiState.value = AuthUiState.SigningIn("Google")
    }

    fun onGoogleSignInSuccess() {
        viewModelScope.launch {
            runCatching {
                authRepository.ensureCloudProfile()
                val account = authRepository.getCurrentAccount()
                if (account != null) {
                    _uiState.value = AuthUiState.Authenticated(account.userId)
                } else {
                    _uiState.value = AuthUiState.Error("Sign-in succeeded but no session was found.")
                }
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(
                    error.message ?: "We couldn't finish signing you in. Please try again."
                )
            }
        }
    }

    fun onGoogleSignInCancelled() {
        _uiState.value = AuthUiState.Unauthenticated
    }

    fun onGoogleSignInFailed(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _uiState.value = AuthUiState.Unauthenticated
            onComplete()
        }
    }

    fun handleOAuthCallback(url: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.SigningIn("Google")
            val success = runCatching { authRepository.handleOAuthCallback(url) }
                .getOrDefault(false)
            if (success) {
                onGoogleSignInSuccess()
            } else {
                _uiState.value = AuthUiState.Error("Sign-in was interrupted. Please try again.")
            }
        }
    }

    fun startOAuthFallback() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.SigningIn("Google")
            runCatching {
                authRepository.signInWithOAuth(com.grid.tv.domain.model.OAuthProviderId.Google)
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(
                    error.message ?: "Google sign-in failed. Please try again."
                )
            }
        }
    }
}
