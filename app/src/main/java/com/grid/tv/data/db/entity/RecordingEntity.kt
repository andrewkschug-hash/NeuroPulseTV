package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val programTitle: String,
    val startTime: Long,
    val endTime: Long,
    val status: String = "SCHEDULED"
)
