package com.grid.tv.domain.model

data class PlaylistConnectResult(
    val success: Boolean,
    val playlistName: String,
    val channelCount: Int = 0,
    val errorMessage: String? = null
)
