package com.grid.tv.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val url: String,
    val lastRefreshed: Long,
    val refreshIntervalHours: Int,
    val epgUrl: String?,
    val type: PlaylistType = PlaylistType.M3U,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamAccountStatus: String? = null,
    val xtreamExpiryDateEpochSec: Long? = null,
    val xtreamMaxConnections: Int? = null,
    val stalkerPortalUrl: String? = null,
    val stalkerMacAddress: String? = null
)
