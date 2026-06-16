package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.data.repository.ContinueWatchingRepository
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.recommendation.TasteGenomeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VodHubViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository
) : ViewModel() {
    private val tasteGenomeEngine = TasteGenomeEngine()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        continueWatchingRepository.observeItems(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val vodCatalog: StateFlow<List<VodItem>> =
        repository.vodStreams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Long, Long>> =
        repository.vodWatchProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val topPicksForYou: StateFlow<List<VodItem>> =
        combine(vodCatalog, continueWatchingItems) { catalog, cw ->
            tasteGenomeEngine.topPicks(catalog, cw, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somethingDifferent: StateFlow<List<VodItem>> =
        combine(vodCatalog, continueWatchingItems) { catalog, cw ->
            tasteGenomeEngine.somethingDifferent(catalog, cw, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runCatching { repository.refreshVodSeriesCatalog() }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
