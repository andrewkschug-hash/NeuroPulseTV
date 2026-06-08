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
import com.neuropulse.tv.feature.recording.RecordingScheduler
import com.neuropulse.tv.feature.recording.RecordingService
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.feature.recording.RecordingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val scheduledDao: ScheduledRecordingDao,
    private val recordedDao: RecordedMediaDao,
    private val scheduler: RecordingScheduler
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sort = MutableStateFlow(RecordingSort.DATE)
    val sort: StateFlow<RecordingSort> = _sort.asStateFlow()

    val scheduled = scheduledDao.observeUpcomingAndActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recorded = sort.flatMapLatest { s ->
        when (s) {
            RecordingSort.DATE -> recordedDao.observeAllByDate()
            RecordingSort.CHANNEL -> recordedDao.observeAllByChannel()
            RecordingSort.DURATION -> recordedDao.observeAllByDuration()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(sort: RecordingSort) {
        _sort.value = sort
    }

    fun clearMessage() {
        _message.value = null
    }

    fun scheduleProgram(channel: Channel, program: Program) {
        viewModelScope.launch {
            val item = ScheduledRecordingEntity(
                channelId = channel.id,
                programTitle = program.title,
                startTime = program.startTime,
                endTime = program.endTime,
                streamUrl = channel.streamUrl,
                channelName = channel.name,
                status = RecordingStatus.SCHEDULED.name
            )
            val result = scheduler.scheduleOrConflict(item)
            _message.value = if (result.allowed) {
                "Recording scheduled"
            } else {
                "Conflict: already recording 2 streams"
            }
        }
    }

    fun startImmediateRecording(context: Context, channel: Channel, title: String, durationMs: Long = 60 * 60 * 1000L) {
        viewModelScope.launch {
            val active = scheduledDao.activeCount()
            if (active >= 2) {
                _message.value = "Conflict: already recording 2 streams"
                return@launch
            }
            val now = System.currentTimeMillis()
            val endAt = now + durationMs
            val item = ScheduledRecordingEntity(
                channelId = channel.id,
                channelName = channel.name,
                programTitle = title,
                startTime = now,
                endTime = endAt,
                streamUrl = channel.streamUrl,
                status = RecordingStatus.RECORDING.name
            )
            val id = scheduledDao.insert(item)
            val intent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_SCHEDULED_ID, id)
                putExtra(RecordingService.EXTRA_CHANNEL_ID, channel.id)
                putExtra(RecordingService.EXTRA_CHANNEL_NAME, channel.name)
                putExtra(RecordingService.EXTRA_TITLE, title)
                putExtra(RecordingService.EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(RecordingService.EXTRA_END_AT, endAt)
            }
            ContextCompat.startForegroundService(context, intent)
            _message.value = "Recording started"
        }
    }

    fun stopActiveRecording(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
        context.startService(intent)
    }

    fun deleteScheduled(id: Long) {
        viewModelScope.launch {
            scheduler.cancelAlarm(id)
            scheduledDao.deleteById(id)
        }
    }

    fun deleteRecording(id: Long, path: String) {
        viewModelScope.launch {
            recordedDao.deleteById(id)
            kotlin.runCatching { java.io.File(path).delete() }
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
