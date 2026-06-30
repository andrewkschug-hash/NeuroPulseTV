package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
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
        Index("number"),
        Index("groupName"),
        Index("epgId"),
        Index("searchTitle"),
        Index(value = ["playlistId", "searchTitle"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val name: String,
    /** Lowercase, tag-stripped title for indexed prefix search. */
    val searchTitle: String = "",
    val groupName: String,
    val logoUrl: String?,
    val epgId: String?,
    val streamUrl: String,
    val backupStreamUrl: String? = null,
    val backupStreamUrl2: String? = null,
    val backupStreamUrl3: String? = null,
    val playlistId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val catchupMode: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val epgResolutionStatus: String = "UNRESOLVED",
    val epgResolutionConfidence: Int = 0,
    val epgResolutionSource: String? = null,
    val epgLastAttemptAt: Long = 0
)
