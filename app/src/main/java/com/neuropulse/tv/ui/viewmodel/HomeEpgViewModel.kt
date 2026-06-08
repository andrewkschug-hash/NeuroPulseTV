package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.Recommendation
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeEpgViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    val channels: StateFlow<List<Channel>> = repository.channels(group = null, search = "", favoritesOnly = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<Channel>> = repository.continueWatching(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendations: StateFlow<List<Recommendation>> = repository.recommendedChannels(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsNow: StateFlow<List<Program>> = repository.liveSportsNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moviesSoon: StateFlow<List<Program>> = repository.moviesStartingSoon(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topChannels: StateFlow<List<Channel>> = repository.topChannels(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<Channel>> = repository.recentlyAdded(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vod: StateFlow<List<VodItem>> = repository.vodStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<SeriesShow>> = repository.seriesShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val now = MutableStateFlow(System.currentTimeMillis())

    private val _epgPrograms = MutableStateFlow<List<Program>>(emptyList())
    val epgPrograms = _epgPrograms.asStateFlow()

    private val _epgLoading = MutableStateFlow(false)
    val epgLoading = _epgLoading.asStateFlow()

    private var windowStart = System.currentTimeMillis() - 2 * 60 * 60 * 1000

    val programs: StateFlow<List<Program>> = channels
        .flatMapLatest { ch ->
            repository.programs(ch.mapNotNull { it.epgId }, now.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadWindow()
    }

    fun loadWindow() {
        viewModelScope.launch {
            _epgLoading.value = true
            val channelIds = channels.value.mapNotNull { it.epgId }
            val start = windowStart
            val end = start + 4 * 60 * 60 * 1000
            _epgPrograms.value = repository.programsWindow(channelIds, start, end)
            _epgLoading.value = false

            // Prefetch next 2-hour block.
            repository.programsWindow(channelIds, end, end + 2 * 60 * 60 * 1000)
        }
    }

    fun loadNextBlock() {
        windowStart += 2 * 60 * 60 * 1000
        loadWindow()
    }

    fun loadPrevBlock() {
        windowStart -= 2 * 60 * 60 * 1000
        loadWindow()
    }
}
