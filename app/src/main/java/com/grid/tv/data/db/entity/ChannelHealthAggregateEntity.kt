package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_health_aggregate")
data class ChannelHealthAggregateEntity(
    @PrimaryKey val channelId: Long,
    val healthScore: Int,
    val healthTier: String,
    val sessionCount: Int,
    val streamCount: Int,
    val lastUpdated: Long
)
