package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchUiState
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.search.UnifiedSearchEngine
import com.grid.tv.feature.search.VoiceSearchController
import com.grid.tv.feature.enrichment.VisibleRowEnrichmentPrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val voiceSearchController: VoiceSearchController,
    private val unifiedSearchEngine: UnifiedSearchEngine,
    private val visibleRowEnrichmentPrefetcher: VisibleRowEnrichmentPrefetcher,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val latestSearchGeneration = AtomicLong(0L)
    private var activeSearchJob: Job? = null

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    /** Prefer [searchUiState]. */
    @Deprecated("Use searchUiState", ReplaceWith("searchUiState"))
    val unifiedResults: StateFlow<UnifiedSearchResults> = searchUiState
        .map { it.results }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UnifiedSearchResults())

    val results: StateFlow<List<SearchResultItem>> = searchUiState
        .map { it.displayResults.flatResults }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queryText: StateFlow<String> = searchUiState
        .map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _searchBarState = MutableStateFlow(SearchBarState.DEFAULT)
    val searchBarState: StateFlow<SearchBarState> = _searchBarState.asStateFlow()

    private val _preferredInputMode = MutableStateFlow(SearchInputMode.KEYBOARD)
    val preferredInputMode: StateFlow<SearchInputMode> = _preferredInputMode.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var voiceSessionActive = false

    init {
        viewModelScope.launch {
            query.debounce(SEARCH_DEBOUNCE_MS).collect { q ->
                if (!voiceSessionActive) launchSearch(q)
            }
        }
        viewModelScope.launch {
            _preferredInputMode.value = repository.preferredSearchInput()
        }
        launchSearch("")
    }

    fun updateQuery(newValue: String) {
        query.value = newValue
        markQueryPending(newValue)
        viewModelScope.launch { repository.setPreferredSearchInput(SearchInputMode.KEYBOARD) }
    }

    fun clearQuery() {
        stopVoiceSearch()
        query.value = ""
        launchSearch("")
        _searchBarState.value = SearchBarState.DEFAULT
    }

    fun applyTrendingOrRecent(term: String) {
        query.value = term
        markQueryPending(term)
        launchSearch(term)
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            unifiedSearchEngine.clearRecentHistory()
            launchSearch(query.value)
        }
    }

    fun recordSelection(queryText: String) {
        unifiedSearchEngine.recordSearch(queryText)
    }

    fun beginVoiceSearch() {
        if (!voiceSearchController.isAvailable()) {
            viewModelScope.launch {
                _toastMessage.emit("Voice search not supported on this device")
            }
            return
        }
        stopVoiceSearch()
        voiceSessionActive = true
        _searchBarState.value = SearchBarState.LISTENING
        voiceSearchController.start(
            onPartial = { partial -> onVoicePartial(partial) },
            onFinal = { final -> onVoiceFinal(final) },
            onError = { onVoiceError() }
        )
    }

    fun stopVoiceSearch() {
        voiceSearchController.stop()
        voiceSessionActive = false
        if (_searchBarState.value == SearchBarState.LISTENING) {
            _searchBarState.value = SearchBarState.DEFAULT
        }
    }

    fun toggleVoiceSearch() {
        if (_searchBarState.value == SearchBarState.LISTENING) {
            stopVoiceSearch()
        } else {
            beginVoiceSearch()
        }
    }

    private fun onVoicePartial(text: String) {
        query.value = text
        markQueryPending(text)
        launchSearch(text)
    }

    private fun onVoiceFinal(text: String) {
        voiceSessionActive = false
        query.value = text
        markQueryPending(text)
        _searchBarState.value = SearchBarState.CONFIRMED
        viewModelScope.launch {
            repository.setPreferredSearchInput(SearchInputMode.VOICE)
            _preferredInputMode.value = SearchInputMode.VOICE
            launchSearch(text)
            delay(1500)
            if (_searchBarState.value == SearchBarState.CONFIRMED) {
                _searchBarState.value = SearchBarState.DEFAULT
            }
        }
    }

    private fun onVoiceError() {
        voiceSessionActive = false
        voiceSearchController.stop()
        if (_searchBarState.value == SearchBarState.LISTENING) {
            _searchBarState.value = SearchBarState.DEFAULT
        }
    }

    private fun markQueryPending(newValue: String) {
        _searchUiState.update { state ->
            val trimmed = newValue.trim()
            state.asActive().copy(
                query = newValue,
                inFlightQuery = trimmed,
                isSearching = trimmed.isNotEmpty() && trimmed != state.lastCompletedQuery.trim(),
                channelsReady = false,
                vodReady = false,
                seriesReady = false,
            )
        }
    }

    private fun launchSearch(q: String) {
        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            try {
                runSearch(q)
            } catch (_: CancellationException) {
                // Superseded by a newer query — keep current UI state.
            }
        }
    }

    private suspend fun runSearch(q: String) {
        val generation = latestSearchGeneration.incrementAndGet()
        val trimmed = q.trim()
        _searchUiState.update { state ->
            state.asActive().copy(
                query = q,
                isSearching = trimmed.isNotEmpty(),
                searchGeneration = generation,
                inFlightQuery = trimmed,
                channelsReady = trimmed.isEmpty(),
                vodReady = trimmed.isEmpty(),
                seriesReady = trimmed.isEmpty(),
                hasAnyResults = false,
                results = if (trimmed.isEmpty()) state.results else UnifiedSearchResults(
                    recentSearches = state.results.recentSearches,
                    trendingSearches = state.results.trendingSearches,
                ),
            )
        }

        unifiedSearchEngine.searchProgressive(
            query = q,
            searchGeneration = generation,
            isCurrentGeneration = { generation == latestSearchGeneration.get() },
        ).collect { progress ->
            if (generation != latestSearchGeneration.get()) return@collect
            if (progress.vodReady || progress.seriesReady) {
                visibleRowEnrichmentPrefetcher.prefetchSearchResults(progress.results.flatResults)
            }
            _searchUiState.update { state ->
                state.asActive().copy(
                    query = q,
                    isSearching = !progress.isComplete,
                    results = progress.results,
                    inFlightQuery = q,
                    lastCompletedQuery = if (progress.isComplete) q else state.lastCompletedQuery,
                    channelsReady = progress.channelsReady,
                    vodReady = progress.vodReady,
                    seriesReady = progress.seriesReady,
                    hasAnyResults = progress.hasAnyResults,
                    searchGeneration = generation,
                )
            }
        }
    }

    private fun SearchUiState.asActive(): SearchUiState.Active =
        when (this) {
            is SearchUiState.Active -> this
        }

    override fun onCleared() {
        activeSearchJob?.cancel()
        voiceSearchController.stop()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
