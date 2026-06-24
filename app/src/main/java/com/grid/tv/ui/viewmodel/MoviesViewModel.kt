package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.session.PlaylistContext
import com.grid.tv.feature.vod.VodLanguagePreferenceStore
import com.grid.tv.feature.vod.filterBrowseRows
import com.grid.tv.feature.vod.matchesLanguageFilter
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.parseVodDurationMs
import com.grid.tv.feature.startup.StartupTierPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val languagePreferenceStore: VodLanguagePreferenceStore,
    private val playlistContext: PlaylistContext
) : ViewModel() {

    private companion object {
        const val COMPLETION_THRESHOLD = 0.95
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _selectedCategoryPlaylistId = MutableStateFlow<Long?>(null)
    val selectedCategoryPlaylistId: StateFlow<Long?> = _selectedCategoryPlaylistId.asStateFlow()

    private val selectedCategoryFilter = combine(
        _selectedCategoryId,
        _selectedCategoryPlaylistId
    ) { categoryId, playlistId -> categoryId to playlistId }

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

    val vodProgress: StateFlow<Map<Pair<Long, Long>, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _hubSearchMode = MutableStateFlow(false)

    fun setHubSearchMode(active: Boolean) {
        _hubSearchMode.value = active
    }

    private val debouncedSearchQuery = _searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val pagedMovies = combine(
        catalogRevisionFlow,
        debouncedSearchQuery,
        selectedCategoryFilter,
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, query, categoryFilter, languages, categoryList ->
        val (categoryId, playlistId) = categoryFilter
        MovieLanguageFilterParams(
            query = query,
            categoryId = categoryId,
            playlistId = playlistId,
            languages = languages,
            categoryNames = categoryList.associate { it.id to it.name }
        )
    }.combine(_hubSearchMode) { params, hubSearchMode ->
        params.copy(hubSearchMode = hubSearchMode)
    }.flatMapLatest { params ->
        if (params.hubSearchMode && params.query.isBlank()) {
            flow { emit(PagingData.empty()) }
        } else {
            repository.vodMoviesPaging(
                categoryId = params.categoryId,
                search = params.query,
                playlistId = params.playlistId
            )
                .map { pagingData ->
                    if (params.languages.isEmpty()) {
                        pagingData
                    } else {
                        pagingData.filter {
                            it.matchesLanguageFilter(params.languages, params.categoryNames)
                        }
                    }
                }
        }
    }.cachedIn(viewModelScope)

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        catalogRevisionFlow,
        moviesCountFlow,
        languagePreferenceStore.preferredLanguages,
        categories,
        _selectedCategoryPlaylistId
    ) { _, movieCount, languages, categoryList, playlistId ->
        BrowseRowsParams(movieCount, languages, categoryList.associate { it.id to it.name }, playlistId)
    }
        .combine(playlistContext.activePlaylistId) { params, _ -> params }
        .debounce(350L)
        .flatMapLatest { params ->
            flow {
                if (params.movieCount <= 0) {
                    emit(emptyList())
                    return@flow
                }
                val rows = withContext(Dispatchers.IO) {
                    repository.loadMovieBrowseRows(
                        playlistId = playlistContext.resolveOrNull(params.playlistId)
                    )
                }
                emit(filterBrowseRows(rows, params.languages, movieCategoryNames = params.categoryNames))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(debouncedSearchQuery, selectedCategoryFilter, _hubSearchMode) { query, categoryFilter, hubSearchMode ->
                val (categoryId, playlistId) = categoryFilter
                FilterCountParams(query, categoryId, playlistId, hubSearchMode)
            }.collect { params ->
                if (params.hubSearchMode && params.query.isBlank()) {
                    _filteredTotalCount.value = 0
                } else {
                    refreshFilteredCount(params.query, params.categoryId, params.playlistId)
                }
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

    private suspend fun refreshFilteredCount(query: String, categoryId: String?, playlistId: Long?) {
        withContext(Dispatchers.IO) {
            _filteredTotalCount.value = repository.vodFilteredCount(categoryId, query, playlistId)
        }
    }

    suspend fun resolveMovie(playlistId: Long, streamId: Long): VodItem? =
        repository.findVodStream(playlistId, streamId)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?, playlistId: Long? = null) {
        _selectedCategoryId.value = categoryId
        _selectedCategoryPlaylistId.value = playlistId?.takeIf { categoryId != null }
        playlistId?.takeIf { it > 0L && categoryId != null }?.let { playlistContext.setActive(it) }
    }

    fun setPreferredLanguages(languages: Set<String>) {
        languagePreferenceStore.setPreferredLanguages(languages)
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runVodPipelineCatching("MoviesViewModel.refreshCatalog") {
                repository.loadVodStreamed(VodRefreshTrigger.MANUAL_RETRY)
            }
        }
    }

    fun progressFraction(item: VodItem, progressByKey: Map<Pair<Long, Long>, Long>): Float? {
        val durationMs = parseVodDurationMs(item.duration) ?: return null
        val progressMs = progressByKey[item.playlistId to item.streamId] ?: return null
        if (durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResume(item: VodItem, progressByKey: Map<Pair<Long, Long>, Long>): Boolean {
        val progressMs = progressByKey[item.playlistId to item.streamId] ?: return false
        if (progressMs <= 5_000L) return false
        val durationMs = parseVodDurationMs(item.duration) ?: return progressMs > 5_000L
        return progressMs.toDouble() / durationMs < COMPLETION_THRESHOLD
    }

    suspend fun shouldResume(card: VodGridCardModel, progressByKey: Map<Pair<Long, Long>, Long>): Boolean {
        val item = resolveMovie(card.playlistId, card.streamId) ?: return false
        return shouldResume(item, progressByKey)
    }
}

private data class MovieLanguageFilterParams(
    val query: String,
    val categoryId: String?,
    val playlistId: Long?,
    val languages: Set<String>,
    val categoryNames: Map<String, String>,
    val hubSearchMode: Boolean = false
)

private data class BrowseRowsParams(
    val movieCount: Int,
    val languages: Set<String>,
    val categoryNames: Map<String, String>,
    val playlistId: Long?
)

private data class FilterCountParams(
    val query: String,
    val categoryId: String?,
    val playlistId: Long?,
    val hubSearchMode: Boolean
)
