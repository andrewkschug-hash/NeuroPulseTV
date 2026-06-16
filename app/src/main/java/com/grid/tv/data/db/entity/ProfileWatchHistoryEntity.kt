package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "profile_watch_history", primaryKeys = ["profileId", "channelId"])
data class ProfileWatchHistoryEntity(
    val profileId: Long,
    val channelId: Long,
    val lastPosition: Long,
    val lastWatched: Long,
    val totalWatchMs: Long,
    val hourBucket: Int,
    val genreHint: String?,
    val lastProgramTitle: String? = null
)
