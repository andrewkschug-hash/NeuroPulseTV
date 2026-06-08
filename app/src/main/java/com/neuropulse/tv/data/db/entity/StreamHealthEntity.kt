package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_health")
data class StreamHealthEntity(
    @PrimaryKey val channelId: Long,
    val lastSuccessfulLoad: Long,
    val bufferEventsPerSession: Float,
    val averageLoadTimeMs: Long,
    val reliabilityScore: Int,
    val sessions: Int
)
