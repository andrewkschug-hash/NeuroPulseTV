package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "epg_match_analytics")
data class EpgMatchAnalyticsEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val totalAttempts: Long = 0,
    val autoMatched: Long = 0,
    val suggested: Long = 0,
    val manualCorrections: Long = 0,
    val unmatched: Long = 0,
    val tvgIdMatches: Long = 0,
    val learnedMatches: Long = 0,
    val canonicalMatches: Long = 0,
    val exactNameMatches: Long = 0,
    val fuzzyMatches: Long = 0,
    val lastUpdatedAt: Long = 0
)
