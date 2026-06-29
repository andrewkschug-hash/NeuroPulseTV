package com.grid.tv.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import com.grid.tv.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.feature.vod.personalization.RecommendationFeedbackStore
import com.grid.tv.feature.vod.personalization.RecommendationVote
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.session.PlaylistContext
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.vod.VodBrowseRowCategoryIndex
import com.grid.tv.feature.vod.VodCatalogPartitionInputs
import com.grid.tv.feature.vod.VodCatalogPartitions
import com.grid.tv.feature.vod.VodSeriesHydrationReason
import com.grid.tv.feature.vod.VodCatalogSessionStore
import com.grid.tv.feature.vod.buildVodCatalogPartitions
import com.grid.tv.feature.vod.VodHubUiStateBuilder
import com.grid.tv.feature.vod.VodHubUiBuildInputs
import com.grid.tv.ui.screen.VodHubFocusBreadcrumbStore
import com.grid.tv.ui.screen.VodHubPersistedFocusSnapshot
import com.grid.tv.feature.vod.VodLanguageFilterOptions
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.VodUiState
import com.grid.tv.feature.vod.filterBrowseRows
import com.grid.tv.feature.vod.toCategoryIndex
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.feature.recommendation.FeaturedContentRanker
import com.grid.tv.feature.recommendation.TasteGenomeEngine
import com.grid.tv.feature.startup.StartupTierPolicy
import com.grid.tv.feature.vod.curation.FeaturedCurationRepository
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.util.VodCatalogLogger
import com.grid.tv.util.VodPerfLogger
import com.grid.tv.util.runVodPipelineCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    private val languagePreferenceStore: VodLanguagePreferenceStore,
    private val playlistContext: PlaylistContext,
    private val focusBreadcrumbStore: VodHubFocusBreadcrumbStore,
    private val recommendationFeedbackStore: RecommendationFeedbackStore,
    private val vodCatalogSessionStore: VodCatalogSessionStore,
    private val tmdbConnectivityMonitor: com.grid.tv.data.network.tmdb.TmdbConnectivityMonitor,
) : ViewModel() {
    private val tasteGenomeEngine = TasteGenomeEngine()
    private val featuredContentRanker = FeaturedContentRanker()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val enrichmentByKey = MutableStateFlow<Map<String, TitleEnrichmentEntity>>(emptyMap())
    val enrichmentMap: StateFlow<Map<String, TitleEnrichmentEntity>> = enrichmentByKey.asStateFlow()

    val tmdbWarningMessage: StateFlow<String?> = tmdbConnectivityMonitor.warningMessage

    val preferredVodLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val includeUntaggedVodContent: StateFlow<Boolean> = languagePreferenceStore.includeUntaggedContent
        .stateIn(viewModelScope, SharingStarted.Eagerly, VodLanguagePreferenceStore.DEFAULT_INCLUDE_UNTAGGED)

    val vodLanguageFilterOptions: StateFlow<VodLanguageFilterOptions> = languagePreferenceStore.filterOptions
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            VodLanguageFilterOptions(includeUntagged = VodLanguagePreferenceStore.DEFAULT_INCLUDE_UNTAGGED)
        )

    private val _availableVodLanguages = MutableStateFlow<List<String>>(emptyList())
    val availableVodLanguages: StateFlow<List<String>> = _availableVodLanguages.asStateFlow()

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        combine(
            continueWatchingRepository.observeItems(limit = 20),
            languagePreferenceStore.filterOptions
        ) { items, filterOptions ->
            if (!filterOptions.isActive) {
                items
            } else {
                items.filter { it.matchesLanguageFilter(filterOptions) }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recommendationSample: StateFlow<List<VodItem>> =
        repository.vodCatalogRevision()
            .flatMapLatest {
                flow {
                    emit(
                        withContext(Dispatchers.IO) {
                            repository.vodSampleForRecommendations(StartupTierPolicy.recommendationSampleSize())
                        }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val vodCategories = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Pair<Long, Long>, Long>> =
        repository.vodWatchProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Catalog → language filter (recommendations and featured use this, not raw sample). */
    private val languageFilteredCatalog: StateFlow<List<VodItem>> =
        combine(recommendationSample, languagePreferenceStore.filterOptions, vodCategories) { catalog, options, categories ->
            val categoryNames = categories.associate { it.id to it.name }
            VodPerfLogger.trace("languageFilteredCatalog", "catalog=${catalog.size}") {
                filterMoviesByLanguage(catalog, options, categoryNames)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCatalogCount: StateFlow<Int> =
        languageFilteredCatalog.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _recommendationFeedbackRevision = MutableStateFlow(0)
    val recommendationFeedbackRevision: StateFlow<Int> = _recommendationFeedbackRevision.asStateFlow()

    private val activeProfileId = featuredCurationRepository.observeActiveProfileId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recommendedForYou: StateFlow<List<VodItem>> =
        combine(
            languageFilteredCatalog,
            continueWatchingItems,
            enrichmentByKey,
            activeProfileId,
            _recommendationFeedbackRevision
        ) { catalog, cw, enrichment, profileId, _ ->
            val profile = profileId ?: return@combine emptyList()
            VodPerfLogger.trace("topPicks", "catalog=${catalog.size}") {
                val (likedGenres, downvotedKeys) = recommendationTasteSignals(catalog, enrichment, profile)
                val picks = tasteGenomeEngine.topPicks(
                    catalog = catalog,
                    continueWatching = cw,
                    enrichmentByProviderKey = enrichment,
                    likedGenres = likedGenres,
                    limit = 24
                ).filter { item ->
                    val key = ContinueWatchingRepository.movieContentKey(item.playlistId, item.streamId)
                    key !in downvotedKeys
                }
                applyRecommendationFeedback(picks, profile)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingNow: StateFlow<List<VodItem>> =
        combine(languageFilteredCatalog, enrichmentByKey) { catalog, enrichment ->
            VodPerfLogger.trace("trendingNow", "catalog=${catalog.size}") {
                tasteGenomeEngine.trendingNow(catalog, enrichment, limit = 24)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somethingDifferent: StateFlow<List<VodItem>> =
        combine(languageFilteredCatalog, continueWatchingItems, enrichmentByKey) { catalog, cw, enrichment ->
            VodPerfLogger.trace("somethingDifferent", "catalog=${catalog.size}") {
                tasteGenomeEngine.somethingDifferent(catalog, cw, enrichment, limit = 20)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _featuredCarousel = MutableStateFlow(vodCatalogSessionStore.cachedFeaturedCarousel())
    val featuredCarousel: StateFlow<List<VodItem>> = _featuredCarousel.asStateFlow()

    private val _heroIndex = MutableStateFlow(0)

    private var lastSeriesHydrationElapsedMs = 0L
    val heroIndex: StateFlow<Int> = _heroIndex.asStateFlow()

    private var featuredSessionKey: Pair<Long, Long>? = null
    private var lastRecordedImpressionKey: String? = null

    private val _contentFilter = MutableStateFlow(VodContentFilter.ALL)
    val contentFilter: StateFlow<VodContentFilter> = _contentFilter.asStateFlow()

    private val moviesCountFlow = repository.vodStreamCount()
    private val seriesCountFlow = repository.seriesShowCount()
    private val catalogRevisionFlow = repository.vodCatalogRevision()

    val catalogTotalCount: StateFlow<Int> = moviesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val seriesCatalogTotalCount: StateFlow<Int> = seriesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val movieCategories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val seriesCategories: StateFlow<List<VodCategory>> = repository.seriesCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMovieCategoryId = MutableStateFlow<String?>(null)
    private val _selectedMovieCategoryPlaylistId = MutableStateFlow<Long?>(null)
    private val _selectedMovieCategoryFilterIds = MutableStateFlow<Set<String>?>(null)
    private val _selectedSeriesCategoryId = MutableStateFlow<String?>(null)
    private val _selectedSeriesCategoryPlaylistId = MutableStateFlow<Long?>(null)
    private val _selectedSeriesCategoryFilterIds = MutableStateFlow<Set<String>?>(null)
    private val _movieFilteredTotalCount = MutableStateFlow(0)
    private val _seriesFilteredTotalCount = MutableStateFlow(0)

    private val rawMovieBrowseRows: StateFlow<List<VodBrowseRow>> = combine(
        catalogRevisionFlow,
        moviesCountFlow,
        catalogProgress,
        _selectedMovieCategoryPlaylistId
    ) { _, movieCount, progress, playlistId ->
        maxOf(movieCount, progress.moviesLoaded, if (progress.moviesPhaseFinished) progress.moviesTotal else 0) to playlistId
    }
        .combine(playlistContext.activePlaylistId) { (movieCount, playlistId), _ ->
            movieCount to playlistId
        }
        .flatMapLatest { (movieCount, playlistId) ->
            flow {
                if (movieCount <= 0) {
                    val cached = vodCatalogSessionStore.cachedMovieBrowseRows()
                    emit(if (cached.isNotEmpty()) cached else emptyList())
                    return@flow
                }
                emit(
                    withContext(Dispatchers.IO) {
                        repository.loadMovieBrowseRows(
                            playlistId = playlistContext.resolveOrNull(playlistId)
                        )
                    }
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            vodCatalogSessionStore.cachedMovieBrowseRows()
        )

    private val rawSeriesBrowseRows: StateFlow<List<VodBrowseRow>> = combine(
        catalogRevisionFlow,
        seriesCountFlow,
        catalogProgress
    ) { _, seriesCount, progress ->
        maxOf(seriesCount, progress.seriesLoaded, if (progress.seriesPhaseFinished) progress.seriesTotal else 0)
    }
        .flatMapLatest { seriesCount ->
            flow {
                if (seriesCount <= 0) {
                    val cached = vodCatalogSessionStore.cachedSeriesBrowseRows()
                    emit(if (cached.isNotEmpty()) cached else emptyList())
                    return@flow
                }
                emit(withContext(Dispatchers.IO) { repository.loadSeriesBrowseRows() })
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            vodCatalogSessionStore.cachedSeriesBrowseRows()
        )

    private val hubMovieBrowseRows: StateFlow<List<VodBrowseRow>> = combine(
        rawMovieBrowseRows,
        languagePreferenceStore.filterOptions,
        movieCategories
    ) { raw, filterOptions, categoryList ->
        VodPerfLogger.trace("filterBrowseRows.hubMovies", "rows=${raw.size}") {
            filterBrowseRows(raw, filterOptions, movieCategoryNames = categoryList.associate { it.id to it.name })
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val hubSeriesBrowseRows: StateFlow<List<VodBrowseRow>> = combine(
        rawSeriesBrowseRows,
        languagePreferenceStore.filterOptions,
        seriesCategories
    ) { raw, filterOptions, categoryList ->
        VodPerfLogger.trace("filterBrowseRows.hubSeries", "rows=${raw.size}") {
            filterBrowseRows(raw, filterOptions, seriesCategoryNames = categoryList.associate { it.id to it.name })
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pre-split catalogs and wall rows — rebuilt when browse data changes, not on tab switch. */
    val catalogPartitions: StateFlow<VodCatalogPartitions> = combine(
        combine(
            hubMovieBrowseRows,
            hubSeriesBrowseRows,
            movieCategories,
            seriesCategories
        ) { movieRows, seriesRows, movieCats, seriesCats ->
        VodCatalogPartitionBrowseSlice(
            movieRows,
            seriesRows,
            movieCats,
            seriesCats,
            movieRows.toCategoryIndex(),
            seriesRows.toCategoryIndex(),
        )
        },
        combine(
            continueWatchingItems,
            trendingNow,
            recommendedForYou
        ) { cw, trending, recommended ->
            Triple(cw, trending, recommended)
        }
    ) { browseSlice, personalized ->
        val (cw, trending, recommended) = personalized
        VodCatalogPartitionInputs(
            movieBrowseRows = browseSlice.movieBrowseRows,
            seriesBrowseRows = browseSlice.seriesBrowseRows,
            movieCategories = browseSlice.movieCategories,
            seriesCategories = browseSlice.seriesCategories,
            continueWatching = cw,
            trendingMovies = trending,
            recommendedMovies = recommended,
            movieBrowseIndex = browseSlice.movieBrowseIndex,
            seriesBrowseIndex = browseSlice.seriesBrowseIndex,
        )
    }
        .map { inputs ->
            VodPerfLogger.trace(
                "buildVodCatalogPartitions",
                "movies=${inputs.movieBrowseRows.size} series=${inputs.seriesBrowseRows.size} " +
                    "movieCats=${inputs.movieCategories.size} seriesCats=${inputs.seriesCategories.size}",
            ) {
                buildVodCatalogPartitions(
                    inputs = inputs,
                    prefetchTabWallRows = !LowEndDeviceMode.isEnabled(),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged { previous, next -> previous.revision == next.revision }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            vodCatalogSessionStore.cachedPartitions()
        )

    /** Stable hub content — catalog, filters, wall rows, sidebar (no focus / hero index). */
    val contentState: StateFlow<VodUiState> = combine(
        combine(
            combine(
                recommendationSample,
                languageFilteredCatalog,
                continueWatchingItems,
                recommendedForYou,
                trendingNow
            ) { sample, filtered, cw, recs, trend ->
                VodCatalogUiSlice(sample, filtered, cw, recs, trend)
            },
            combine(
                _contentFilter,
                _searchQuery,
                languagePreferenceStore.preferredLanguages,
                languagePreferenceStore.includeUntaggedContent,
                _availableVodLanguages
            ) { filter, query, langs, untagged, available ->
                VodChromeUiSlice(filter, query, langs, untagged, available)
            },
            combine(
                featuredCarousel,
                enrichmentByKey,
                vodProgress
            ) { carousel, enrichMap, progress ->
                VodHeroUiSlice(carousel, enrichMap, progress)
            }
        ) { catalogSlice, chromeSlice, heroSlice ->
            Triple(catalogSlice, chromeSlice, heroSlice)
        },
        combine(
            catalogPartitions,
            combine(
                movieCategories,
                seriesCategories,
                combine(
                    _selectedMovieCategoryId,
                    _selectedMovieCategoryPlaylistId,
                    _selectedSeriesCategoryId,
                    _selectedSeriesCategoryPlaylistId
                ) { movieCatId, movieCatPl, seriesCatId, seriesCatPl ->
                    VodCategorySelectionSlice(movieCatId, movieCatPl, seriesCatId, seriesCatPl)
                }
            ) { movieCats, seriesCats, selection ->
                VodBrowseUiSlice(
                    movieBrowseRows = emptyList(),
                    seriesBrowseRows = emptyList(),
                    movieCategories = movieCats,
                    seriesCategories = seriesCats,
                    selectedMovieCategoryId = selection.movieCategoryId,
                    selectedMovieCategoryPlaylistId = selection.movieCategoryPlaylistId,
                    selectedSeriesCategoryId = selection.seriesCategoryId,
                    selectedSeriesCategoryPlaylistId = selection.seriesCategoryPlaylistId
                )
            },
            combine(
                combine(
                    catalogTotalCount,
                    seriesCatalogTotalCount,
                    _movieFilteredTotalCount
                ) { movieTotal, seriesTotal, movieFiltered ->
                    Triple(movieTotal, seriesTotal, movieFiltered)
                },
                combine(
                    _seriesFilteredTotalCount,
                    catalogLoading,
                    catalogProgress
                ) { seriesFiltered, loading, progress ->
                    Triple(seriesFiltered, loading, progress)
                }
            ) { counts, status ->
                VodCatalogMetaSlice(
                    catalogTotalCount = counts.first,
                    seriesCatalogTotalCount = counts.second,
                    movieFilteredTotalCount = counts.third,
                    seriesFilteredTotalCount = status.first,
                    catalogLoading = status.second,
                    catalogProgress = status.third
                )
            }
        ) { partitions, browseSlice, metaSlice ->
            Triple(partitions, browseSlice, metaSlice)
        }
    ) { topSlices, bottomSlices ->
        val (catalogSlice, chromeSlice, heroSlice) = topSlices
        val (partitions, browseSlice, metaSlice) = bottomSlices
        val resolvedPartitions = partitions.ensureWallRowsFor(chromeSlice.filter)
        VodHubUiStateBuilder.build(
            VodHubUiBuildInputs(
                catalogSample = catalogSlice.sample,
                filteredCatalog = catalogSlice.filtered,
                continueWatching = catalogSlice.continueWatching,
                recommendedForYou = catalogSlice.recommended,
                trendingNow = catalogSlice.trending,
                contentFilter = chromeSlice.filter,
                searchQuery = chromeSlice.query,
                preferredLanguages = chromeSlice.languages,
                includeUntagged = chromeSlice.includeUntagged,
                availableLanguages = chromeSlice.availableLanguages,
                featuredCarousel = heroSlice.carousel,
                enrichmentMap = heroSlice.enrichmentMap,
                vodProgress = heroSlice.vodProgress,
                movieBrowseRows = resolvedPartitions.movieBrowseRows,
                seriesBrowseRows = resolvedPartitions.seriesBrowseRows,
                movieCategories = browseSlice.movieCategories,
                seriesCategories = browseSlice.seriesCategories,
                selectedMovieCategoryId = browseSlice.selectedMovieCategoryId,
                selectedMovieCategoryPlaylistId = browseSlice.selectedMovieCategoryPlaylistId,
                selectedSeriesCategoryId = browseSlice.selectedSeriesCategoryId,
                selectedSeriesCategoryPlaylistId = browseSlice.selectedSeriesCategoryPlaylistId,
                catalogTotalCount = metaSlice.catalogTotalCount,
                seriesCatalogTotalCount = metaSlice.seriesCatalogTotalCount,
                movieFilteredTotalCount = metaSlice.movieFilteredTotalCount,
                seriesFilteredTotalCount = metaSlice.seriesFilteredTotalCount,
                catalogLoading = metaSlice.catalogLoading,
                catalogProgress = metaSlice.catalogProgress,
                catalogPartitions = resolvedPartitions
            )
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            vodCatalogSessionStore.cachedUiState()
        )

    fun setContentFilter(filter: VodContentFilter) {
        _contentFilter.value = filter
        vodCatalogSessionStore.setHubSessionState(active = true, contentFilter = filter)
    }

    fun onHubEntered() {
        vodCatalogSessionStore.setHubSessionState(active = true, contentFilter = _contentFilter.value)
    }

    fun onHubExited() {
        vodCatalogSessionStore.setHubSessionState(active = false, contentFilter = _contentFilter.value)
    }

    fun setMovieCategory(categoryId: String?, filterIds: Set<String>? = null, playlistId: Long? = null) {
        _selectedMovieCategoryId.value = categoryId
        _selectedMovieCategoryPlaylistId.value = playlistId?.takeIf { categoryId != null }
        _selectedMovieCategoryFilterIds.value = when {
            categoryId == null -> null
            !filterIds.isNullOrEmpty() -> filterIds
            else -> setOf(categoryId)
        }
        viewModelScope.launch { refreshMovieFilteredCount() }
    }

    fun setSeriesCategory(categoryId: String?, filterIds: Set<String>? = null, playlistId: Long? = null) {
        _selectedSeriesCategoryId.value = categoryId
        _selectedSeriesCategoryPlaylistId.value = playlistId?.takeIf { categoryId != null }
        _selectedSeriesCategoryFilterIds.value = when {
            categoryId == null -> null
            !filterIds.isNullOrEmpty() -> filterIds
            else -> setOf(categoryId)
        }
        viewModelScope.launch { refreshSeriesFilteredCount() }
    }

    /** Keeps hub uiState category/filter counts aligned with browse ViewModels during migration. */
    fun syncBrowseCounts(movieFiltered: Int, seriesFiltered: Int) {
        _movieFilteredTotalCount.value = movieFiltered
        _seriesFilteredTotalCount.value = seriesFiltered
    }

    fun syncMovieCategorySelection(categoryId: String?, playlistId: Long?) {
        _selectedMovieCategoryId.value = categoryId
        _selectedMovieCategoryPlaylistId.value = playlistId
    }

    fun syncSeriesCategorySelection(categoryId: String?, playlistId: Long?) {
        _selectedSeriesCategoryId.value = categoryId
        _selectedSeriesCategoryPlaylistId.value = playlistId
    }

    private suspend fun refreshMovieFilteredCount() {
        withContext(Dispatchers.IO) {
            _movieFilteredTotalCount.value = repository.vodFilteredCount(
                categoryIds = _selectedMovieCategoryFilterIds.value,
                search = _searchQuery.value,
                playlistId = _selectedMovieCategoryPlaylistId.value
            )
        }
    }

    private suspend fun refreshSeriesFilteredCount() {
        withContext(Dispatchers.IO) {
            _seriesFilteredTotalCount.value = repository.seriesFilteredCount(
                categoryIds = _selectedSeriesCategoryFilterIds.value,
                search = _searchQuery.value,
                playlistId = _selectedSeriesCategoryPlaylistId.value
            )
        }
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
            vodCatalogSessionStore.warmShell(repository)
        }
        viewModelScope.launch {
            contentState.collect { state -> vodCatalogSessionStore.publishUiState(state) }
        }
        viewModelScope.launch {
            catalogPartitions.collect { partitions ->
                vodCatalogSessionStore.publishPartitions(partitions)
            }
        }
        viewModelScope.launch {
            combine(rawMovieBrowseRows, rawSeriesBrowseRows) { movies, series ->
                movies to series
            }.collect { (movies, series) ->
                vodCatalogSessionStore.publishRawBrowseRows(movies, series)
            }
        }
        viewModelScope.launch {
            if (!playlistImportCoordinator.isImportActive()) {
                if (vodCatalogSessionStore.hasInstantShell()) {
                    delay(StartupTierPolicy.deferredVodRefreshDelayMs(VodRefreshTrigger.VOD_HUB_MOUNT))
                }
                repository.loadVodStreamed(VodRefreshTrigger.VOD_HUB_MOUNT)
            }
        }
        viewModelScope.launch {
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
                    repository.loadVodStreamed(VodRefreshTrigger.VOD_HUB_MOUNT)
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
                recommendationSample,
                languageFilteredCatalog,
                languagePreferenceStore.filterOptions,
                recommendedForYou
            ) { raw, filtered, options, recommendations ->
                VodPipelineSnapshot(raw.size, filtered.size, options, recommendations.size)
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    VodPerfLogger.logEmission(
                        "vodPipeline",
                        "catalog=${snapshot.catalogCount} filtered=${snapshot.filteredCount} " +
                            "recs=${snapshot.recommendationCount} lang=${snapshot.filterOptions.preferredLanguages}"
                    )
                    VodCatalogLogger.vodPipelineDebug(
                    catalogCount = snapshot.catalogCount,
                    filteredCount = snapshot.filteredCount,
                    selectedLanguage = snapshot.filterOptions.preferredLanguages,
                    recommendationCount = snapshot.recommendationCount,
                    focusBefore = _lastFocusedContentKey,
                    focusAfter = _lastFocusedContentKey,
                    includeUntagged = snapshot.filterOptions.includeUntagged
                )
            }
        }
        viewModelScope.launch {
            combine(
                combine(
                    languageFilteredCatalog,
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
                languagePreferenceStore.filterOptions
            ) { inputs, filterOptions ->
                FeaturedSelectionInputs(
                    catalog = inputs.catalog,
                    enrichment = inputs.enrichment,
                    categories = inputs.categories,
                    catalogRevision = inputs.catalogRevision,
                    profileId = inputs.profileId,
                    filterOptions = filterOptions
                )
            }
                .distinctUntilChanged { old, new ->
                    old.profileId == new.profileId &&
                        old.catalogRevision == new.catalogRevision &&
                        old.catalog.size == new.catalog.size &&
                        old.filterOptions == new.filterOptions
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
                repository.loadVodStreamed(VodRefreshTrigger.MANUAL_RETRY)
            }
        }
    }

    /**
     * Single entry for series-tab catalog hydration.
     * Bootstrap, deep-link, and tab-select all route here (coalesced within 300ms).
     */
    fun ensureSeriesTabHydrated(reason: VodSeriesHydrationReason = VodSeriesHydrationReason.TAB_SELECT) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSeriesHydrationElapsedMs < 300L) {
            if (BuildConfig.DEBUG) {
                Log.d("VOD_STATE", "ensureSeriesCatalogForTab skipped (coalesced) reason=$reason")
            }
            return
        }
        lastSeriesHydrationElapsedMs = now
        if (BuildConfig.DEBUG) {
            Log.d("VOD_STATE", "ensureSeriesCatalogForTab reason=$reason")
        }
        repository.ensureSeriesCatalogForTab(VodRefreshTrigger.SERIES_VIEW_MODEL)
    }

    fun setHeroIndex(index: Int) {
        _heroIndex.value = index.coerceAtLeast(0)
    }

    fun stepHeroCarousel(delta: Int) {
        val size = _featuredCarousel.value.size
        if (size <= 1) return
        val next = ((_heroIndex.value + delta) % size + size) % size
        setHeroIndex(next)
    }

    fun advanceHeroCarousel() {
        stepHeroCarousel(1)
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
        VodPerfLogger.markInput("languageFilter", "languages=${languages.joinToString(",")}")
        val started = System.currentTimeMillis()
        languagePreferenceStore.setPreferredLanguages(languages)
        logLanguageFilterSwitch(started)
    }

    fun setIncludeUntaggedVodContent(enabled: Boolean) {
        VodPerfLogger.markInput("includeUntagged", "enabled=$enabled")
        val started = System.currentTimeMillis()
        languagePreferenceStore.setIncludeUntaggedContent(enabled)
        logLanguageFilterSwitch(started)
    }

    private fun logLanguageFilterSwitch(startedMs: Long) {
        viewModelScope.launch {
            VodCatalogLogger.vodLanguageSwitch(
                durationMs = System.currentTimeMillis() - startedMs,
                catalogCount = recommendationSample.value.size,
                filteredCount = languageFilteredCatalog.value.size,
                includeUntagged = languagePreferenceStore.currentIncludeUntaggedContent()
            )
        }
    }

    private var _lastFocusedContentKey: String? = null

    fun rememberFocusedContentKey(key: String?) {
        _lastFocusedContentKey = key
    }

    val activePlaylistId = playlistContext.activePlaylistId

    fun readPersistedFocus(playlistId: Long): VodHubPersistedFocusSnapshot? =
        focusBreadcrumbStore.read(playlistId)

    fun writePersistedFocus(playlistId: Long, snapshot: VodHubPersistedFocusSnapshot) {
        focusBreadcrumbStore.write(playlistId, snapshot)
    }

    val catalogSampleCount: StateFlow<Int> =
        recommendationSample.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            val heroPlaylistId = inputs.catalog.firstOrNull { it.playlistId > 0L }?.playlistId
                ?: playlistContext.activePlaylistId.value.takeIf { it > 0L }
            if (heroPlaylistId == null) {
                emptyList()
            } else {
                val categoryNames = inputs.categories.associate { it.id to it.name }
                repository.vodRecent(heroPlaylistId, limit = 5).filter { item ->
                    item.matchesLanguageFilter(inputs.filterOptions, categoryNames) &&
                        featuredContentRanker.isEligibleForFeatured(
                            item = item,
                            enrichment = enrichmentFor(item, inputs.enrichment)
                        )
                }
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

    fun recommendationVoteFor(movie: VodItem): RecommendationVote? {
        val profileId = activeProfileId.value ?: return null
        val key = ContinueWatchingRepository.movieContentKey(movie.playlistId, movie.streamId)
        return recommendationFeedbackStore.voteFor(profileId, key)
    }

    fun voteRecommendation(movie: VodItem, vote: RecommendationVote) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = activeProfileId.value ?: return@launch
            val key = ContinueWatchingRepository.movieContentKey(movie.playlistId, movie.streamId)
            recommendationFeedbackStore.vote(profileId, key, vote)
            _recommendationFeedbackRevision.update { it + 1 }
        }
    }

    private fun applyRecommendationFeedback(items: List<VodItem>, profileId: Long): List<VodItem> {
        if (items.isEmpty()) return items
        return items
            .map { item ->
                val key = ContinueWatchingRepository.movieContentKey(item.playlistId, item.streamId)
                item to recommendationFeedbackStore.scoreBoost(profileId, key)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun recommendationTasteSignals(
        catalog: List<VodItem>,
        enrichment: Map<String, TitleEnrichmentEntity>,
        profileId: Long
    ): Pair<Set<String>, Set<String>> {
        val likedGenres = linkedSetOf<String>()
        val downvotedKeys = linkedSetOf<String>()
        catalog.forEach { item ->
            val key = ContinueWatchingRepository.movieContentKey(item.playlistId, item.streamId)
            when (recommendationFeedbackStore.voteFor(profileId, key)) {
                com.grid.tv.feature.vod.personalization.RecommendationVote.UP -> {
                    val enrichmentKey = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)
                    val row = enrichment[enrichmentKey]
                    likedGenres += tokenizeGenreCsv(row?.genres ?: item.genre.orEmpty())
                }
                com.grid.tv.feature.vod.personalization.RecommendationVote.DOWN -> downvotedKeys += key
                null -> Unit
            }
        }
        return likedGenres to downvotedKeys
    }

    private fun tokenizeGenreCsv(raw: String): Set<String> =
        raw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .toSet()

    private fun filterMoviesByLanguage(
        catalog: List<VodItem>,
        options: VodLanguageFilterOptions,
        categoryNames: Map<String, String> = emptyMap()
    ): List<VodItem> {
        if (!options.isActive) return catalog
        return catalog.filter { it.matchesLanguageFilter(options, categoryNames) }
    }

    private data class VodCatalogUiSlice(
        val sample: List<VodItem>,
        val filtered: List<VodItem>,
        val continueWatching: List<ContinueWatchingItem>,
        val recommended: List<VodItem>,
        val trending: List<VodItem>
    )

    private data class VodChromeUiSlice(
        val filter: VodContentFilter,
        val query: String,
        val languages: Set<String>,
        val includeUntagged: Boolean,
        val availableLanguages: List<String>
    )

    private data class VodHeroUiSlice(
        val carousel: List<VodItem>,
        val enrichmentMap: Map<String, TitleEnrichmentEntity>,
        val vodProgress: Map<Pair<Long, Long>, Long>
    )

    private data class VodCatalogPartitionBrowseSlice(
        val movieBrowseRows: List<VodBrowseRow>,
        val seriesBrowseRows: List<VodBrowseRow>,
        val movieCategories: List<VodCategory>,
        val seriesCategories: List<VodCategory>,
        val movieBrowseIndex: VodBrowseRowCategoryIndex = VodBrowseRowCategoryIndex.EMPTY,
        val seriesBrowseIndex: VodBrowseRowCategoryIndex = VodBrowseRowCategoryIndex.EMPTY,
    )

    private data class VodCategorySelectionSlice(
        val movieCategoryId: String?,
        val movieCategoryPlaylistId: Long?,
        val seriesCategoryId: String?,
        val seriesCategoryPlaylistId: Long?
    )

    private data class VodBrowseUiSlice(
        val movieBrowseRows: List<VodBrowseRow>,
        val seriesBrowseRows: List<VodBrowseRow>,
        val movieCategories: List<VodCategory>,
        val seriesCategories: List<VodCategory>,
        val selectedMovieCategoryId: String?,
        val selectedMovieCategoryPlaylistId: Long?,
        val selectedSeriesCategoryId: String?,
        val selectedSeriesCategoryPlaylistId: Long?
    )

    private data class VodCatalogMetaSlice(
        val catalogTotalCount: Int,
        val seriesCatalogTotalCount: Int,
        val movieFilteredTotalCount: Int,
        val seriesFilteredTotalCount: Int,
        val catalogLoading: Boolean,
        val catalogProgress: VodCatalogProgress
    )

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
        val filterOptions: VodLanguageFilterOptions = VodLanguageFilterOptions()
    )

    private data class VodPipelineSnapshot(
        val catalogCount: Int,
        val filteredCount: Int,
        val filterOptions: VodLanguageFilterOptions,
        val recommendationCount: Int
    )
}
