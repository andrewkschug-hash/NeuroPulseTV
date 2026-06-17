package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_health_aggregate")
data class ProviderHealthAggregateEntity(
    @PrimaryKey val providerId: Long,
    val healthScore: Int,
    val healthTier: String,
    val sessionCount: Int,
    val channelCount: Int,
    val lastUpdated: Long
)
