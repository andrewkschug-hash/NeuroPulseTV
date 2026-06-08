package com.neuropulse.tv.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.feature.recording.RecordingService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class RecordingStartWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduledRecordingDao: ScheduledRecordingDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_SCHEDULED_ID, -1L)
        if (id <= 0L) return Result.failure()

        val scheduled = scheduledRecordingDao.getById(id) ?: return Result.failure()
        val waitMs = scheduled.startTime - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)

        val serviceIntent = Intent(applicationContext, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_SCHEDULED_ID, scheduled.id)
            putExtra(RecordingService.EXTRA_CHANNEL_ID, scheduled.channelId)
            putExtra(RecordingService.EXTRA_CHANNEL_NAME, scheduled.channelName)
            putExtra(RecordingService.EXTRA_TITLE, scheduled.programTitle)
            putExtra(RecordingService.EXTRA_STREAM_URL, scheduled.streamUrl)
            putExtra(RecordingService.EXTRA_END_AT, scheduled.endTime)
        }
        ContextCompat.startForegroundService(applicationContext, serviceIntent)
        return Result.success()
    }

    companion object {
        const val KEY_SCHEDULED_ID = "scheduled_id"
        fun uniqueWorkName(id: Long): String = "recording_start_$id"
    }
}
