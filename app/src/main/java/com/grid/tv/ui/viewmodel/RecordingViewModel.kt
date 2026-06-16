package com.grid.tv.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import com.grid.tv.data.db.dao.SeriesRecordingRuleDao
import com.grid.tv.data.db.entity.ScheduledRecordingEntity
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.RecordQuality
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.recording.ActiveRecordingSession
import com.grid.tv.feature.recording.PendingRecording
import com.grid.tv.feature.recording.RecordingBitrateEstimator
import com.grid.tv.feature.recording.RecordingHealth
import com.grid.tv.feature.recording.RecordingPrecheck
import com.grid.tv.feature.recording.RecordingScheduler
import com.grid.tv.feature.recording.RecordingService
import com.grid.tv.feature.recording.RecordingSort
import com.grid.tv.feature.recording.RecordingStatus
import com.grid.tv.feature.recording.RecordingStorageManager
import com.grid.tv.feature.recording.RecordingStreamUrlResolver
import com.grid.tv.feature.recording.SeriesRuleScheduler
import com.grid.tv.feature.recording.StorageOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val scheduledDao: ScheduledRecordingDao,
    private val recordedDao: RecordedMediaDao,
    private val seriesRuleDao: SeriesRecordingRuleDao,
    private val scheduler: RecordingScheduler,
    private val seriesRuleScheduler: SeriesRuleScheduler,
    private val storageManager: RecordingStorageManager,
    private val repository: IptvRepository,
    private val activeSession: ActiveRecordingSession
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sort = MutableStateFlow(RecordingSort.DATE)
    val sort: StateFlow<RecordingSort> = _sort.asStateFlow()

    private val _showStoragePicker = MutableStateFlow(false)
    val showStoragePicker: StateFlow<Boolean> = _showStoragePicker.asStateFlow()

    private val _pendingRecording = MutableStateFlow<PendingRecording?>(null)
    val pendingRecording: StateFlow<PendingRecording?> = _pendingRecording.asStateFlow()

    private val _storageOptions = MutableStateFlow<List<StorageOption>>(emptyList())
    val storageOptions: StateFlow<List<StorageOption>> = _storageOptions.asStateFlow()

    private val _precheck = MutableStateFlow<RecordingPrecheck?>(null)
    val precheck: StateFlow<RecordingPrecheck?> = _precheck.asStateFlow()

    val scheduled = scheduledDao.observeUpcomingAndActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecordingActive = scheduled.map { list ->
        list.any { it.status == RecordingStatus.RECORDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Global recording indicator — true while any stream is being recorded. */
    val isRecording: StateFlow<Boolean> = isRecordingActive

    /** Live health state of the active recording session (IDLE when nothing is recording). */
    val recordingHealth: StateFlow<RecordingHealth> = activeSession.health

    val activeRecording = scheduled.map { list ->
        list.firstOrNull { it.status == RecordingStatus.RECORDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeRecordingTitle: StateFlow<String?> = activeRecording.map { it?.programTitle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recorded = sort.flatMapLatest { s ->
        when (s) {
            RecordingSort.DATE -> recordedDao.observeAllByDate()
            RecordingSort.CHANNEL -> recordedDao.observeAllByChannel()
            RecordingSort.DURATION -> recordedDao.observeAllByDuration()
            RecordingSort.FILE_SIZE -> recordedDao.observeAllBySize()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesRules = seriesRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(sort: RecordingSort) {
        _sort.value = sort
    }

    fun clearMessage() {
        _message.value = null
    }

    fun dismissPrecheck() {
        _precheck.value = null
        _pendingRecording.value = null
    }

    fun dismissStoragePicker() {
        _showStoragePicker.value = false
        _pendingRecording.value = null
    }

    fun refreshStorageOptions() {
        _storageOptions.value = storageManager.enumerateOptions()
    }

    fun scheduleProgram(channel: Channel, program: Program) {
        requestRecording(
            PendingRecording(
                channelId = channel.id,
                channelName = channel.name,
                title = program.title,
                streamUrl = channel.streamUrl,
                durationMs = program.endTime - program.startTime,
                scheduled = true,
                programStartTime = program.startTime,
                programEndTime = program.endTime
            )
        )
    }

    fun startImmediateRecording(
        context: Context,
        channel: Channel,
        title: String,
        durationMs: Long = 60 * 60 * 1000L
    ) {
        requestRecording(
            PendingRecording(
                channelId = channel.id,
                channelName = channel.name,
                title = title,
                streamUrl = channel.streamUrl,
                durationMs = durationMs,
                scheduled = false
            )
        )
    }

    private fun requestRecording(pending: PendingRecording) {
        viewModelScope.launch {
            if (!storageManager.hasConfiguredLocation()) {
                refreshStorageOptions()
                _pendingRecording.value = pending
                _showStoragePicker.value = true
                return@launch
            }

            if (pending.scheduled) {
                proceedSchedule(pending)
                _pendingRecording.value = null
                return@launch
            }

            _precheck.value = buildPrecheck(pending.durationMs, pending.streamUrl)
            _pendingRecording.value = pending
        }
    }

    fun updatePrecheckQuality(quality: RecordQuality) {
        val pending = _pendingRecording.value ?: return
        viewModelScope.launch {
            _precheck.value = buildPrecheck(pending.durationMs, pending.streamUrl, quality)
        }
    }

    fun confirmImmediateRecording(context: Context) {
        val pending = _pendingRecording.value ?: return
        val precheck = _precheck.value ?: return
        viewModelScope.launch {
            val settings = repository.loadSettings()
            if (settings.recordQuality != precheck.selectedQuality) {
                repository.saveSettings(settings.copy(recordQuality = precheck.selectedQuality))
            }
            val resolvedUrl = RecordingStreamUrlResolver.resolveUrl(pending.streamUrl, precheck.selectedQuality)
            proceedImmediate(context, pending.copy(streamUrl = resolvedUrl))
            _precheck.value = null
            _pendingRecording.value = null
        }
    }

    fun onStorageSelected(optionId: String, context: Context? = null) {
        viewModelScope.launch {
            storageManager.saveLocationById(optionId)
            _showStoragePicker.value = false
            val pending = _pendingRecording.value ?: return@launch
            if (pending.scheduled) {
                proceedSchedule(pending)
                _pendingRecording.value = null
            } else {
                _precheck.value = buildPrecheck(pending.durationMs, pending.streamUrl)
            }
        }
    }

    private suspend fun buildPrecheck(
        durationMs: Long,
        streamUrl: String,
        selectedQuality: RecordQuality? = null
    ): RecordingPrecheck {
        val qualities = RecordingStreamUrlResolver.availableQualities(streamUrl)
        val saved = repository.loadSettings().recordQuality
        val quality = when {
            selectedQuality != null && (qualities.isEmpty() || selectedQuality in qualities) -> selectedQuality
            qualities.isNotEmpty() && saved in qualities -> saved
            else -> RecordQuality.ORIGINAL
        }
        val estimate = RecordingBitrateEstimator.formatEstimate(durationMs, quality)
        val estimatedBytes = RecordingBitrateEstimator.estimateBytes(durationMs, quality)
        return RecordingPrecheck(
            estimateText = estimate,
            freeStorageText = storageManager.freeStorageSummaryLine(),
            lowStorageWarning = storageManager.lowStorageWarning(),
            insufficientSpaceWarning = storageManager.insufficientSpaceWarning(estimatedBytes),
            availableQualities = qualities,
            selectedQuality = quality
        )
    }

    private suspend fun proceedSchedule(pending: PendingRecording) {
        val start = pending.programStartTime ?: return
        val end = pending.programEndTime ?: return
        val item = ScheduledRecordingEntity(
            channelId = pending.channelId,
            programTitle = pending.title,
            startTime = start,
            endTime = end,
            streamUrl = pending.streamUrl,
            channelName = pending.channelName,
            status = RecordingStatus.SCHEDULED.name
        )
        val result = scheduler.scheduleOrConflict(item)
        _message.value = if (result.allowed) {
            "Recording scheduled"
        } else {
            result.reason ?: "Conflict: already recording 2 streams"
        }
    }

    private suspend fun proceedImmediate(context: Context, pending: PendingRecording) {
        val active = scheduledDao.activeCount()
        if (active >= 2) {
            _message.value = "Conflict: already recording 2 streams"
            return
        }
        val now = System.currentTimeMillis()
        val endAt = now + pending.durationMs
        val item = ScheduledRecordingEntity(
            channelId = pending.channelId,
            channelName = pending.channelName,
            programTitle = pending.title,
            startTime = now,
            endTime = endAt,
            streamUrl = pending.streamUrl,
            status = RecordingStatus.RECORDING.name
        )
        val id = scheduledDao.insert(item)
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_SCHEDULED_ID, id)
            putExtra(RecordingService.EXTRA_CHANNEL_ID, pending.channelId)
            putExtra(RecordingService.EXTRA_CHANNEL_NAME, pending.channelName)
            putExtra(RecordingService.EXTRA_TITLE, pending.title)
            putExtra(RecordingService.EXTRA_STREAM_URL, pending.streamUrl)
            putExtra(RecordingService.EXTRA_END_AT, endAt)
        }
        ContextCompat.startForegroundService(context, intent)
        val warning = storageManager.lowStorageWarning()
        _message.value = warning ?: "Recording started"
    }

    fun stopActiveRecording(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
        context.startService(intent)
    }

    fun deleteScheduled(id: Long) {
        viewModelScope.launch {
            scheduler.cancelScheduled(id)
            scheduledDao.deleteById(id)
        }
    }

    fun deleteRecording(id: Long, path: String, thumbnailPath: String?) {
        viewModelScope.launch {
            recordedDao.deleteById(id)
            kotlin.runCatching { java.io.File(path).delete() }
            thumbnailPath?.let { kotlin.runCatching { java.io.File(it).delete() } }
        }
    }

    fun deleteSeriesRule(ruleId: Long) {
        viewModelScope.launch {
            seriesRuleScheduler.deleteRule(ruleId)
        }
    }

    fun applySeriesRulesNow() {
        viewModelScope.launch {
            val summary = seriesRuleScheduler.applyRulesAfterEpgRefresh()
            _message.value = when {
                summary.conflictCount > 0 && summary.scheduledCount > 0 ->
                    "Series rules updated: ${summary.scheduledCount} scheduled, ${summary.conflictCount} conflicted"
                summary.conflictCount > 0 ->
                    "Series rule conflicts: ${summary.conflictCount} episode(s) skipped"
                summary.scheduledCount > 0 ->
                    "Series rules scheduled ${summary.scheduledCount} episode(s)"
                else -> "Series rules checked: no new episodes"
            }
        }
    }

    fun savePlaybackPosition(id: Long, positionMs: Long) {
        viewModelScope.launch {
            recordedDao.updatePlaybackPosition(id, positionMs)
        }
    }

    fun hasScheduled(program: Program, channelId: Long): Boolean {
        return scheduled.value.any {
            it.channelId == channelId &&
                it.programTitle == program.title &&
                it.startTime == program.startTime &&
                it.status != RecordingStatus.FAILED.name
        }
    }
}
