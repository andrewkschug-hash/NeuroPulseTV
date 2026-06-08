package com.neuropulse.tv.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.feature.recording.PendingRecording
import com.neuropulse.tv.feature.recording.RecordingBitrateEstimator
import com.neuropulse.tv.feature.recording.RecordingPrecheck
import com.neuropulse.tv.feature.recording.RecordingScheduler
import com.neuropulse.tv.feature.recording.RecordingService
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.feature.recording.RecordingStorageManager
import com.neuropulse.tv.feature.recording.StorageOption
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
    private val scheduler: RecordingScheduler,
    private val storageManager: RecordingStorageManager
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

    val activeRecording = scheduled.map { list ->
        list.firstOrNull { it.status == RecordingStatus.RECORDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recorded = sort.flatMapLatest { s ->
        when (s) {
            RecordingSort.DATE -> recordedDao.observeAllByDate()
            RecordingSort.CHANNEL -> recordedDao.observeAllByChannel()
            RecordingSort.DURATION -> recordedDao.observeAllByDuration()
            RecordingSort.FILE_SIZE -> recordedDao.observeAllBySize()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

            _precheck.value = buildPrecheck(pending.durationMs)
            _pendingRecording.value = pending
        }
    }

    fun confirmImmediateRecording(context: Context) {
        val pending = _pendingRecording.value ?: return
        viewModelScope.launch {
            proceedImmediate(context, pending)
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
                _precheck.value = buildPrecheck(pending.durationMs)
            }
        }
    }

    private suspend fun buildPrecheck(durationMs: Long): RecordingPrecheck {
        val estimate = RecordingBitrateEstimator.formatEstimate(durationMs)
        return RecordingPrecheck(
            estimateText = estimate,
            lowStorageWarning = storageManager.lowStorageWarning(),
            insufficientSpaceWarning = storageManager.insufficientSpaceWarning(
                RecordingBitrateEstimator.estimateBytes(durationMs)
            )
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
            "Conflict: already recording 2 streams"
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
