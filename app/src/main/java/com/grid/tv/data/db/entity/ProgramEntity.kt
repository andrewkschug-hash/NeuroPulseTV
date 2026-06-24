package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    indices = [
        Index("channelEpgId"),
        Index("startTime"),
        Index("playlistId"),
        Index(value = ["playlistId", "channelEpgId"])
    ]
)
data class ProgramEntity(
    @PrimaryKey val id: Long = 0,
    val playlistId: Long = 0L,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val genre: String,
    val catchupUrl: String? = null
)
