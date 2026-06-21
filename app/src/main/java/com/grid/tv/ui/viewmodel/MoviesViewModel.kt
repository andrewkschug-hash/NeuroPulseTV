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
import com.grid.tv.ui.component.toGridCardModel
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

    val preferredLanguages: StateFlow<Set<String>> = languagePreferenceStore.preferredLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagedMovies = combine(
        repository.vodCatalogRevision(),
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

    init {
        viewModelScope.launch {
            combine(_searchQuery, _selectedCategoryId) { query, categoryId ->
                query to categoryId
            }.collect { (query, categoryId) ->
                refreshFilteredCount(query, categoryId)
            }
        }
    }

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        repository.vodCatalogRevision(),
        repository.vodStreamCount(),
        languagePreferenceStore.preferredLanguages,
        categories
    ) { _, _, languages, categoryList ->
        languages to categoryList.associate { it.id to it.name }
    }
        .flatMapLatest { (languages, categoryNames) ->
            flow {
                val rows = repository.loadMovieBrowseRows()
                emit(filterBrowseRows(rows, languages, movieCategoryNames = categoryNames))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogStatus: StateFlow<VodCatalogStatus> = repository.vodCatalogStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogStatus())

    val catalogTotalCount: StateFlow<Int> = repository.vodStreamCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
            runCatching {
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
