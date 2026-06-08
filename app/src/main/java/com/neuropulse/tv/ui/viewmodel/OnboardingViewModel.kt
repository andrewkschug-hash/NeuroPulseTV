package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.PlaylistConnectResult
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.util.DeviceMacAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectState = MutableStateFlow(OnboardingConnectState.Idle)
    val connectState = _connectState.asStateFlow()

    private val _connectResult = MutableStateFlow<PlaylistConnectResult?>(null)
    val connectResult = _connectResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val deviceMac: String? = DeviceMacAddress.resolve()

    fun connectXtream(name: String, serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            val result = repository.connectXtreamPlaylist(name, serverUrl, username, password)
            handleResult(result)
        }
    }

    fun connectM3u(name: String, url: String) {
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            val result = repository.connectM3uPlaylist(name, url)
            handleResult(result)
        }
    }

    fun connectStalker(name: String, portalUrl: String, macAddress: String) {
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            _errorMessage.value = null
            val result = repository.connectStalkerPlaylist(name, portalUrl, macAddress)
            handleResult(result)
        }
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
        } else {
            _connectState.value = OnboardingConnectState.Error
            _errorMessage.value = result.errorMessage
        }
    }
}
