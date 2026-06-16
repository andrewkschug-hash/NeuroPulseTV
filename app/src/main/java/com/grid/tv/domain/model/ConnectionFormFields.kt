package com.grid.tv.domain.model

data class ConnectionFormFields(
    val name: String = "",
    val playlistType: PlaylistType = PlaylistType.M3U,
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val refreshHours: String = "24",
    val xtreamServer: String = "",
    val xtreamUser: String = "",
    val xtreamPassword: String = ""
)
