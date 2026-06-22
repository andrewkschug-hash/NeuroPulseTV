package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vod_streams",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("streamId"),
        Index("categoryId"),
        Index("addedEpochSec"),
        Index(value = ["playlistId", "streamId"], unique = true)
    ]
)
data class VodStreamEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val streamId: Long,
    val title: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val categoryId: String? = null,
    val addedEpochSec: Long? = null
)
