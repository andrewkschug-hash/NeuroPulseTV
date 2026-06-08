package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SearchScheduleFilter { LIVE_NOW, UPCOMING_TODAY }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val scheduleFilter = MutableStateFlow(SearchScheduleFilter.LIVE_NOW)

    val channels: StateFlow<List<Channel>> = query.flatMapLatest {
        repository.channels(group = null, search = it, favoritesOnly = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val programs: StateFlow<List<Program>> = query.flatMapLatest {
        repository.searchPrograms(it)
    }.combine(scheduleFilter) { list, filter ->
        val now = System.currentTimeMillis()
        when (filter) {
            SearchScheduleFilter.LIVE_NOW -> list.filter { now in it.startTime..it.endTime }
            SearchScheduleFilter.UPCOMING_TODAY -> {
                val end = now + 24 * 60 * 60 * 1000
                list.filter { it.startTime in now..end }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(newValue: String) {
        query.value = newValue
    }

    fun setScheduleFilter(filter: SearchScheduleFilter) {
        scheduleFilter.value = filter
    }
}
