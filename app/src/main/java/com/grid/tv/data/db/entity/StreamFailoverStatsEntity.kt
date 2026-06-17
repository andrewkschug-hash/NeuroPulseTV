package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_failover_stats")
data class StreamFailoverStatsEntity(
    @PrimaryKey val channelId: Long,
    val failoverCount: Int = 0,
    val successfulRecoveryCount: Int = 0,
    val lastFailoverAt: Long = 0L,
    val lastRecoveryAt: Long = 0L
) {
    val isProblematic: Boolean
        get() = failoverCount >= 5 &&
            successfulRecoveryCount.toFloat() / failoverCount.coerceAtLeast(1) < 0.5f
}
