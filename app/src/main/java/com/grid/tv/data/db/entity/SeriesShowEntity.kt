package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series_shows",
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
        Index(value = ["playlistId", "seriesId"], unique = true),
        Index("searchTitle"),
        Index(value = ["playlistId", "searchTitle"])
    ]
)
data class SeriesShowEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val seriesId: Long,
    val name: String,
    /** Lowercase, tag-stripped title for indexed prefix search. */
    val searchTitle: String = "",
    val coverUrl: String? = null,
    val categoryId: String? = null,
    val genre: String? = null,
    val syncGeneration: Long = 0L
)
