package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_session_telemetry",
    indices = [
        Index("channelId"),
        Index("streamId"),
        Index("providerId"),
        Index("sessionStart")
    ]
)
data class PlaybackSessionTelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val streamId: String,
    val providerId: Long,
    val sessionStart: Long,
    val sessionEnd: Long,
    val watchDurationMs: Long,
    val startupTimeMs: Long,
    val bufferingEventCount: Int,
    val bufferingDurationMs: Long,
    val playbackErrorCount: Int,
    val streamSwitchCount: Int,
    val reconnectAttempts: Int,
    val playbackSuccess: Boolean
)
