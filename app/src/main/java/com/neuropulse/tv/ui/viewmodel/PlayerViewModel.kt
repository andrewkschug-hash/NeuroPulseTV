package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.player.PictureInPictureController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IptvRepository,
    val pipController: PictureInPictureController
) : ViewModel() {

    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel.asStateFlow()

    private val _numberInput = MutableStateFlow("")
    val numberInput = _numberInput.asStateFlow()

    private var previousChannelId: Long? = null
    private val _lastWatchedChannel = MutableStateFlow<Channel?>(null)
    val lastWatchedChannel: StateFlow<Channel?> = _lastWatchedChannel.asStateFlow()

    val channels: StateFlow<List<Channel>> = repository.channels(group = null, search = "", favoritesOnly = false)
        .map { list -> list.distinctBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = channels
        .map { list -> list.filter { it.isFavorite }.distinctBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(channelId: Long) {
        viewModelScope.launch {
            applyChannel(channelId, recordWatchHistory = true)
        }
    }

    fun tuneChannel(channelId: Long) {
        viewModelScope.launch { applyChannel(channelId) }
    }

    fun switchToPreviousChannel() {
        val prevId = previousChannelId ?: return
        viewModelScope.launch { applyChannel(prevId) }
    }

    fun switchNext() {
        val current = _channel.value ?: return
        viewModelScope.launch {
            repository.channelByNumber(current.number + 1)?.let { applyChannel(it.id) }
        }
    }

    fun switchPrev() {
        val current = _channel.value ?: return
        viewModelScope.launch {
            repository.channelByNumber((current.number - 1).coerceAtLeast(1))?.let { applyChannel(it.id) }
        }
    }

    private suspend fun applyChannel(channelId: Long, recordWatchHistory: Boolean = true) {
        val current = _channel.value
        if (current != null && current.id != channelId) {
            previousChannelId = current.id
            _lastWatchedChannel.value = current
        }
        val ch = repository.channelById(channelId)
        _channel.value = ch
        if (recordWatchHistory && ch != null) {
            val history = repository.watchHistory(ch.id)
            repository.saveWatchPosition(
                channelId = ch.id,
                position = history?.lastPosition ?: 0L,
                programTitle = ch.currentProgram ?: ch.name
            )
        }
    }

    fun appendDigit(d: Int) {
        val next = (_numberInput.value + d.toString()).take(4)
        _numberInput.value = next
    }

    fun jumpByNumber() {
        val number = _numberInput.value.toIntOrNull() ?: return
        _numberInput.value = ""
        viewModelScope.launch {
            repository.channelByNumber(number)?.let { applyChannel(it.id) }
        }
    }

    fun clearNumberInput() {
        _numberInput.value = ""
    }

    fun savePosition(positionMs: Long) {
        val ch = _channel.value ?: return
        viewModelScope.launch {
            repository.saveWatchPosition(ch.id, positionMs, ch.currentProgram ?: ch.name)
        }
    }

    fun reportStreamHealth(loadMs: Long, bufferEvents: Int, success: Boolean) {
        val id = _channel.value?.id ?: return
        viewModelScope.launch {
            repository.reportStreamSession(id, loadMs, bufferEvents, success)
        }
    }

    suspend fun lastPosition(): Long {
        val id = _channel.value?.id ?: return 0L
        return repository.watchHistory(id)?.lastPosition ?: 0L
    }
}
