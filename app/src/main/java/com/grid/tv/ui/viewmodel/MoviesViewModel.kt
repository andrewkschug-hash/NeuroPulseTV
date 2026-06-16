package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.ui.component.parseVodDurationMs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    private companion object {
        const val COMPLETION_THRESHOLD = 0.95
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<VodCategory>> = repository.vodCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val movies: StateFlow<List<VodItem>> = combine(
        repository.vodStreams(),
        _searchQuery,
        _selectedCategoryId
    ) { all, query, categoryId ->
        all
            .asSequence()
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter {
                query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                    it.genre?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.addedEpochSec ?: 0L }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
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
}
