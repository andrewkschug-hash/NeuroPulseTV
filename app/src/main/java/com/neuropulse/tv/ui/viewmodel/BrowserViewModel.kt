package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val sportsOnly = MutableStateFlow(false)

    val groups = repository.groups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: StateFlow<List<Channel>> = combine(selectedCategory, favoritesOnly, sportsOnly) { group, fav, sports -> Triple(group, fav, sports) }
        .flatMapLatest { (group, fav, sports) ->
            repository.channels(group, search = "", favoritesOnly = fav).let { flow ->
                if (!sports) flow else kotlinx.coroutines.flow.combine(flow, repository.liveSportsNow()) { ch, pr ->
                    val epgIds = pr.map { it.channelEpgId }.toSet()
                    ch.filter { it.epgId in epgIds }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsPrograms: StateFlow<List<Program>> = repository.liveSportsNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectAll() {
        selectedCategory.value = null
        favoritesOnly.value = false
        sportsOnly.value = false
    }

    fun selectFavorites() {
        favoritesOnly.value = true
        sportsOnly.value = false
    }

    fun selectGroup(group: String) {
        favoritesOnly.value = false
        sportsOnly.value = false
        selectedCategory.value = group
    }

    fun selectSports() {
        favoritesOnly.value = false
        selectedCategory.value = null
        sportsOnly.value = true
    }

    fun toggleFavorite(channelId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(channelId, enabled) }
    }
}
