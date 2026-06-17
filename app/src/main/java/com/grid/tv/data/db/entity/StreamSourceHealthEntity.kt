package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "stream_source_health",
    primaryKeys = ["channelId", "streamId"]
)
data class StreamSourceHealthEntity(
    val channelId: Long,
    val streamId: String,
    val healthScore: Int,
    val healthTier: String,
    val sessionCount: Int,
    val avgStartupTimeMs: Double,
    val avgBufferingDurationMs: Double,
    val failureRate: Double,
    val lastUpdated: Long
)
