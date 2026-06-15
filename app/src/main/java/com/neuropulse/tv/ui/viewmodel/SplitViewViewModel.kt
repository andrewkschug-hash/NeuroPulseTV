package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SplitViewViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    val channels: StateFlow<List<Channel>> = repository.channels(null, "", false, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = repository.channels(null, "", favoritesOnly = true, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = repository.recentChannels(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _primaryChannel = MutableStateFlow<Channel?>(null)
    val primaryChannel: StateFlow<Channel?> = _primaryChannel.asStateFlow()

    private val _secondaryChannel = MutableStateFlow<Channel?>(null)
    val secondaryChannel: StateFlow<Channel?> = _secondaryChannel.asStateFlow()

    fun loadPrimary(channelId: Long) {
        viewModelScope.launch {
            _primaryChannel.value = repository.channelById(channelId)
        }
    }

    fun selectSecondary(channelId: Long) {
        viewModelScope.launch {
            _secondaryChannel.value = repository.channelById(channelId)
        }
    }
}
