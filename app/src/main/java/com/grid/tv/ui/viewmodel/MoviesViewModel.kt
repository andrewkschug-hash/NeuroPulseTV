package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.buildMovieBrowseRows
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private companion object {
        const val COMPLETION_THRESHOLD = 0.95
    }

    private val pager = VodCatalogPager<VodItem>()
    private val filteredCatalog = ArrayList<VodItem>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    init {
        refreshCatalog()
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                repository.vodStreams(),
                _searchQuery,
                _selectedCategoryId
            ) { all, query, categoryId ->
                all.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .filter {
                        query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                            it.genre?.contains(query, ignoreCase = true) == true
                    }
                    .sortedByDescending { it.addedEpochSec ?: 0L }
                    .toList()
            }.collect { filtered ->
                withContext(Dispatchers.Main.immediate) {
                    applyFilteredCatalog(filtered)
                }
            }
        }
    }

    val categories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseRows: StateFlow<List<VodBrowseRow>> = combine(
        repository.vodStreams(),
        categories
    ) { movies, cats ->
        buildMovieBrowseRows(movies, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogProgress: StateFlow<VodCatalogProgress> = repository.vodCatalogProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogProgress())

    val catalogStatus: StateFlow<VodCatalogStatus> = repository.vodCatalogStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodCatalogStatus())

    val catalogTotalCount: StateFlow<Int> = repository.vodStreams()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val catalogLoading: StateFlow<Boolean> = repository.vodCatalogLoading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _pagedCards = MutableStateFlow<List<VodGridCardModel>>(emptyList())
    val pagedCards: StateFlow<List<VodGridCardModel>> = _pagedCards.asStateFlow()

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    private fun applyFilteredCatalog(filtered: List<VodItem>) {
        filteredCatalog.clear()
        filteredCatalog.addAll(filtered)
        pager.reset(filtered)
        _filteredTotalCount.value = filtered.size
        publishPagedCards()
    }

    private fun publishPagedCards() {
        _pagedCards.value = pager.currentSlice().map { it.toGridCardModel() }
    }

    fun loadNextPage() {
        if (pager.loadMore()) {
            publishPagedCards()
        }
    }

    fun findMovie(playlistId: Long, streamId: Long): VodItem? =
        filteredCatalog.find { it.playlistId == playlistId && it.streamId == streamId }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runCatching { repository.refreshVodSeriesCatalog() }
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
        val item = findMovie(card.playlistId, card.streamId) ?: return false
        val progressMs = progressByStreamId[item.streamId] ?: return false
        if (progressMs <= 5_000L) return false
        val durationMs = parseVodDurationMs(item.duration) ?: return progressMs > 5_000L
        return progressMs.toDouble() / durationMs < COMPLETION_THRESHOLD
    }
}
