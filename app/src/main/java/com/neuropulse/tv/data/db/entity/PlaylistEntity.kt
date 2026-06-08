package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val lastRefreshed: Long = 0,
    val refreshIntervalHours: Int = 24,
    val epgUrl: String? = null,
    val isLocalFile: Boolean = false,
    val type: String = "M3U",
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val xtreamAccountStatus: String? = null,
    val xtreamExpiryDateEpochSec: Long? = null,
    val xtreamMaxConnections: Int? = null,
    val stalkerPortalUrl: String? = null,
    val stalkerMacAddress: String? = null
)
