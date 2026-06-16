package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "epg_source_channels",
    primaryKeys = ["epgId", "source"],
    indices = [Index("source"), Index("normalizedName")]
)
data class EpgSourceChannelEntity(
    val epgId: String,
    val displayName: String,
    val normalizedName: String,
    val source: String,
    val logoUrl: String?,
    val cachedAt: Long
)
