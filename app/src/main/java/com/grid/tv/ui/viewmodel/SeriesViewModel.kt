package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.recording.SeriesRuleScheduler
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.parseVodDurationMs
import com.grid.tv.ui.component.toGridCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.withContext

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao
) : ViewModel() {

    private companion object {
        const val DB_PAGE_SIZE = 60
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val loadedItems = ArrayList<SeriesShow>()
    private var loadedOffset = 0
    private var endReached = false

    init {
        viewModelScope.launch {
            combine(
                repository.vodCatalogRevision(),
                _searchQuery,
                _selectedCategory
            ) { _, query, category ->
                query to category
            }.collect { (query, category) ->
                reloadFiltered(query, category)
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

    val categories: StateFlow<List<String>> = repository.vodCatalogRevision()
        .flatMapLatest {
            flow {
                emit(listOf("All") + repository.distinctSeriesCategories().sortedBy { it.lowercase() }.take(12))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        repository.vodCatalogRevision(),
        repository.seriesShowCount()
    ) { _, _ -> Unit }
        .flatMapLatest {
            flow { emit(repository.loadSeriesBrowseRows()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pagedCards = MutableStateFlow<List<VodGridCardModel>>(emptyList())
    val pagedCards: StateFlow<List<VodGridCardModel>> = _pagedCards.asStateFlow()

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    private val _selectedShowId = MutableStateFlow<Long?>(null)
    val selectedShowId = _selectedShowId.asStateFlow()

    private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
    val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
    val selectedSeasonNumber: StateFlow<Int?> = _selectedSeasonNumber.asStateFlow()

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

    private suspend fun reloadFiltered(query: String, category: String) {
        withContext(Dispatchers.IO) {
            loadedItems.clear()
            loadedOffset = 0
            endReached = false
            _filteredTotalCount.value = repository.seriesFilteredCount(category, query)
            appendNextDbPage(query, category)
        }
    }

    private suspend fun appendNextDbPage(query: String, category: String) {
        if (endReached) return
        val page = repository.seriesPage(
            category = category,
            search = query,
            limit = DB_PAGE_SIZE,
            offset = loadedOffset
        )
        if (page.isEmpty()) {
            endReached = true
            return
        }
        loadedItems.addAll(page)
        loadedOffset += page.size
        if (page.size < DB_PAGE_SIZE) {
            endReached = true
        }
        _pagedCards.value = loadedItems.map { it.toGridCardModel() }
    }

    fun loadNextPage() {
        if (endReached) return
        viewModelScope.launch(Dispatchers.IO) {
            appendNextDbPage(_searchQuery.value, _selectedCategory.value)
        }
    }

    fun findShow(showId: Long): SeriesShow? =
        loadedItems.find { it.id == showId }

    suspend fun resolveShow(showId: Long): SeriesShow? =
        findShow(showId) ?: repository.findSeriesShow(showId)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
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

    fun selectShow(showId: Long, preferredSeason: Int? = null) {
        _selectedShowId.value = showId
        viewModelScope.launch {
            val loaded = repository.seriesSeasons(showId)
            _seasons.value = loaded.sortedBy { it.number }
            _selectedSeasonNumber.value = preferredSeason?.takeIf { season ->
                loaded.any { it.number == season }
            } ?: loaded.firstOrNull()?.number
        }
    }

    fun clearShowSelection() {
        _selectedShowId.value = null
        _seasons.value = emptyList()
        _selectedSeasonNumber.value = null
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
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
}
