package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SearchUiState
import com.grid.tv.domain.model.SearchSurfaceLogic
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.preview.PreviewPlayerManager
import com.grid.tv.util.ChannelBrowserMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: IptvRepository,
    val previewManager: PreviewPlayerManager
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val sportsOnly = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val listRevision = MutableStateFlow(0)

    private val _filteredTotalCount = MutableStateFlow(0)
    val filteredTotalCount: StateFlow<Int> = _filteredTotalCount.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    val groups = repository.groups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsPrograms: StateFlow<List<Program>> = repository.liveSportsNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filterParams = combine(
        selectedCategory,
        favoritesOnly,
        sportsOnly,
        searchQuery,
        sportsPrograms
    ) { group, fav, sports, search, programs ->
        buildFilterParams(
            group = group,
            favoritesOnly = fav,
            sportsOnly = sports,
            search = search,
            programs = programs
        )
    }

    val pagedChannels = combine(filterParams, listRevision) { params, _ -> params }
        .flatMapLatest { params ->
            repository.channelsPaging(
                group = params.group,
                search = params.search,
                favoritesOnly = params.favoritesOnly,
                sportsEpgIds = params.sportsEpgIds
            )
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            combine(filterParams, listRevision) { params, _ -> params }.collect { params ->
                val total = repository.channelsFilteredCount(
                    group = params.group,
                    search = params.search,
                    favoritesOnly = params.favoritesOnly,
                    sportsEpgIds = params.sportsEpgIds
                )
                _filteredTotalCount.value = total
                _searchUiState.value = SearchSurfaceLogic.pagedSearchState(
                    query = params.search,
                    isRefreshLoading = false,
                    itemCount = total,
                    lastCompletedQuery = params.search,
                )
                ChannelBrowserMetrics.logFilterApplied(
                    group = params.group,
                    favoritesOnly = params.favoritesOnly,
                    matchSports = params.sportsEpgIds != null,
                    search = params.search,
                    totalCount = total
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        val trimmed = query.trim()
        searchQuery.value = query
        _searchUiState.value = SearchSurfaceLogic.pagedSearchState(
            query = query,
            isRefreshLoading = trimmed.isNotEmpty(),
            itemCount = _filteredTotalCount.value,
        )
    }

    fun selectAll() {
        selectedCategory.value = null
        favoritesOnly.value = false
        sportsOnly.value = false
    }

    fun selectFavorites() {
        favoritesOnly.value = true
        sportsOnly.value = false
        selectedCategory.value = null
    }

    fun selectGroup(group: String) {
        favoritesOnly.value = false
        sportsOnly.value = false
        selectedCategory.value = group
    }

    fun selectSports() {
        favoritesOnly.value = false
        selectedCategory.value = null
        sportsOnly.value = true
    }

    fun toggleFavorite(channelId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId, enabled)
            listRevision.value = listRevision.value + 1
        }
    }

    private fun buildFilterParams(
        group: String?,
        favoritesOnly: Boolean,
        sportsOnly: Boolean,
        search: String,
        programs: List<Program>
    ): ChannelBrowserFilterParams {
        return ChannelBrowserFilterParams(
            group = if (sportsOnly) null else group,
            favoritesOnly = favoritesOnly,
            sportsEpgIds = if (sportsOnly) {
                programs.mapNotNull { program ->
                    program.channelEpgId.takeIf { it.isNotBlank() }
                }.toSet()
            } else {
                null
            },
            search = search
        )
    }

    private data class ChannelBrowserFilterParams(
        val group: String?,
        val favoritesOnly: Boolean,
        val sportsEpgIds: Set<String>?,
        val search: String
    )
}
