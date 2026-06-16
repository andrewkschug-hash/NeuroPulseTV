package com.neuropulse.tv.feature.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.worker.RecordingStartWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager,
    private val scheduledRecordingDao: ScheduledRecordingDao
) {
    suspend fun scheduleOrConflict(item: ScheduledRecordingEntity): ConflictDecision {
        val overlaps = scheduledRecordingDao.getOverlapping(item.startTime, item.endTime)
            .filter { it.id != item.id }
        val decision = RecordingConflictResolver.resolve(overlaps.map { it.id })
        if (!decision.allowed) return decision

        val id = scheduledRecordingDao.insert(item)
        registerWork(item.copy(id = id))
        return ConflictDecision(allowed = true, activeIds = emptyList(), reason = null)
    }

    fun registerWork(item: ScheduledRecordingEntity) {
        val triggerAt = RecordingAlarmPlanner.triggerAt(item.startTime)
        val delayMs = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<RecordingStartWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RecordingStartWorker.KEY_SCHEDULED_ID to item.id))
            .build()
        workManager.enqueueUniqueWork(
            RecordingStartWorker.uniqueWorkName(item.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
        registerAlarmFallback(item)
    }

    private fun registerAlarmFallback(item: ScheduledRecordingEntity) {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).apply {
            action = RecordingAlarmReceiver.ACTION_START_SCHEDULED
            putExtra(RecordingAlarmReceiver.EXTRA_SCHEDULED_ID, item.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            RecordingAlarmPlanner.triggerAt(item.startTime),
            pending
        )
    }

    suspend fun restoreAfterBoot() {
        scheduledRecordingDao.getScheduled().forEach { registerWork(it) }
    }

    fun cancelScheduled(id: Long) {
        cancelAlarm(id)
        workManager.cancelUniqueWork(RecordingStartWorker.uniqueWorkName(id))
    }

    fun cancelAlarm(id: Long) {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).apply {
            action = RecordingAlarmReceiver.ACTION_START_SCHEDULED
            putExtra(RecordingAlarmReceiver.EXTRA_SCHEDULED_ID, id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}
