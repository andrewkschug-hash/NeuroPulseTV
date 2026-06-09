package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.SearchBarState
import com.neuropulse.tv.domain.model.SearchInputMode
import com.neuropulse.tv.domain.model.SearchResultItem
import com.neuropulse.tv.domain.model.SearchResultType
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.epg.EpgPlaceholderData
import com.neuropulse.tv.feature.search.FuzzySearch
import com.neuropulse.tv.feature.search.VoiceSearchController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val voiceSearchController: VoiceSearchController
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val results: StateFlow<List<SearchResultItem>> = _results.asStateFlow()

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
            query.debounce(150).collect { q ->
                if (!voiceSessionActive) search(q)
            }
        }
        viewModelScope.launch {
            _preferredInputMode.value = repository.preferredSearchInput()
        }
    }

    fun updateQuery(newValue: String) {
        query.value = newValue
        viewModelScope.launch { repository.setPreferredSearchInput(SearchInputMode.KEYBOARD) }
    }

    fun clearQuery() {
        stopVoiceSearch()
        query.value = ""
        _results.value = emptyList()
        _searchBarState.value = SearchBarState.DEFAULT
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
        if (q.isBlank()) {
            _results.value = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        val dbChannels = repository.channels(group = null, search = "", favoritesOnly = false).first()
        val playlists = repository.playlists().first()
        val usePlaceholder = dbChannels.isEmpty() && playlists.isNotEmpty()
        val channels = if (usePlaceholder) EpgPlaceholderData.channels() else dbChannels
        val programs = if (usePlaceholder) {
            EpgPlaceholderData.programs(now - 4 * 60 * 60 * 1000, now + 4 * 60 * 60 * 1000)
        } else {
            repository.searchPrograms(q).first()
        }
        val vod = repository.vodStreams().first()
        val series = repository.seriesShows().first()

        val channelByEpg = channels.associateBy { it.epgId }
        val items = mutableListOf<SearchResultItem>()

        FuzzySearch.rank(q, channels, { it.name }).forEach { scored ->
            val ch = scored.item
            val liveProgram = programs.firstOrNull {
                it.channelEpgId == ch.epgId && now in it.startTime..it.endTime
            }
            items += SearchResultItem(
                id = "ch-${ch.id}",
                primaryTitle = if (liveProgram != null) "${ch.name} — ${liveProgram.title}" else ch.name,
                secondaryLine = if (liveProgram != null) "${ch.name} • Live Now" else "CH ${ch.number}",
                imageUrl = ch.logoUrl,
                type = SearchResultType.CHANNEL,
                channelId = ch.id,
                program = liveProgram,
                isLive = liveProgram != null
            )
        }

        FuzzySearch.rank(q, programs, { it.title }).forEach { scored ->
            val prog = scored.item
            val ch = channelByEpg[prog.channelEpgId]
            val isLive = now in prog.startTime..prog.endTime
            val timeLabel = if (isLive) {
                "Live Now"
            } else {
                SimpleDateFormat("Tonight h:mm a", Locale.getDefault()).format(Date(prog.startTime))
            }
            items += SearchResultItem(
                id = "pg-${prog.id}",
                primaryTitle = prog.title,
                secondaryLine = "${ch?.name ?: prog.channelEpgId} • $timeLabel",
                imageUrl = ch?.logoUrl,
                type = SearchResultType.PROGRAM,
                channelId = ch?.id,
                program = prog,
                isLive = isLive
            )
        }

        FuzzySearch.rank(q, vod, { it.title }).forEach { scored ->
            val v = scored.item
            items += SearchResultItem(
                id = "vod-${v.id}",
                primaryTitle = v.title,
                secondaryLine = "VOD • ${v.genre ?: "Movies"}",
                imageUrl = v.posterUrl,
                type = SearchResultType.VOD,
                vodItem = v
            )
        }

        FuzzySearch.rank(q, series, { it.name }).forEach { scored ->
            val s = scored.item
            items += SearchResultItem(
                id = "series-${s.id}",
                primaryTitle = s.name,
                secondaryLine = "Series",
                imageUrl = s.coverUrl,
                type = SearchResultType.SERIES,
                seriesShow = s
            )
        }

        _results.value = items
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<SearchResultItem> { it.isLive }
                    .thenBy { it.primaryTitle }
            )
            .take(8)
    }

    override fun onCleared() {
        voiceSearchController.stop()
        super.onCleared()
    }
}
