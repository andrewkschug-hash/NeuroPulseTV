package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "profile_favorites", primaryKeys = ["profileId", "channelId"])
data class ProfileFavoriteEntity(
    val profileId: Long,
    val channelId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: Long? = null,
    val sortOrder: Int = 0
)
