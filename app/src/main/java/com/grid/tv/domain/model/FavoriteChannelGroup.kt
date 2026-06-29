package com.grid.tv.domain.model

data class FavoriteChannelGroup(
    val playlistId: Long,
    val groupKey: String,
    val sortOrder: Int,
    val createdAt: Long
)
