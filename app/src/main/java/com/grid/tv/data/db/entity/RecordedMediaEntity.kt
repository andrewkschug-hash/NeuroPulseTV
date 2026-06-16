package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recorded_media")
data class RecordedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val channelName: String,
    val programTitle: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val recordedAt: Long,
    val playbackPositionMs: Long = 0,
    val thumbnailPath: String? = null,
    val integrityStatus: String = "OK"   // RecordingIntegrityStatus enum name
)
