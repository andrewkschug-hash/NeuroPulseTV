package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "profile_genre_affinity",
    primaryKeys = ["profileId", "genre"],
    indices = [Index("profileId")]
)
data class ProfileGenreAffinityEntity(
    val profileId: Long,
    val genre: String,
    val score: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
