package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.epg.ChannelCategoryPresets
import com.neuropulse.tv.ui.component.PlayerSideMenuSportItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel.asStateFlow()

    private val _numberInput = MutableStateFlow("")
    val numberInput = _numberInput.asStateFlow()

    private var previousChannelId: Long? = null
    private val _hasPreviousChannel = MutableStateFlow(false)
    val hasPreviousChannel: StateFlow<Boolean> = _hasPreviousChannel.asStateFlow()

    val channels: StateFlow<List<Channel>> = repository.channels(group = null, search = "", favoritesOnly = false)
        .map { list -> list.distinctBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsNow: StateFlow<List<Program>> = repository.liveSportsNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsMenuItems: StateFlow<List<PlayerSideMenuSportItem>> = combine(channels, sportsNow) { chs, progs ->
        progs.mapNotNull { prog ->
            chs.find { it.epgId == prog.channelEpgId }?.let { ch ->
                PlayerSideMenuSportItem(channel = ch, programTitle = prog.title)
            }
        }.distinctBy { it.channel.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val newsChannels: StateFlow<List<Channel>> = channels
        .map { chs ->
            ChannelCategoryPresets.apply(chs, ChannelCategoryPresets.fromPreset("news")).take(25)
        }
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
        val current = _channel.value ?: return
        val prevId = previousChannelId ?: return
        previousChannelId = current.id
        viewModelScope.launch {
            applyChannel(prevId)
            _hasPreviousChannel.value = true
        }
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
            _hasPreviousChannel.value = true
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
