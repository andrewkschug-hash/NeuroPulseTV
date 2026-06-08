package com.neuropulse.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "profile_favorites", primaryKeys = ["profileId", "channelId"])
data class ProfileFavoriteEntity(
    val profileId: Long,
    val channelId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
