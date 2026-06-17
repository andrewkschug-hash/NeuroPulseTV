package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "series_follows",
    primaryKeys = ["profileId", "seriesId"]
)
data class SeriesFollowEntity(
    val profileId: Long,
    val seriesId: Long,
    val seriesTitle: String,
    val playlistId: Long,
    val following: Boolean,
    val autoFollowed: Boolean,
    val followedAt: Long
)
