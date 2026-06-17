package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vod_user_notifications",
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "readAt"])
    ]
)
data class VodUserNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val type: String,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seriesTitle: String,
    val episodeTitle: String?,
    val contentKey: String?,
    val createdAt: Long,
    val readAt: Long?,
    val pushPending: Boolean = true
)
