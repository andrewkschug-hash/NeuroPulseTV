package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.recording.SeriesRuleScheduler
import com.grid.tv.ui.component.parseVodDurationMs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val shows = repository.seriesShows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = shows.map { all ->
        listOf("All") + all.mapNotNull { show ->
            show.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }.distinct().sorted().take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val filteredShows: StateFlow<List<SeriesShow>> = combine(shows, _searchQuery, _selectedCategory) { all, query, category ->
        all.asSequence()
            .filter { category == "All" || it.genre?.contains(category, ignoreCase = true) == true }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedShowId = MutableStateFlow<Long?>(null)
    val selectedShowId = _selectedShowId.asStateFlow()

    private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
    val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
    val selectedSeasonNumber: StateFlow<Int?> = _selectedSeasonNumber.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val episodeProgressMs: StateFlow<Map<Long, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> id to item.positionMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val episodeDurationMs: StateFlow<Map<Long, Long>> = continueWatchingRepository.observeItems(limit = 50)
        .map { items ->
            items.filter { it.contentType == ContinueWatchingContentType.SERIES }
                .mapNotNull { item -> item.streamId?.let { id -> id to item.durationMs } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectShow(showId: Long, preferredSeason: Int? = null) {
        _selectedShowId.value = showId
        viewModelScope.launch {
            val loaded = repository.seriesSeasons(showId)
            _seasons.value = loaded.sortedBy { it.number }
            _selectedSeasonNumber.value = preferredSeason?.takeIf { season ->
                loaded.any { it.number == season }
            } ?: loaded.firstOrNull()?.number
        }
    }

    fun clearShowSelection() {
        _selectedShowId.value = null
        _seasons.value = emptyList()
        _selectedSeasonNumber.value = null
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
    }

    fun clearMessage() {
        _message.value = null
    }

    fun recordSeries(show: SeriesShow) {
        viewModelScope.launch {
            if (show.playlistId == 0L) {
                _message.value = "Cannot record series without a provider link"
                return@launch
            }
            seriesRuleScheduler.createRule(
                seriesTitle = show.name,
                seriesId = show.id,
                playlistId = show.playlistId
            )
            _message.value = "Series recording enabled for ${show.name}"
        }
    }

    fun episodeProgressFraction(episodeId: Long, durationRaw: String?): Float? {
        val durationMs = parseVodDurationMs(durationRaw) ?: episodeDurationMs.value[episodeId]
        val progressMs = episodeProgressMs.value[episodeId] ?: return null
        if (durationMs == null || durationMs <= 0L) return null
        return (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun shouldResumeEpisode(
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        streamId: Long
    ): Boolean {
        val profileId = profileDao.activeProfile()?.profileId ?: return false
        if (continueWatchingRepository.hasEpisodeResumeProgress(profileId, seriesId, seasonNumber, episodeNumber)) {
            return true
        }
        return continueWatchingRepository.hasResumeProgress(profileId, streamId)
    }
}
