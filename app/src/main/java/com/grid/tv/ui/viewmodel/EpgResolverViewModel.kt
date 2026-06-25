package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import com.grid.tv.domain.epg.EpgAnalyticsSummary
import com.grid.tv.domain.epg.EpgFixProposal
import com.grid.tv.domain.epg.EpgMatchAnalyticsTracker
import com.grid.tv.domain.epg.EpgResolutionProgress
import com.grid.tv.domain.epg.EpgResolverEngine
import com.grid.tv.domain.model.EpgResolutionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val lastResolvedAt: Long = 0,
    val matchRatePercent: Float = 0f
)

@HiltViewModel
class EpgResolverViewModel @Inject constructor(
    private val resolverEngine: EpgResolverEngine,
    private val channelDao: ChannelDao,
    private val suggestionDao: EpgResolutionSuggestionDao,
    private val sourceDao: EpgSourceChannelDao,
    private val analyticsTracker: EpgMatchAnalyticsTracker
) : ViewModel() {

    private val _summary = MutableStateFlow(EpgResolverSummary())
    val summary: StateFlow<EpgResolverSummary> = _summary.asStateFlow()

    private val _analytics = MutableStateFlow(EpgAnalyticsSummary())
    val analytics: StateFlow<EpgAnalyticsSummary> = _analytics.asStateFlow()

    private val _progress = MutableStateFlow<EpgResolutionProgress?>(null)
    val progress: StateFlow<EpgResolutionProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _fixProposals = MutableStateFlow<List<EpgFixProposal>>(emptyList())
    val fixProposals: StateFlow<List<EpgFixProposal>> = _fixProposals.asStateFlow()

    private val _fixScanning = MutableStateFlow(false)
    val fixScanning: StateFlow<Boolean> = _fixScanning.asStateFlow()

    private val _unresolved = MutableStateFlow<List<com.grid.tv.data.db.entity.ChannelEntity>>(emptyList())
    val unresolved = _unresolved.asStateFlow()

    private val _channelNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val channelNames: StateFlow<Map<Long, String>> = _channelNames.asStateFlow()

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
        refreshAnalytics()
        loadChannelNameIndex()
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
            refreshAnalytics()
        }
    }

    fun scanForGuideFixes() {
        if (_fixScanning.value || _running.value) return
        viewModelScope.launch {
            _fixScanning.value = true
            _fixProposals.value = resolverEngine.previewGuideFixes()
            _fixScanning.value = false
        }
    }

    fun applyAllGuideFixes() {
        viewModelScope.launch {
            val proposals = _fixProposals.value
            if (proposals.isEmpty()) return@launch
            resolverEngine.applyGuideFixes(proposals)
            _fixProposals.value = emptyList()
            refreshSummary()
            refreshUnresolved()
            refreshAnalytics()
        }
    }

    fun clearGuideFixes() {
        _fixProposals.value = emptyList()
    }

    fun cancelResolver() {
        runningJob?.cancel()
        _running.value = false
    }

    fun acceptSuggestion(item: EpgResolutionSuggestionEntity) {
        viewModelScope.launch {
            val channel = channelDao.getById(item.channelId.toLong())
            channelDao.applyResolution(
                channelId = item.channelId.toLong(),
                epgId = item.suggestedEpgId,
                status = EpgResolutionStatus.CONFIRMED.name,
                confidence = item.confidence,
                source = item.source,
                attemptAt = System.currentTimeMillis()
            )
            channel?.let {
                analyticsTracker.saveLearnedMapping(
                    originalName = it.name,
                    epgId = item.suggestedEpgId,
                    epgDisplayName = item.suggestedEpgName,
                    source = item.source
                )
                analyticsTracker.recordManualCorrection(it.name)
            }
            suggestionDao.clearForChannel(item.channelId)
            refreshSummary()
            refreshUnresolved()
            refreshAnalytics()
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

    fun applyManual(channelId: Long, epgId: String, epgDisplayName: String, source: String) {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId)
            channelDao.applyResolution(
                channelId = channelId,
                epgId = epgId,
                status = EpgResolutionStatus.MANUAL.name,
                confidence = 100,
                source = source,
                attemptAt = System.currentTimeMillis()
            )
            channel?.let {
                analyticsTracker.saveLearnedMapping(
                    originalName = it.name,
                    epgId = epgId,
                    epgDisplayName = epgDisplayName,
                    source = source
                )
                analyticsTracker.recordManualCorrection(it.name)
            }
            suggestionDao.clearForChannel(channelId.toString())
            refreshSummary()
            refreshUnresolved()
            refreshAnalytics()
        }
    }

    fun refreshSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val total = channelDao.countTotal()
            val confirmed = channelDao.countByStatus(EpgResolutionStatus.CONFIRMED.name)
            val autoMatched = channelDao.countByStatus(EpgResolutionStatus.AUTO_MATCHED.name)
            val manual = channelDao.countByStatus(EpgResolutionStatus.MANUAL.name)
            val suggested = channelDao.countByStatus(EpgResolutionStatus.SUGGESTED.name)
            val unresolvedCount = channelDao.countByStatus(EpgResolutionStatus.UNRESOLVED.name) +
                channelDao.countByStatus(EpgResolutionStatus.UNRESOLVABLE.name)
            val matched = confirmed + autoMatched + manual
            _summary.value = EpgResolverSummary(
                totalChannels = total,
                matched = matched,
                awaitingConfirmation = suggested,
                unresolved = unresolvedCount,
                lastResolvedAt = channelDao.lastResolvedAt() ?: 0L,
                matchRatePercent = if (total > 0) matched * 100f / total else 0f
            )
        }
    }

    fun refreshAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            _analytics.value = analyticsTracker.summary()
        }
    }

    fun refreshUnresolved() {
        viewModelScope.launch(Dispatchers.IO) {
            _unresolved.value = channelDao.unresolvedForManual()
        }
    }

    private fun loadChannelNameIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            _channelNames.value = channelDao.all().associate { it.id to it.name }
        }
    }
}
