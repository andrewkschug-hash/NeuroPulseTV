package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_scan")
data class ChannelScanEntity(
    @PrimaryKey val channelId: Long,
    val status: String,
    val lastCheckedAt: Long
)
