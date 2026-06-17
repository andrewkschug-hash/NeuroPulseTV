package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.search.UnifiedSearchEngine
import com.grid.tv.feature.search.VoiceSearchController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val voiceSearchController: VoiceSearchController,
    private val unifiedSearchEngine: UnifiedSearchEngine
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _unifiedResults = MutableStateFlow(UnifiedSearchResults())
    val unifiedResults: StateFlow<UnifiedSearchResults> = _unifiedResults.asStateFlow()

    val results: StateFlow<List<SearchResultItem>> = unifiedResults
        .map { it.flatResults }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queryText = query.asStateFlow()

    private val _searchBarState = MutableStateFlow(SearchBarState.DEFAULT)
    val searchBarState: StateFlow<SearchBarState> = _searchBarState.asStateFlow()

    private val _preferredInputMode = MutableStateFlow(SearchInputMode.KEYBOARD)
    val preferredInputMode: StateFlow<SearchInputMode> = _preferredInputMode.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var voiceSessionActive = false

    init {
        viewModelScope.launch {
            query.debounce(120).collect { q ->
                if (!voiceSessionActive) search(q)
            }
        }
        viewModelScope.launch {
            _preferredInputMode.value = repository.preferredSearchInput()
        }
        viewModelScope.launch {
            search("")
        }
    }

    fun updateQuery(newValue: String) {
        query.value = newValue
        viewModelScope.launch { repository.setPreferredSearchInput(SearchInputMode.KEYBOARD) }
    }

    fun clearQuery() {
        stopVoiceSearch()
        query.value = ""
        viewModelScope.launch { search("") }
        _searchBarState.value = SearchBarState.DEFAULT
    }

    fun applyTrendingOrRecent(term: String) {
        query.value = term
        viewModelScope.launch { search(term) }
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
        viewModelScope.launch { search(text) }
    }

    private fun onVoiceFinal(text: String) {
        voiceSessionActive = false
        query.value = text
        _searchBarState.value = SearchBarState.CONFIRMED
        viewModelScope.launch {
            repository.setPreferredSearchInput(SearchInputMode.VOICE)
            _preferredInputMode.value = SearchInputMode.VOICE
            search(text)
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

    private suspend fun search(q: String) {
        _unifiedResults.value = unifiedSearchEngine.search(q)
    }

    override fun onCleared() {
        voiceSearchController.stop()
        super.onCleared()
    }
}
