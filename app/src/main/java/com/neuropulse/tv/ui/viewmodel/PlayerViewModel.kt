package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun load(channelId: Long) {
        viewModelScope.launch {
            _channel.value = repository.channelById(channelId)
        }
    }

    fun switchNext() {
        val current = _channel.value ?: return
        viewModelScope.launch {
            _channel.value = repository.channelByNumber(current.number + 1) ?: current
        }
    }

    fun switchPrev() {
        val current = _channel.value ?: return
        viewModelScope.launch {
            _channel.value = repository.channelByNumber((current.number - 1).coerceAtLeast(1)) ?: current
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
            _channel.value = repository.channelByNumber(number) ?: _channel.value
        }
    }

    fun clearNumberInput() {
        _numberInput.value = ""
    }

    fun savePosition(positionMs: Long) {
        val id = _channel.value?.id ?: return
        viewModelScope.launch { repository.saveWatchPosition(id, positionMs) }
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
