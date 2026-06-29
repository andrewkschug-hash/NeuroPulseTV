package com.grid.tv.data.db.entity

import androidx.room.Entity

/** Per-playlist favourite live channel groups (distinct from profile channel favourite groups). */
@Entity(
    tableName = "playlist_favorite_groups",
    primaryKeys = ["playlistId", "groupKey"]
)
data class PlaylistFavoriteGroupEntity(
    val playlistId: Long,
    /** Scoped group key — [com.grid.tv.domain.model.ChannelGroupIdentity.groupKey]. */
    val groupKey: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
