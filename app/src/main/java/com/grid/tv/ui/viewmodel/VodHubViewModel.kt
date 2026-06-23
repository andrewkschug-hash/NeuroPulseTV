package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.feature.recommendation.FeaturedContentRanker
import com.grid.tv.feature.recommendation.TasteGenomeEngine
import com.grid.tv.feature.startup.StartupTierPolicy
import com.grid.tv.feature.vod.curation.FeaturedCurationRepository
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.util.runVodPipelineCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@HiltViewModel
class VodHubViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val playlistImportCoordinator: PlaylistImportCoordinator,
    private val featuredCurationRepository: FeaturedCurationRepository,
    private val languagePreferenceStore: VodLanguagePreferenceStore
) : ViewModel() {
    private val tasteGenomeEngine = TasteGenomeEngine()
    private val featuredContentRanker = FeaturedContentRanker()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val enrichmentByKey = MutableStateFlow<Map<String, TitleEnrichmentEntity>>(emptyMap())
    val enrichmentMap: StateFlow<Map<String, TitleEnrichmentEntity>> = enrichmentByKey.asStateFlow()

    val preferredVodLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _availableVodLanguages = MutableStateFlow<List<String>>(emptyList())
    val availableVodLanguages: StateFlow<List<String>> = _availableVodLanguages.asStateFlow()

    // Resume history is profile-scoped; do not apply VOD language filters here.
    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        continueWatchingRepository.observeItems(limit = 20)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recommendationSample: StateFlow<List<VodItem>> =
        repository.vodCatalogRevision()
            .flatMapLatest {
                flow {
                    delay(StartupTierPolicy.tier2DelayMs())
                    val sampleSize = StartupTierPolicy.recommendationSampleSize()
                    emit(
                        withContext(Dispatchers.IO) {
                            repository.vodSampleForRecommendations(sampleSize)
                        }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val vodCategories = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Long, Long>> =
        repository.vodWatchProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recommendedForYou: StateFlow<List<VodItem>> =
        combine(recommendationSample, continueWatchingItems, enrichmentByKey, preferredVodLanguages, vodCategories) { catalog, cw, enrichment, languages, categories ->
            val categoryNames = categories.associate { it.id to it.name }
            tasteGenomeEngine.topPicks(filterMoviesByLanguage(catalog, languages, categoryNames), cw, enrichment, limit = 24)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingNow: StateFlow<List<VodItem>> =
        combine(recommendationSample, enrichmentByKey, preferredVodLanguages, vodCategories) { catalog, enrichment, languages, categories ->
            val categoryNames = categories.associate { it.id to it.name }
            tasteGenomeEngine.trendingNow(filterMoviesByLanguage(catalog, languages, categoryNames), enrichment, limit = 24)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somethingDifferent: StateFlow<List<VodItem>> =
        combine(recommendationSample, continueWatchingItems, enrichmentByKey, preferredVodLanguages, vodCategories) { catalog, cw, enrichment, languages, categories ->
            val categoryNames = categories.associate { it.id to it.name }
            tasteGenomeEngine.somethingDifferent(filterMoviesByLanguage(catalog, languages, categoryNames), cw, enrichment, limit = 20)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _featuredCarousel = MutableStateFlow<List<VodItem>>(emptyList())
    val featuredCarousel: StateFlow<List<VodItem>> = _featuredCarousel.asStateFlow()

    private val _heroIndex = MutableStateFlow(0)
    val heroIndex: StateFlow<Int> = _heroIndex.asStateFlow()

    private var featuredSessionKey: Pair<Long, Long>? = null
    private var lastRecordedImpressionKey: String? = null

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
            delay(StartupTierPolicy.tier2DelayMs())
            recommendationSample.collect { catalog ->
                com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubRecommendations", catalog.size)
                if (catalog.isNotEmpty() && !playlistImportCoordinator.isImportActive()) {
                    prefetchEnrichmentForCatalog(catalog.take(if (LowEndDeviceMode.current().active) 20 else 40))
                }
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
            featuredCarousel.collect { carousel ->
                if (!playlistImportCoordinator.isImportActive() && carousel.isNotEmpty()) {
                    val prefetchCount = if (LowEndDeviceMode.current().active) 2 else carousel.size
                    carousel.take(prefetchCount).forEach { prefetchEnrichmentForItem(it) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                combine(
                    recommendationSample,
                    enrichmentByKey,
                    vodCategories,
                    repository.vodCatalogRevision(),
                    featuredCurationRepository.observeActiveProfileId()
                ) { catalog, enrichment, categories, catalogRevision, profileId ->
                    FeaturedCatalogInputs(
                        catalog = catalog,
                        enrichment = enrichment,
                        categories = categories,
                        catalogRevision = catalogRevision,
                        profileId = profileId ?: 0L
                    )
                },
                preferredVodLanguages
            ) { inputs, languages ->
                val categoryNames = inputs.categories.associate { it.id to it.name }
                FeaturedSelectionInputs(
                    catalog = filterMoviesByLanguage(inputs.catalog, languages, categoryNames),
                    enrichment = inputs.enrichment,
                    categories = inputs.categories,
                    catalogRevision = inputs.catalogRevision,
                    profileId = inputs.profileId,
                    languages = languages
                )
            }
                .distinctUntilChanged { old, new ->
                    old.profileId == new.profileId &&
                        old.catalogRevision == new.catalogRevision &&
                        old.catalog.size == new.catalog.size &&
                        old.languages == new.languages
                }
                .collect { inputs ->
                    refreshFeaturedSelection(inputs, force = false)
                }
        }
        viewModelScope.launch {
            combine(heroMovie, heroIndex) { hero, _ -> hero }.collect { hero ->
                hero?.let { recordHeroImpression(it) }
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runVodPipelineCatching("VodHubViewModel.refreshCatalog") {
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
        recordVodSelection(item)
    }

    fun onHeroInteraction(item: VodItem) {
        recordHeroClick(item)
    }

    suspend fun awaitEnrichment(item: VodItem): TitleEnrichmentEntity? {
        if (item.playlistId <= 0L) return null
        val key = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)
        enrichmentByKey.value[key]?.let { return it }
        val entity = titleEnrichmentRepository.enrichOnDemand(
            providerKey = key,
            title = item.title,
            releaseYear = parseYear(item.title),
            isTv = false
        ) ?: return null
        enrichmentByKey.value = enrichmentByKey.value + (key to entity)
        return entity
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPreferredVodLanguages(languages: Set<String>) {
        languagePreferenceStore.setPreferredLanguages(languages)
    }

    fun refreshAvailableVodLanguages() {
        viewModelScope.launch {
            _availableVodLanguages.value = withContext(Dispatchers.IO) {
                repository.discoverVodContentLanguages()
            }
        }
    }

    fun enrichmentFor(item: VodItem, map: Map<String, TitleEnrichmentEntity> = enrichmentByKey.value): TitleEnrichmentEntity? {
        if (item.playlistId <= 0L) return null
        return map[TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)]
    }

    fun publishEnrichment(entity: TitleEnrichmentEntity) {
        enrichmentByKey.value = enrichmentByKey.value + (entity.providerKey to entity)
    }

    fun displayRating(item: VodItem, enrichment: TitleEnrichmentEntity?): String? {
        enrichment?.rating?.takeIf { it > 0.0 }?.let { return String.format("%.1f", it) }
        return item.rating?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun refreshFeaturedSelection(inputs: FeaturedSelectionInputs, force: Boolean) {
        if (inputs.catalog.isEmpty()) {
            _featuredCarousel.value = emptyList()
            _heroIndex.value = 0
            featuredSessionKey = null
            return
        }

        val profileId = inputs.profileId
        val sessionKey = profileId to inputs.catalogRevision
        if (!force && featuredSessionKey == sessionKey && _featuredCarousel.value.isNotEmpty()) {
            return
        }

        val selection = featuredContentRanker.selectFeaturedContent(
            catalog = inputs.catalog,
            categories = inputs.categories,
            enrichmentByProviderKey = inputs.enrichment,
            genreAffinities = featuredCurationRepository.genreAffinities(profileId),
            bannerStats = featuredCurationRepository.bannerStats(profileId),
            sessionSeed = sessionKey.hashCode().toLong()
        )

        val carousel = selection.carousel.ifEmpty {
            val categoryNames = inputs.categories.associate { it.id to it.name }
            repository.vodRecent(limit = 5).filter { item ->
                item.matchesLanguageFilter(inputs.languages, categoryNames) &&
                    featuredContentRanker.isEligibleForFeatured(
                        item = item,
                        enrichment = enrichmentFor(item, inputs.enrichment)
                    )
            }
        }

        featuredSessionKey = sessionKey
        _featuredCarousel.value = carousel
        _heroIndex.value = selection.heroIndex.coerceIn(0, (carousel.size - 1).coerceAtLeast(0))
        lastRecordedImpressionKey = null
    }

    private fun recordVodSelection(item: VodItem) {
        viewModelScope.launch {
            val profileId = featuredCurationRepository.activeProfileId() ?: return@launch
            val enrichment = enrichmentFor(item)
            val genres = buildList {
                enrichment?.genres?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.let(::addAll)
                item.genre?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.let(::addAll)
            }
            featuredCurationRepository.recordGenreSelection(profileId, genres)
        }
    }

    private fun recordHeroImpression(item: VodItem) {
        val key = featuredCurationRepository.contentKey(item)
        if (lastRecordedImpressionKey == key) return
        lastRecordedImpressionKey = key
        viewModelScope.launch {
            val profileId = featuredCurationRepository.activeProfileId() ?: return@launch
            featuredCurationRepository.recordBannerImpression(profileId, item)
        }
    }

    private fun recordHeroClick(item: VodItem) {
        viewModelScope.launch {
            val profileId = featuredCurationRepository.activeProfileId() ?: return@launch
            featuredCurationRepository.recordBannerClick(profileId, item)
        }
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

    private fun filterMoviesByLanguage(
        catalog: List<VodItem>,
        languages: Set<String>,
        categoryNames: Map<String, String> = emptyMap()
    ): List<VodItem> =
        if (languages.isEmpty()) {
            catalog
        } else {
            catalog.filter { it.matchesLanguageFilter(languages, categoryNames) }
        }

    private data class FeaturedCatalogInputs(
        val catalog: List<VodItem>,
        val enrichment: Map<String, TitleEnrichmentEntity>,
        val categories: List<com.grid.tv.domain.model.VodCategory>,
        val catalogRevision: Long,
        val profileId: Long
    )

    private data class FeaturedSelectionInputs(
        val catalog: List<VodItem>,
        val enrichment: Map<String, TitleEnrichmentEntity>,
        val categories: List<com.grid.tv.domain.model.VodCategory>,
        val catalogRevision: Long,
        val profileId: Long,
        val languages: Set<String> = emptySet()
    )
}
