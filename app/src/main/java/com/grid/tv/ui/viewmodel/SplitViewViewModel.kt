package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.player.PlayerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SplitViewViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val playerFactory: PlayerFactory
) : ViewModel() {

    companion object {
        const val MAX_PANES = 4
    }

    val channels: StateFlow<List<Channel>> = repository.channels(null, "", false, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = repository.channels(null, "", favoritesOnly = true, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = repository.recentChannels(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _paneChannelIds = MutableStateFlow<List<Long>>(emptyList())
    val paneChannelIds: StateFlow<List<Long>> = _paneChannelIds.asStateFlow()

    private val _resolvedChannels = MutableStateFlow<Map<Long, Channel>>(emptyMap())
    val paneChannels: StateFlow<List<Channel?>> = combine(_paneChannelIds, _resolvedChannels) { ids, resolved ->
        ids.map { resolved[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _audioPaneIndex = MutableStateFlow(0)
    val audioPaneIndex: StateFlow<Int> = _audioPaneIndex.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    fun loadPrimary(channelId: Long) {
        viewModelScope.launch {
            _loadFailed.value = false
            val channel = repository.channelById(channelId)
            if (channel == null) {
                _loadFailed.value = true
                return@launch
            }
            _resolvedChannels.value = _resolvedChannels.value + (channelId to channel)
            _paneChannelIds.value = listOf(channelId)
            _audioPaneIndex.value = 0
        }
    }

    fun addPane(channelId: Long) {
        if (_paneChannelIds.value.size >= MAX_PANES) return
        if (channelId in _paneChannelIds.value) return
        viewModelScope.launch {
            val channel = repository.channelById(channelId) ?: return@launch
            _resolvedChannels.value = _resolvedChannels.value + (channelId to channel)
            _paneChannelIds.value = _paneChannelIds.value + channelId
        }
    }

    fun removePane(index: Int) {
        val ids = _paneChannelIds.value
        if (ids.size <= 1 || index !in ids.indices) return
        val removedAudioIndex = _audioPaneIndex.value
        _paneChannelIds.value = ids.filterIndexed { i, _ -> i != index }
        _audioPaneIndex.value = when {
            removedAudioIndex == index -> 0
            removedAudioIndex > index -> removedAudioIndex - 1
            else -> removedAudioIndex
        }.coerceIn(0, _paneChannelIds.value.lastIndex)
    }

    fun makePrimary(index: Int) {
        val ids = _paneChannelIds.value.toMutableList()
        if (index !in ids.indices || index == 0) return
        val id = ids.removeAt(index)
        ids.add(0, id)
        val audioIndex = _audioPaneIndex.value
        _paneChannelIds.value = ids
        _audioPaneIndex.value = when (audioIndex) {
            index -> 0
            in 1 until index -> audioIndex - 1
            else -> audioIndex
        }.coerceIn(0, ids.lastIndex)
    }

    fun setAudioPane(index: Int) {
        if (index in _paneChannelIds.value.indices) {
            _audioPaneIndex.value = index
        }
    }

    @UnstableApi
    fun createPanePlayer(context: Context): ExoPlayer =
        playerFactory.create(context.applicationContext)
}
