package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "featured_banner_stats",
    primaryKeys = ["profileId", "contentKey"],
    indices = [Index("profileId")]
)
data class FeaturedBannerStatsEntity(
    val profileId: Long,
    val contentKey: String,
    val impressionCount: Int = 0,
    val clickCount: Int = 0,
    val lastShownAt: Long = 0L
)
