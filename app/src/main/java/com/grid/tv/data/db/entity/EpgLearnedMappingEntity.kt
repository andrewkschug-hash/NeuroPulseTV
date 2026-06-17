package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "epg_learned_mappings",
    indices = [Index("epgId")]
)
data class EpgLearnedMappingEntity(
    @androidx.room.PrimaryKey val normalizedOriginalName: String,
    val originalNameSample: String,
    val epgId: String,
    val epgDisplayName: String,
    val source: String,
    val learnedAt: Long = System.currentTimeMillis()
)
