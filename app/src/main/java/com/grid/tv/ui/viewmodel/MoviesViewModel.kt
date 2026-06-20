package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private companion object {
        const val COMPLETION_THRESHOLD = 0.95
        const val DB_PAGE_SIZE = 60
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _pagedCards = MutableStateFlow<List<VodGridCardModel>>(emptyList())
    val pagedCards: StateFlow<List<VodGridCardModel>> = _pagedCards.asStateFlow()

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    private var loadedOffset = 0
    private var endReached = false
    private val loadedItems = ArrayList<VodItem>()

    init {
        viewModelScope.launch {
            combine(
                repository.vodCatalogRevision(),
                _searchQuery,
                _selectedCategoryId
            ) { _, query, categoryId ->
                query to categoryId
            }.collect { (query, categoryId) ->
                reloadFiltered(query, categoryId)
            }
        }
    }

    val categories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        repository.vodCatalogRevision(),
        repository.vodStreamCount()
    ) { _, _ -> Unit }
        .flatMapLatest {
            flow { emit(repository.loadMovieBrowseRows()) }
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

    private suspend fun reloadFiltered(query: String, categoryId: String?) {
        withContext(Dispatchers.IO) {
            loadedItems.clear()
            loadedOffset = 0
            endReached = false
            _filteredTotalCount.value = repository.vodFilteredCount(categoryId, query)
            appendNextDbPage(query, categoryId)
        }
    }

    private suspend fun appendNextDbPage(query: String, categoryId: String?) {
        if (endReached) return
        val page = repository.vodPage(
            categoryId = categoryId,
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
            appendNextDbPage(_searchQuery.value, _selectedCategoryId.value)
        }
    }

    fun findMovie(playlistId: Long, streamId: Long): VodItem? =
        loadedItems.find { it.playlistId == playlistId && it.streamId == streamId }

    suspend fun resolveMovie(playlistId: Long, streamId: Long): VodItem? =
        findMovie(playlistId, streamId) ?: repository.findVodStream(playlistId, streamId)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
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

    fun progressFraction(card: VodGridCardModel, progressByStreamId: Map<Long, Long>): Float? {
        val item = findMovie(card.playlistId, card.streamId) ?: return null
        val durationMs = parseVodDurationMs(item.duration) ?: return null
        val progressMs = progressByStreamId[item.streamId] ?: return null
        if (durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResume(card: VodGridCardModel, progressByStreamId: Map<Long, Long>): Boolean {
        val item = resolveMovie(card.playlistId, card.streamId) ?: return false
        val progressMs = progressByStreamId[item.streamId] ?: return false
        if (progressMs <= 5_000L) return false
        val durationMs = parseVodDurationMs(item.duration) ?: return progressMs > 5_000L
        return progressMs.toDouble() / durationMs < COMPLETION_THRESHOLD
    }
}
