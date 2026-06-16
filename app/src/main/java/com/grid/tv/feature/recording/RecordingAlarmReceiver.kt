package com.grid.tv.feature.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduledRecordingDao: ScheduledRecordingDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (id <= 0L) return

        CoroutineScope(Dispatchers.IO).launch {
            val scheduled = scheduledRecordingDao.getById(id) ?: return@launch
            val waitMs = scheduled.startTime - System.currentTimeMillis()
            if (waitMs > 0) delay(waitMs)
            val serviceIntent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_SCHEDULED_ID, scheduled.id)
                putExtra(RecordingService.EXTRA_CHANNEL_ID, scheduled.channelId)
                putExtra(RecordingService.EXTRA_CHANNEL_NAME, scheduled.channelName)
                putExtra(RecordingService.EXTRA_TITLE, scheduled.programTitle)
                putExtra(RecordingService.EXTRA_STREAM_URL, scheduled.streamUrl)
                putExtra(RecordingService.EXTRA_END_AT, scheduled.endTime)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    companion object {
        const val ACTION_START_SCHEDULED = "com.grid.tv.recording.START_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"
    }
}
