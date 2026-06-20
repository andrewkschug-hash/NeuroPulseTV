package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.buildVodWallRows
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.feature.recommendation.TasteGenomeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VodHubViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val playlistImportCoordinator: PlaylistImportCoordinator
) : ViewModel() {
    private val tasteGenomeEngine = TasteGenomeEngine()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val enrichmentByKey = MutableStateFlow<Map<String, TitleEnrichmentEntity>>(emptyMap())
    val enrichmentMap: StateFlow<Map<String, TitleEnrichmentEntity>> = enrichmentByKey.asStateFlow()

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        continueWatchingRepository.observeItems(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val recommendationSample: StateFlow<List<VodItem>> =
        repository.vodCatalogRevision()
            .flatMapLatest {
                flow { emit(repository.vodSampleForRecommendations(sampleSize = 500)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Long, Long>> =
        repository.vodWatchProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recommendedForYou: StateFlow<List<VodItem>> =
        combine(recommendationSample, continueWatchingItems, enrichmentByKey) { catalog, cw, enrichment ->
            tasteGenomeEngine.topPicks(catalog, cw, enrichment, limit = 24)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingNow: StateFlow<List<VodItem>> =
        combine(recommendationSample, enrichmentByKey) { catalog, enrichment ->
            tasteGenomeEngine.trendingNow(catalog, enrichment, limit = 24)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somethingDifferent: StateFlow<List<VodItem>> =
        combine(recommendationSample, continueWatchingItems, enrichmentByKey) { catalog, cw, enrichment ->
            tasteGenomeEngine.somethingDifferent(catalog, cw, enrichment, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredCarousel: StateFlow<List<VodItem>> =
        combine(recommendedForYou, trendingNow, repository.vodCatalogRevision()) { recommended, trending, _ ->
            val merged = (recommended.take(5) + trending.take(5))
                .distinctBy { "${it.playlistId}_${it.streamId}" }
                .take(5)
            merged
        }.flatMapLatest { merged ->
            if (merged.isNotEmpty()) {
                flow { emit(merged) }
            } else {
                flow { emit(repository.vodRecent(limit = 5)) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _heroIndex = MutableStateFlow(0)
    val heroIndex: StateFlow<Int> = _heroIndex.asStateFlow()

    private val _contentFilter = MutableStateFlow(VodContentFilter.ALL)
    val contentFilter: StateFlow<VodContentFilter> = _contentFilter.asStateFlow()

    fun setContentFilter(filter: VodContentFilter) {
        _contentFilter.value = filter
    }

    val heroMovie: StateFlow<VodItem?> =
        combine(featuredCarousel, _heroIndex) { carousel, index ->
            carousel.getOrNull(index.coerceIn(0, (carousel.size - 1).coerceAtLeast(0)))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val heroEnrichment: StateFlow<TitleEnrichmentEntity?> =
        combine(heroMovie, enrichmentByKey) { movie, map ->
            movie?.let { enrichmentFor(it, map) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            if (!playlistImportCoordinator.isImportActive()) {
                repository.ensureVodCatalogLoaded(VodRefreshTrigger.VOD_HUB_MOUNT)
            }
        }
        viewModelScope.launch {
            playlistImportCoordinator.importActive.collect { importing ->
                if (!importing) {
                    repository.ensureVodCatalogLoaded(VodRefreshTrigger.VOD_HUB_MOUNT)
                }
            }
        }
        viewModelScope.launch {
            continueWatchingItems.collect { items ->
                if (!playlistImportCoordinator.isImportActive()) {
                    prefetchEnrichmentForContinueWatching(items)
                }
            }
        }
        viewModelScope.launch {
            recommendationSample.collect { catalog ->
                if (catalog.isNotEmpty() && !playlistImportCoordinator.isImportActive()) {
                    prefetchEnrichmentForCatalog(catalog.take(40))
                }
            }
        }
        viewModelScope.launch {
            featuredCarousel.collect { carousel ->
                if (!playlistImportCoordinator.isImportActive()) {
                    carousel.forEach { prefetchEnrichmentForItem(it) }
                }
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runCatching {
                repository.refreshVodSeriesCatalog(
                    trigger = VodRefreshTrigger.MANUAL_RETRY,
                    force = true
                )
            }
        }
    }

    fun setHeroIndex(index: Int) {
        _heroIndex.value = index.coerceAtLeast(0)
    }

    fun advanceHeroCarousel() {
        val size = featuredCarousel.value.size
        if (size <= 1) return
        _heroIndex.value = (_heroIndex.value + 1) % size
    }

    fun enrichOnBrowse(item: VodItem) {
        prefetchEnrichmentForItem(item)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun enrichmentFor(item: VodItem, map: Map<String, TitleEnrichmentEntity> = enrichmentByKey.value): TitleEnrichmentEntity? {
        if (item.playlistId <= 0L) return null
        return map[TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)]
    }

    fun displayRating(item: VodItem, enrichment: TitleEnrichmentEntity?): String? {
        enrichment?.rating?.takeIf { it > 0.0 }?.let { return String.format("%.1f", it) }
        return item.rating?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun prefetchEnrichmentForItem(item: VodItem) {
        if (item.playlistId <= 0L) return
        viewModelScope.launch {
            val key = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)
            if (enrichmentByKey.value.containsKey(key)) return@launch
            val entity = titleEnrichmentRepository.enrichOnDemand(
                providerKey = key,
                title = item.title,
                releaseYear = parseYear(item.title),
                isTv = false
            ) ?: return@launch
            enrichmentByKey.value = enrichmentByKey.value + (key to entity)
        }
    }

    private suspend fun prefetchEnrichmentForCatalog(items: List<VodItem>) {
        items.forEach { item -> prefetchEnrichmentForItem(item) }
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
