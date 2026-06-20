package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "vod_categories",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["playlistId", "categoryId"],
    indices = [Index("playlistId")]
)
data class VodCategoryEntity(
    val playlistId: Long,
    val categoryId: String,
    val name: String
)
