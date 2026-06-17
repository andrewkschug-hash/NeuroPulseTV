package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(tableName = "epg_alias_hits")
data class EpgAliasHitEntity(
    @androidx.room.PrimaryKey val normalizedAlias: String,
    val originalNameSample: String,
    val hitCount: Long = 1,
    val lastSeenAt: Long = System.currentTimeMillis()
)
