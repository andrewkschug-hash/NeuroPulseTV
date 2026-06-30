package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vod_catalog_episodes",
    primaryKeys = ["playlistId", "seriesId", "seasonNumber", "episodeNumber"],
    indices = [
        Index("seriesId"),
        Index("addedAt"),
        Index(value = ["playlistId", "seriesId"])
    ]
)
data class VodCatalogEpisodeEntity(
    val playlistId: Long,
    val seriesId: Long,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: Long,
    val episodeTitle: String,
    val addedAt: Long,
    val extension: String = "mp4",
    val streamUrl: String = "",
    val plot: String? = null,
    val duration: String? = null,
    val seriesPlot: String? = null,
    val fetchedAt: Long = 0L
)
