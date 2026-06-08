package com.neuropulse.tv.feature.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class RecordingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val scheduledRecordingDao: ScheduledRecordingDao
) {
    suspend fun scheduleOrConflict(item: ScheduledRecordingEntity): ConflictDecision {
        val active = scheduledRecordingDao.observeUpcomingAndActive().first().filter { it.status == RecordingStatus.RECORDING.name }
        val decision = RecordingConflictResolver.resolve(active.map { it.id })
        if (!decision.allowed) return decision

        val id = scheduledRecordingDao.insert(item)
        registerAlarm(item.copy(id = id))
        return ConflictDecision(allowed = true, activeIds = emptyList())
    }

    fun registerAlarm(item: ScheduledRecordingEntity) {
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
        scheduledRecordingDao.getScheduled().forEach { registerAlarm(it) }
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
