package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** Cached smart-navigation mapping for one provider group, rebuilt on playlist import. */
@Entity(
    tableName = "smart_group_cache",
    primaryKeys = ["playlistId", "groupKey"],
    indices = [Index("playlistId"), Index("country"), Index("category")]
)
data class SmartGroupCacheEntity(
    val playlistId: Long,
    val groupKey: String,
    val rawGroupName: String,
    val normalizedName: String,
    val country: String,
    val category: String,
    val channelCount: Int,
    val syncGeneration: Long,
)
