package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "favorites", primaryKeys = ["channelId"])
data class FavoriteEntity(
    val channelId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
