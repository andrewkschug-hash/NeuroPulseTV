package com.grid.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.network.tmdb.TmdbYearParser
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.ui.component.EpisodeWatchStatus
import com.grid.tv.ui.component.episodeWatchStatus
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.session.PlaylistContext
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.enrichment.VisibleRowEnrichmentPrefetcher
import com.grid.tv.feature.recording.SeriesRuleScheduler
import com.grid.tv.feature.vod.VodCatalogSessionStore
import com.grid.tv.feature.vod.VodLanguageFilterOptions
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.filterBrowseRows
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.feature.startup.StartupTierPolicy
import com.grid.tv.util.VodPerfLogger
import com.grid.tv.util.runVodPipelineCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.paging.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

data class SelectedEpisodeDetail(
    val episode: SeriesEpisode,
    val seasonNumber: Int,
    val episodeNumber: Int
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val visibleRowEnrichmentPrefetcher: VisibleRowEnrichmentPrefetcher,
    private val languagePreferenceStore: VodLanguagePreferenceStore,
    private val playlistContext: PlaylistContext,
    private val vodCatalogSessionStore: VodCatalogSessionStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _selectedCategoryPlaylistId = MutableStateFlow<Long?>(null)
    val selectedCategoryPlaylistId: StateFlow<Long?> = _selectedCategoryPlaylistId.asStateFlow()

    private val _selectedCategoryFilterIds = MutableStateFlow<Set<String>?>(null)

    private val selectedSeriesCategoryFilter = combine(
        _selectedCategoryFilterIds,
        _selectedCategoryPlaylistId
    ) { categoryFilterIds, playlistId -> categoryFilterIds to playlistId }

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    val preferredLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val includeUntaggedContent: StateFlow<Boolean> = languagePreferenceStore.includeUntaggedContent
        .stateIn(viewModelScope, SharingStarted.Eagerly, VodLanguagePreferenceStore.DEFAULT_INCLUDE_UNTAGGED)

    val categories: StateFlow<List<VodCategory>> = repository.seriesCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _hubSearchMode = MutableStateFlow(false)

    fun setHubSearchMode(active: Boolean) {
        _hubSearchMode.value = active
    }

    private val debouncedSearchQuery = _searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val pagedSeries = combine(
        repository.vodCatalogRevision(),
        debouncedSearchQuery,
        selectedSeriesCategoryFilter,
        languagePreferenceStore.filterOptions,
        categories
    ) { _, query, categoryFilter, filterOptions, categoryList ->
        val (categoryFilterIds, playlistId) = categoryFilter
        SeriesLanguageFilterParams(
            query = query,
            categoryFilterIds = categoryFilterIds,
            playlistId = playlistId,
            filterOptions = filterOptions,
            categoryNames = categoryList.associate { it.id to it.name }
        )
    }.combine(_hubSearchMode) { params, hubSearchMode ->
        params.copy(hubSearchMode = hubSearchMode)
    }.flatMapLatest { params ->
        if (params.hubSearchMode && params.query.isBlank()) {
            flow { emit(PagingData.empty()) }
        } else {
            repository.seriesShowsPaging(
                categoryIds = params.categoryFilterIds,
                search = params.query,
                playlistId = params.playlistId
            )
                .map { pagingData ->
                    if (!params.filterOptions.isActive) {
                        pagingData
                    } else {
                        pagingData.filter {
                            it.matchesLanguageFilter(params.filterOptions, params.categoryNames)
                        }
                    }
                }
        }
    }.cachedIn(viewModelScope)

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogStatus: StateFlow<VodCatalogStatus> = repository.vodCatalogStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogStatus())

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val catalogTotalCount: StateFlow<Int> = repository.seriesShowCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val rawSeriesBrowseRows: StateFlow<List<VodBrowseRow>> =
        combine(
            repository.vodCatalogRevision(),
            repository.seriesShowCount()
        ) { _, seriesCount ->
            seriesCount
        }
            .flatMapLatest { seriesCount ->
                flow {
                    if (seriesCount <= 0) {
                        emit(emptyList())
                        return@flow
                    }
                    val cached = vodCatalogSessionStore.cachedRawSeriesBrowseRows()
                    if (cached.isNotEmpty()) {
                        emit(cached)
                        return@flow
                    }
                    emit(withContext(Dispatchers.IO) { repository.loadSeriesBrowseRows() })
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                vodCatalogSessionStore.cachedRawSeriesBrowseRows()
            )

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        rawSeriesBrowseRows,
        languagePreferenceStore.filterOptions,
        categories
    ) { raw, filterOptions, categoryList ->
        VodPerfLogger.trace("filterBrowseRows.series", "rows=${raw.size}") {
            filterBrowseRows(raw, filterOptions, seriesCategoryNames = categoryList.associate { it.id to it.name })
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(debouncedSearchQuery, selectedSeriesCategoryFilter, _hubSearchMode) { query, categoryFilter, hubSearchMode ->
                val (categoryFilterIds, playlistId) = categoryFilter
                SeriesFilterCountParams(query, categoryFilterIds, playlistId, hubSearchMode)
            }.collect { params ->
                if (params.hubSearchMode && params.query.isBlank()) {
                    _filteredTotalCount.value = 0
                } else {
                    refreshFilteredCount(params.query, params.categoryFilterIds, params.playlistId)
                }
            }
        }
        viewModelScope.launch {
            combine(
                languagePreferenceStore.filterOptions,
                debouncedSearchQuery,
                selectedSeriesCategoryFilter,
                categories
            ) { filterOptions, query, categoryFilter, categoryList ->
                val (categoryFilterIds, playlistId) = categoryFilter
                SeriesLanguageFilterDebugParams(
                    filterOptions = filterOptions,
                    query = query,
                    categoryFilterIds = categoryFilterIds,
                    playlistId = playlistId,
                    categoryNames = categoryList.associate { it.id to it.name }
                )
            }
                .debounce(300)
                .collect { params ->
                    if (!params.filterOptions.isActive) return@collect
                    logLanguageFilterCounts(params)
                }
        }
        viewModelScope.launch {
            browseRows.collect { rows ->
                val shows = rows.flatMap { it.series }.distinctBy { "${it.playlistId}_${it.id}" }
                visibleRowEnrichmentPrefetcher.prefetchSeriesShows(shows)
            }
        }
    }

    private val _selectedShowId = MutableStateFlow<Long?>(null)
    val selectedShowId = _selectedShowId.asStateFlow()

    private val _selectedShow = MutableStateFlow<SeriesShow?>(null)
    val selectedShow: StateFlow<SeriesShow?> = _selectedShow.asStateFlow()

    private val _selectedShowOverview = MutableStateFlow<String?>(null)
    val selectedShowOverview: StateFlow<String?> = _selectedShowOverview.asStateFlow()

    private val _selectedShowEnrichment = MutableStateFlow<TitleEnrichmentEntity?>(null)
    val selectedShowEnrichment: StateFlow<TitleEnrichmentEntity?> = _selectedShowEnrichment.asStateFlow()

    private val _focusedEpisodeNumber = MutableStateFlow<Int?>(null)
    val focusedEpisodeNumber: StateFlow<Int?> = _focusedEpisodeNumber.asStateFlow()

    private val _selectedEpisodeDetail = MutableStateFlow<SelectedEpisodeDetail?>(null)
    val selectedEpisodeDetail: StateFlow<SelectedEpisodeDetail?> = _selectedEpisodeDetail.asStateFlow()

    private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
    val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

    private val _seasonsLoading = MutableStateFlow(false)
    val seasonsLoading: StateFlow<Boolean> = _seasonsLoading.asStateFlow()

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
    val selectedSeasonNumber: StateFlow<Int?> = _selectedSeasonNumber.asStateFlow()

    val selectedSeasonEpisodes: StateFlow<List<SeriesEpisode>> = combine(
        _seasons,
        _selectedSeasonNumber
    ) { seasons, seasonNumber ->
        seasons.firstOrNull { it.number == seasonNumber }?.episodes
            ?: seasons.firstOrNull()?.episodes
            ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val episodeProgressMs: StateFlow<Map<Pair<Long, Long>, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> (item.playlistId to id) to item.positionMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val episodeDurationMs: StateFlow<Map<Pair<Long, Long>, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> (item.playlistId to id) to item.durationMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private suspend fun refreshFilteredCount(
        query: String,
        categoryFilterIds: Set<String>?,
        playlistId: Long?
    ) {
        withContext(Dispatchers.IO) {
            _filteredTotalCount.value = repository.seriesFilteredCount(categoryFilterIds, query, playlistId)
        }
    }

    private suspend fun logLanguageFilterCounts(params: SeriesLanguageFilterDebugParams) {
        withContext(Dispatchers.IO) {
            val before = repository.seriesFilteredCount(
                params.categoryFilterIds,
                params.query,
                params.playlistId
            )
            if (before == 0) {
                Log.d(
                    LANGUAGE_FILTER_LOG_TAG,
                    "series language filter: before=0 (catalog not loaded yet) languages=${params.filterOptions.preferredLanguages}"
                )
                return@withContext
            }
            var after = 0
            var offset = 0
            val batchSize = 500
            while (offset < before) {
                val batch = repository.seriesShowsBatch(
                    categoryIds = params.categoryFilterIds,
                    search = params.query,
                    playlistId = params.playlistId,
                    limit = batchSize,
                    offset = offset
                )
                if (batch.isEmpty()) break
                after += batch.count { it.matchesLanguageFilter(params.filterOptions, params.categoryNames) }
                offset += batch.size
                if (batch.size < batchSize) break
            }
            Log.d(
                LANGUAGE_FILTER_LOG_TAG,
                "series language filter: before=$before after=$after languages=${params.filterOptions.preferredLanguages} " +
                    "includeUntagged=${params.filterOptions.includeUntagged}"
            )
        }
    }

    suspend fun resolveShow(playlistId: Long, showId: Long): SeriesShow? = withContext(Dispatchers.IO) {
        repository.findSeriesShow(playlistId, showId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?, filterIds: Set<String>? = null, playlistId: Long? = null) {
        VodPerfLogger.markInput("genreSelect.series", "categoryId=$categoryId filterIds=${filterIds?.size ?: 0}")
        _selectedCategoryId.value = categoryId
        _selectedCategoryPlaylistId.value = playlistId?.takeIf { categoryId != null }
        playlistId?.takeIf { it > 0L && categoryId != null }?.let { playlistContext.setActive(it) }
        _selectedCategoryFilterIds.value = when {
            categoryId == null -> null
            !filterIds.isNullOrEmpty() -> filterIds
            else -> setOf(categoryId)
        }
    }

    fun setPreferredLanguages(languages: Set<String>) {
        languagePreferenceStore.setPreferredLanguages(languages)
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runVodPipelineCatching("SeriesViewModel.refreshCatalog") {
                repository.loadVodStreamed(VodRefreshTrigger.MANUAL_RETRY)
            }
        }
    }

    fun selectShow(
        showId: Long,
        playlistId: Long,
        preferredSeason: Int? = null,
        preview: SeriesShow? = null
    ) {
        _selectedShowId.value = showId
        _selectedShow.value = preview
        _selectedShowOverview.value = null
        _selectedShowEnrichment.value = null
        _selectedEpisodeDetail.value = null
        _focusedEpisodeNumber.value = null
        _seasons.value = emptyList()
        _selectedSeasonNumber.value = null
        viewModelScope.launch {
            _seasonsLoading.value = true
            try {
                val effectivePlaylistId = preview?.playlistId?.takeIf { it > 0L } ?: playlistId
                playlistContext.setActive(effectivePlaylistId)
                val show = preview ?: withContext(Dispatchers.IO) {
                    repository.findSeriesShow(effectivePlaylistId, showId)
                }
                if (show != null) {
                    _selectedShow.value = show
                }
                val detail = withContext(Dispatchers.IO) {
                    runVodPipelineCatching(
                        "SeriesViewModel.loadSeriesDetail playlist=$effectivePlaylistId showId=$showId"
                    ) {
                        repository.loadSeriesDetail(effectivePlaylistId, showId)
                    }
                        .onFailure { error ->
                            _message.value = "Could not load seasons: ${error.message ?: "unknown error"}"
                        }
                        .getOrDefault(SeriesDetail())
                }
                val resolvedShow = show?.copy(plot = detail.plot ?: show.plot) ?: show
                if (resolvedShow != null) {
                    _selectedShow.value = resolvedShow
                }
                _seasons.value = detail.seasons.sortedBy { it.number }
                val profileId = profileDao.activeProfile()?.profileId
                val resumePlaylistId = (resolvedShow ?: show)?.playlistId?.takeIf { it > 0L } ?: effectivePlaylistId
                val latestWatch = if (profileId != null && resumePlaylistId > 0L) {
                    continueWatchingRepository.latestForSeries(profileId, showId, resumePlaylistId)
                } else {
                    null
                }
                val resumeSeason = preferredSeason?.takeIf { season ->
                    detail.seasons.any { it.number == season }
                } ?: latestWatch?.seasonNumber?.takeIf { season ->
                    detail.seasons.any { it.number == season }
                } ?: detail.seasons.firstOrNull()?.number
                _selectedSeasonNumber.value = resumeSeason
                val seasonEpisodes = detail.seasons.firstOrNull { it.number == resumeSeason }?.episodes.orEmpty()
                val resumeEpisode = resolveResumeEpisodeNumber(seasonEpisodes, latestWatch)
                _focusedEpisodeNumber.value = resumeEpisode
                val displayShow = resolvedShow ?: show
                if (displayShow != null && displayShow.playlistId > 0L) {
                    val enrichment = titleEnrichmentRepository.enrichOnDemand(
                        providerKey = TitleEnrichmentRepository.xtreamSeriesKey(
                            displayShow.playlistId,
                            showId
                        ),
                        title = displayShow.name,
                        releaseYear = parseYear(displayShow.name),
                        isTv = true
                    )
                    _selectedShowEnrichment.value = enrichment
                    _selectedShowOverview.value = enrichment?.overview?.takeIf { it.isNotBlank() }
                        ?: detail.plot?.takeIf { it.isNotBlank() }
                        ?: displayShow.plot?.takeIf { it.isNotBlank() }
                } else {
                    _selectedShowOverview.value = detail.plot?.takeIf { it.isNotBlank() }
                        ?: displayShow?.plot?.takeIf { it.isNotBlank() }
                }
            } finally {
                _seasonsLoading.value = false
            }
        }
    }

    fun clearShowSelection() {
        _selectedShowId.value = null
        _selectedShow.value = null
        _selectedShowOverview.value = null
        _selectedShowEnrichment.value = null
        _selectedEpisodeDetail.value = null
        _focusedEpisodeNumber.value = null
        _seasons.value = emptyList()
        _selectedSeasonNumber.value = null
        _seasonsLoading.value = false
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
        _focusedEpisodeNumber.value = null
    }

    fun openEpisodeDetail(episode: SeriesEpisode, seasonNumber: Int, episodeNumber: Int) {
        _focusedEpisodeNumber.value = episodeNumber
        _selectedEpisodeDetail.value = SelectedEpisodeDetail(
            episode = episode,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    fun closeEpisodeDetail() {
        _selectedEpisodeDetail.value = null
    }

    suspend fun loadEpisodeWatchStatus(
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        episode: SeriesEpisode
    ): EpisodeWatchStatus = withContext(Dispatchers.IO) {
        val profileId = profileDao.activeProfile()?.profileId
        val progressKey = playlistId to episode.id
        val progressMs = episodeProgressMs.value[progressKey]
            ?: profileId?.let {
                continueWatchingRepository.resumePositionForSeriesEpisode(
                    profileId = it,
                    playlistId = playlistId,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )
            }
        val durationMs = parseVodDurationMs(episode.duration)
            ?: episodeDurationMs.value[progressKey]
            ?: profileId?.let {
                continueWatchingRepository.latestForSeries(it, seriesId, playlistId)
                    ?.takeIf { row ->
                        row.seasonNumber == seasonNumber && row.episodeNumber == episodeNumber
                    }
                    ?.durationMs
            }
        episodeWatchStatus(progressMs, durationMs)
    }

    fun clearMessage() {
        _message.value = null
    }

    fun recordSeries(show: SeriesShow) {
        viewModelScope.launch {
            if (show.playlistId == 0L) {
                _message.value = "Cannot record series without a provider link"
                return@launch
            }
            seriesRuleScheduler.createRule(
                seriesTitle = show.name,
                seriesId = show.id,
                playlistId = show.playlistId
            )
            _message.value = "Series recording enabled for ${show.name}"
        }
    }

    fun episodeProgressFraction(
        playlistId: Long,
        episodeId: Long,
        durationRaw: String?
    ): Float? {
        val durationMs = parseVodDurationMs(durationRaw) ?: episodeDurationMs.value[playlistId to episodeId]
        val progressMs = episodeProgressMs.value[playlistId to episodeId] ?: return null
        if (durationMs == null || durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResumeEpisode(
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): Boolean {
        val profileId = profileDao.activeProfile()?.profileId ?: return false
        return continueWatchingRepository.hasEpisodeResumeProgress(
            profileId,
            playlistId,
            seriesId,
            seasonNumber,
            episodeNumber
        )
    }

    private fun parseVodDurationMs(durationRaw: String?): Long? =
        com.grid.tv.ui.component.parseVodDurationMs(durationRaw)

    private fun parseYear(value: String): Int? = TmdbYearParser.parse(value)

    private fun resolveResumeEpisodeNumber(
        episodes: List<SeriesEpisode>,
        latestWatch: com.grid.tv.domain.model.ContinueWatchingItem?
    ): Int? {
        if (episodes.isEmpty() || latestWatch == null) return null
        val byStream = latestWatch.streamId?.let { streamId ->
            episodes.firstOrNull { it.id == streamId }
        }
        val target = byStream ?: latestWatch.episodeNumber?.let { episodeNum ->
            episodes.firstOrNull { episode ->
                (episode.episodeNumber ?: episodes.indexOf(episode) + 1) == episodeNum
            }
        } ?: return null
        return target.episodeNumber ?: (episodes.indexOf(target) + 1).takeIf { it > 0 }
    }
}

private data class SeriesLanguageFilterParams(
    val query: String,
    val categoryFilterIds: Set<String>?,
    val playlistId: Long?,
    val filterOptions: VodLanguageFilterOptions,
    val categoryNames: Map<String, String>,
    val hubSearchMode: Boolean = false
)

private data class SeriesFilterCountParams(
    val query: String,
    val categoryFilterIds: Set<String>?,
    val playlistId: Long?,
    val hubSearchMode: Boolean
)

private data class SeriesLanguageFilterDebugParams(
    val filterOptions: VodLanguageFilterOptions,
    val query: String,
    val categoryFilterIds: Set<String>?,
    val playlistId: Long?,
    val categoryNames: Map<String, String>
)

private const val LANGUAGE_FILTER_LOG_TAG = "VodCatalogPipeline"
