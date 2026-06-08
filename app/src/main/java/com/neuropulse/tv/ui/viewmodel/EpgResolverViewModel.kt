package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.EpgResolutionSuggestionDao
import com.neuropulse.tv.data.db.dao.EpgSourceChannelDao
import com.neuropulse.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.neuropulse.tv.data.db.entity.EpgSourceChannelEntity
import com.neuropulse.tv.domain.epg.EpgResolutionProgress
import com.neuropulse.tv.domain.epg.EpgResolverEngine
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class EpgResolverSummary(
    val totalChannels: Int = 0,
    val matched: Int = 0,
    val awaitingConfirmation: Int = 0,
    val unresolved: Int = 0,
    val lastResolvedAt: Long = 0
)

@HiltViewModel
class EpgResolverViewModel @Inject constructor(
    private val resolverEngine: EpgResolverEngine,
    private val channelDao: ChannelDao,
    private val suggestionDao: EpgResolutionSuggestionDao,
    private val sourceDao: EpgSourceChannelDao
) : ViewModel() {

    private val _summary = MutableStateFlow(EpgResolverSummary())
    val summary: StateFlow<EpgResolverSummary> = _summary.asStateFlow()

    private val _progress = MutableStateFlow<EpgResolutionProgress?>(null)
    val progress: StateFlow<EpgResolutionProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _unresolved = MutableStateFlow<List<com.neuropulse.tv.data.db.entity.ChannelEntity>>(emptyList())
    val unresolved = _unresolved.asStateFlow()

    private val _manualCandidates = MutableStateFlow<List<EpgSourceChannelEntity>>(emptyList())
    val manualCandidates = _manualCandidates.asStateFlow()

    val suggestions: StateFlow<List<EpgResolutionSuggestionEntity>> = suggestionDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestionsByChannelId: StateFlow<Map<String, EpgResolutionSuggestionEntity>> = suggestions
        .map { list -> list.associateBy { it.channelId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var runningJob: Job? = null

    init {
        refreshSummary()
        refreshUnresolved()
    }

    fun runResolver(createdAfter: Long = 0L) {
        if (_running.value) return
        runningJob = viewModelScope.launch {
            _running.value = true
            _progress.value = null
            resolverEngine.resolveAllUnmatched(createdAfter).collect {
                _progress.value = it
            }
            _running.value = false
            refreshSummary()
            refreshUnresolved()
        }
    }

    fun cancelResolver() {
        runningJob?.cancel()
        _running.value = false
    }

    fun acceptSuggestion(item: EpgResolutionSuggestionEntity) {
        viewModelScope.launch {
            channelDao.applyResolution(
                channelId = item.channelId.toLong(),
                epgId = item.suggestedEpgId,
                status = EpgResolutionStatus.CONFIRMED.name,
                confidence = item.confidence,
                source = item.source,
                attemptAt = System.currentTimeMillis()
            )
            suggestionDao.clearForChannel(item.channelId)
            refreshSummary()
            refreshUnresolved()
        }
    }

    fun dismissSuggestion(item: EpgResolutionSuggestionEntity) {
        viewModelScope.launch {
            suggestionDao.dismissByChannel(item.channelId)
            channelDao.applyResolution(
                channelId = item.channelId.toLong(),
                epgId = null,
                status = EpgResolutionStatus.UNRESOLVABLE.name,
                confidence = 0,
                source = item.source,
                attemptAt = System.currentTimeMillis()
            )
            refreshSummary()
            refreshUnresolved()
        }
    }

    fun acceptAll() {
        viewModelScope.launch {
            suggestions.value.forEach { acceptSuggestion(it) }
        }
    }

    fun searchManualCandidates(query: String) {
        viewModelScope.launch {
            val normalized = resolverEngine.normalizeChannelName(query)
            _manualCandidates.value = if (normalized.isBlank()) emptyList() else sourceDao.searchNormalized(normalized)
        }
    }

    fun applyManual(channelId: Long, epgId: String, source: String) {
        viewModelScope.launch {
            channelDao.applyResolution(
                channelId = channelId,
                epgId = epgId,
                status = EpgResolutionStatus.MANUAL.name,
                confidence = 100,
                source = source,
                attemptAt = System.currentTimeMillis()
            )
            suggestionDao.clearForChannel(channelId.toString())
            refreshSummary()
            refreshUnresolved()
        }
    }

    fun refreshSummary() {
        viewModelScope.launch {
            val total = channelDao.countTotal()
            val confirmed = channelDao.countByStatus(EpgResolutionStatus.CONFIRMED.name)
            val autoMatched = channelDao.countByStatus(EpgResolutionStatus.AUTO_MATCHED.name)
            val manual = channelDao.countByStatus(EpgResolutionStatus.MANUAL.name)
            val suggested = channelDao.countByStatus(EpgResolutionStatus.SUGGESTED.name)
            val unresolved = channelDao.countByStatus(EpgResolutionStatus.UNRESOLVED.name) + channelDao.countByStatus(EpgResolutionStatus.UNRESOLVABLE.name)
            _summary.value = EpgResolverSummary(
                totalChannels = total,
                matched = confirmed + autoMatched + manual,
                awaitingConfirmation = suggested,
                unresolved = unresolved,
                lastResolvedAt = channelDao.lastResolvedAt() ?: 0L
            )
        }
    }

    fun refreshUnresolved() {
        viewModelScope.launch {
            _unresolved.value = channelDao.unresolvedForManual()
        }
    }
}
