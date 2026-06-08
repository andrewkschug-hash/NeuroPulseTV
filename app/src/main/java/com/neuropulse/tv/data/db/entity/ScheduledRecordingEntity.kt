package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_recordings")
data class ScheduledRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val programTitle: String,
    val startTime: Long,
    val endTime: Long,
    val streamUrl: String,
    val channelName: String,
    val status: String = "SCHEDULED",
    val outputPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
