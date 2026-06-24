package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.filterBrowseRows
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.parseVodDurationMs
import com.grid.tv.feature.startup.StartupTierPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.paging.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import com.grid.tv.util.runVodPipelineCatching
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val languagePreferenceStore: VodLanguagePreferenceStore
) : ViewModel() {

    private companion object {
        const val COMPLETION_THRESHOLD = 0.95
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    /** Non-null repository flows — initialized before any init { launch } block. */
    private val moviesCountFlow: Flow<Int> = repository.vodStreamCount()

    private val seriesCountFlow: Flow<Int> = repository.seriesShowCount()

    private val catalogRevisionFlow: Flow<Long> = repository.vodCatalogRevision()

    val preferredLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogTotalCount: StateFlow<Int> = moviesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogStatus: StateFlow<VodCatalogStatus> = repository.vodCatalogStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogStatus())

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pagedMovies = combine(
        catalogRevisionFlow,
        _searchQuery,
        _selectedCategoryId,
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, query, categoryId, languages, categoryList ->
        MovieLanguageFilterParams(
            query = query,
            categoryId = categoryId,
            languages = languages,
            categoryNames = categoryList.associate { it.id to it.name }
        )
    }.flatMapLatest { params ->
        repository.vodMoviesPaging(categoryId = params.categoryId, search = params.query)
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

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        catalogRevisionFlow,
        moviesCountFlow,
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, movieCount, languages, categoryList ->
        Triple(movieCount, languages, categoryList.associate { it.id to it.name })
    }
        .flatMapLatest { (movieCount, languages, categoryNames) ->
            flow {
                if (movieCount <= 0) {
                    delay(StartupTierPolicy.tier2DelayMs())
                }
                val rows = withContext(Dispatchers.IO) { repository.loadMovieBrowseRows() }
                emit(filterBrowseRows(rows, languages, movieCategoryNames = categoryNames))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(_searchQuery, _selectedCategoryId) { query, categoryId ->
                query to categoryId
            }.collect { (query, categoryId) ->
                refreshFilteredCount(query, categoryId)
            }
        }
        viewModelScope.launch {
            combine(moviesCountFlow, _filteredTotalCount) { dbTotal, filtered ->
                dbTotal to filtered
            }.collect { (dbTotal, filtered) ->
                com.grid.tv.util.VodCatalogLogger.moviesReceived(dbTotal)
                com.grid.tv.util.VodCatalogLogger.uiItemsRendered("MoviesBrowser", filtered)
            }
        }
    }

    private suspend fun refreshFilteredCount(query: String, categoryId: String?) {
        withContext(Dispatchers.IO) {
            _filteredTotalCount.value = repository.vodFilteredCount(categoryId, query)
        }
    }

    suspend fun resolveMovie(playlistId: Long, streamId: Long): VodItem? =
        repository.findVodStream(playlistId, streamId)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setPreferredLanguages(languages: Set<String>) {
        languagePreferenceStore.setPreferredLanguages(languages)
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runVodPipelineCatching("MoviesViewModel.refreshCatalog") {
                repository.refreshVodSeriesCatalog(
                    trigger = VodRefreshTrigger.MANUAL_RETRY,
                    force = true
                )
            }
        }
    }

    fun progressFraction(item: VodItem, progressByStreamId: Map<Long, Long>): Float? {
        val durationMs = parseVodDurationMs(item.duration) ?: return null
        val progressMs = progressByStreamId[item.streamId] ?: return null
        if (durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResume(item: VodItem, progressByStreamId: Map<Long, Long>): Boolean {
        val progressMs = progressByStreamId[item.streamId] ?: return false
        if (progressMs <= 5_000L) return false
        val durationMs = parseVodDurationMs(item.duration) ?: return progressMs > 5_000L
        return progressMs.toDouble() / durationMs < COMPLETION_THRESHOLD
    }

    suspend fun shouldResume(card: VodGridCardModel, progressByStreamId: Map<Long, Long>): Boolean {
        val item = resolveMovie(card.playlistId, card.streamId) ?: return false
        return shouldResume(item, progressByStreamId)
    }
}

private data class MovieLanguageFilterParams(
    val query: String,
    val categoryId: String?,
    val languages: Set<String>,
    val categoryNames: Map<String, String>
)
