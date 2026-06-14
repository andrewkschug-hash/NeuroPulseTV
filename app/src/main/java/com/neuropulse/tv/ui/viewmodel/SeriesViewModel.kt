package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.SeriesSeason
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.recording.SeriesRuleScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler
) : ViewModel() {

    val shows = repository.seriesShows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedShowId = MutableStateFlow<Long?>(null)
    val selectedShowId = _selectedShowId.asStateFlow()

    private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
    val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun selectShow(showId: Long) {
        _selectedShowId.value = showId
        viewModelScope.launch {
            _seasons.value = repository.seriesSeasons(showId)
        }
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
}
