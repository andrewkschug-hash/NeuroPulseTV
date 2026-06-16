package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "subtitle_cache",
    primaryKeys = ["imdbId", "language"]
)
data class SubtitleCacheEntity(
    val imdbId: String,
    val language: String,
    val filePath: String,
    val sourceSubtitleId: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)
