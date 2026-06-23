package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.db.dao.ProfileDao
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
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.recording.SeriesRuleScheduler
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.filterBrowseRows
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.feature.startup.StartupTierPolicy
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SelectedEpisodeDetail(
    val episode: SeriesEpisode,
    val seasonNumber: Int,
    val episodeNumber: Int
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val languagePreferenceStore: VodLanguagePreferenceStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _selectedCategoryFilterIds = MutableStateFlow<Set<String>?>(null)

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    val preferredLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<VodCategory>> = repository.seriesCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagedSeries = combine(
        repository.vodCatalogRevision(),
        _searchQuery,
        _selectedCategoryFilterIds,
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, query, categoryFilterIds, languages, categoryList ->
        SeriesLanguageFilterParams(
            query = query,
            categoryFilterIds = categoryFilterIds,
            languages = languages,
            categoryNames = categoryList.associate { it.id to it.name }
        )
    }.flatMapLatest { params ->
        repository.seriesShowsPaging(categoryIds = params.categoryFilterIds, search = params.query)
            .map { pagingData ->
                if (params.languages.isEmpty()) {
                    pagingData
                } else {
                    pagingData.filter {
                        it.matchesLanguageFilter(params.languages, params.categoryNames)
                    }
                }
            }
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            combine(_searchQuery, _selectedCategoryFilterIds) { query, categoryFilterIds ->
                query to categoryFilterIds
            }.collect { (query, categoryFilterIds) ->
                refreshFilteredCount(query, categoryFilterIds)
            }
        }
    }

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogStatus: StateFlow<VodCatalogStatus> = repository.vodCatalogStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogStatus())

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val catalogTotalCount: StateFlow<Int> = repository.seriesShowCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        repository.vodCatalogRevision(),
        repository.seriesShowCount(),
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, _, languages, categoryList ->
        languages to categoryList.associate { it.id to it.name }
    }
        .flatMapLatest { (languages, categoryNames) ->
            flow {
                delay(StartupTierPolicy.tier2DelayMs())
                val rows = withContext(Dispatchers.IO) { repository.loadSeriesBrowseRows() }
                emit(filterBrowseRows(rows, languages, seriesCategoryNames = categoryNames))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val episodeProgressMs: StateFlow<Map<Long, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> id to item.positionMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val episodeDurationMs: StateFlow<Map<Long, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> id to item.durationMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private suspend fun refreshFilteredCount(query: String, categoryFilterIds: Set<String>?) {
        withContext(Dispatchers.IO) {
            _filteredTotalCount.value = repository.seriesFilteredCount(categoryFilterIds, query)
        }
    }

    suspend fun resolveShow(showId: Long): SeriesShow? = withContext(Dispatchers.IO) {
        repository.findSeriesShow(showId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?, filterIds: Set<String>? = null) {
        _selectedCategoryId.value = categoryId
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
                repository.refreshVodSeriesCatalog(
                    trigger = VodRefreshTrigger.MANUAL_RETRY,
                    force = true
                )
            }
        }
    }

    fun selectShow(showId: Long, preferredSeason: Int? = null, preview: SeriesShow? = null) {
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
                val show = preview ?: withContext(Dispatchers.IO) { repository.findSeriesShow(showId) }
                if (show != null) {
                    _selectedShow.value = show
                }
                val detail = withContext(Dispatchers.IO) {
                    runVodPipelineCatching("SeriesViewModel.loadSeriesDetail showId=$showId") {
                        repository.loadSeriesDetail(showId)
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
                val latestWatch = if (profileId != null) {
                    continueWatchingRepository.latestForSeries(profileId, showId)
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
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        episode: SeriesEpisode
    ): EpisodeWatchStatus = withContext(Dispatchers.IO) {
        val profileId = profileDao.activeProfile()?.profileId
        val progressMs = episodeProgressMs.value[episode.id]
            ?: profileId?.let {
                continueWatchingRepository.resumePositionForSeriesEpisode(
                    profileId = it,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )
            }
            ?: profileId?.let { continueWatchingRepository.resumePositionForStream(it, episode.id) }
        val durationMs = parseVodDurationMs(episode.duration)
            ?: episodeDurationMs.value[episode.id]
            ?: profileId?.let {
                continueWatchingRepository.latestForSeries(it, seriesId)
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

    fun episodeProgressFraction(episodeId: Long, durationRaw: String?): Float? {
        val durationMs = parseVodDurationMs(durationRaw) ?: episodeDurationMs.value[episodeId]
        val progressMs = episodeProgressMs.value[episodeId] ?: return null
        if (durationMs == null || durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResumeEpisode(
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        streamId: Long
    ): Boolean {
        val profileId = profileDao.activeProfile()?.profileId ?: return false
        if (continueWatchingRepository.hasEpisodeResumeProgress(profileId, seriesId, seasonNumber, episodeNumber)) {
            return true
        }
        return continueWatchingRepository.hasResumeProgress(profileId, streamId)
    }

    private fun parseVodDurationMs(durationRaw: String?): Long? =
        com.grid.tv.ui.component.parseVodDurationMs(durationRaw)

    private fun parseYear(value: String): Int? {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(value) ?: return null
        return match.value.toIntOrNull()
    }

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
    val languages: Set<String>,
    val categoryNames: Map<String, String>
)
