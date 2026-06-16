package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.data.repository.ContinueWatchingRepository
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.enrichment.TitleEnrichmentRepository
import com.neuropulse.tv.feature.recommendation.TasteGenomeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VodHubViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val titleEnrichmentRepository: TitleEnrichmentRepository
) : ViewModel() {
    private val tasteGenomeEngine = TasteGenomeEngine()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val enrichmentByKey = MutableStateFlow<Map<String, com.neuropulse.tv.data.db.entity.TitleEnrichmentEntity>>(emptyMap())

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        continueWatchingRepository.observeItems(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val vodCatalog: StateFlow<List<VodItem>> =
        repository.vodStreams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Long, Long>> =
        repository.vodWatchProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val topPicksForYou: StateFlow<List<VodItem>> =
        combine(vodCatalog, continueWatchingItems, enrichmentByKey) { catalog, cw, enrichment ->
            tasteGenomeEngine.topPicks(catalog, cw, enrichment, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somethingDifferent: StateFlow<List<VodItem>> =
        combine(vodCatalog, continueWatchingItems, enrichmentByKey) { catalog, cw, enrichment ->
            tasteGenomeEngine.somethingDifferent(catalog, cw, enrichment, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshCatalog()
        viewModelScope.launch {
            continueWatchingItems.collect { items ->
                prefetchEnrichmentForContinueWatching(items)
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runCatching { repository.refreshVodSeriesCatalog() }
        }
    }

    fun enrichOnBrowse(item: VodItem) {
        if (item.playlistId <= 0L) return
        viewModelScope.launch {
            val key = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)
            val entity = titleEnrichmentRepository.enrichOnDemand(
                providerKey = key,
                title = item.title,
                releaseYear = parseYear(item.title),
                isTv = false
            ) ?: return@launch
            enrichmentByKey.value = enrichmentByKey.value + (key to entity)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private suspend fun prefetchEnrichmentForContinueWatching(items: List<ContinueWatchingItem>) {
        if (items.isEmpty()) return
        val keys = items.map { TitleEnrichmentRepository.continueWatchingKey(it) }
        val cached = titleEnrichmentRepository.getCachedBatch(keys)
        enrichmentByKey.value = enrichmentByKey.value + cached
        items.forEach { item ->
            titleEnrichmentRepository.enrichContinueWatching(item)?.let { entity ->
                enrichmentByKey.value = enrichmentByKey.value + (entity.providerKey to entity)
            }
        }
    }

    private fun parseYear(value: String): Int? {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(value) ?: return null
        return match.value.toIntOrNull()
    }
}
