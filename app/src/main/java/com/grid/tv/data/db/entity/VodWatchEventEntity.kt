package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vod_watch_events",
    indices = [
        Index("profileId"),
        Index("seriesId"),
        Index(value = ["profileId", "seriesId"])
    ]
)
data class VodWatchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val contentId: String,
    val contentType: String,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val progressPercent: Float,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatched: Long
)
