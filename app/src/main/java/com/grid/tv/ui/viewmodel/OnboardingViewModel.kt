package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistConnectResult
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.util.CONNECTION_TIMEOUT_ERROR
import com.grid.tv.util.connectionTimeoutMs
import com.grid.tv.util.DeviceMacAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingConnectState {
    Idle,
    Connecting,
    Success,
    Error
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    companion object {
        const val CONNECT_ERROR = "Login or URL invalid"
    }

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectState = MutableStateFlow(OnboardingConnectState.Idle)
    val connectState = _connectState.asStateFlow()

    private val _connectResult = MutableStateFlow<PlaylistConnectResult?>(null)
    val connectResult = _connectResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val deviceMac: String? = DeviceMacAddress.resolve()

    suspend fun hasActiveConnection(): Boolean = repository.hasActiveConnection()

    fun connectXtream(name: String, serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            showConnectError()
            return
        }
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            handleResult(
                runConnectWithTimeout {
                    repository.connectXtreamPlaylist(name, serverUrl, username, password)
                }
            )
        }
    }

    fun connectM3u(name: String, url: String) {
        if (url.isBlank()) {
            showConnectError()
            return
        }
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            handleResult(
                runConnectWithTimeout {
                    repository.connectM3uPlaylist(name, url)
                }
            )
        }
    }

    fun connectStalker(name: String, portalUrl: String, macAddress: String) {
        if (portalUrl.isBlank() || macAddress.isBlank()) {
            showConnectError()
            return
        }
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            handleResult(
                runConnectWithTimeout {
                    repository.connectStalkerPlaylist(name, portalUrl, macAddress)
                }
            )
        }
    }

    private suspend fun runConnectWithTimeout(
        block: suspend () -> PlaylistConnectResult
    ): PlaylistConnectResult {
        val timeoutMs = connectionTimeoutMs(repository.loadSettings().connectionTimeoutSeconds)
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            PlaylistConnectResult(
                success = false,
                playlistName = "",
                errorMessage = CONNECTION_TIMEOUT_ERROR
            )
        }
    }

    private fun showConnectError() {
        _connectState.value = OnboardingConnectState.Error
        _errorMessage.value = CONNECT_ERROR
    }

    fun resetConnectState() {
        _connectState.value = OnboardingConnectState.Idle
        _errorMessage.value = null
    }

    private fun handleResult(result: PlaylistConnectResult) {
        _connectResult.value = result
        if (result.success) {
            _connectState.value = OnboardingConnectState.Success
            _errorMessage.value = null
            viewModelScope.launch {
                runCatching { repository.refreshVodSeriesCatalog() }
            }
        } else {
            _connectState.value = OnboardingConnectState.Error
            _errorMessage.value = result.errorMessage
        }
    }
}
