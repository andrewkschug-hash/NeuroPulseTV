package com.neuropulse.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "continue_watching", primaryKeys = ["profileId", "contentKey"])
data class ContinueWatchingEntity(
    val profileId: Long,
    val contentKey: String,
    val contentType: String,
    val streamId: Long?,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val title: String,
    val posterUrl: String?,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long
)
