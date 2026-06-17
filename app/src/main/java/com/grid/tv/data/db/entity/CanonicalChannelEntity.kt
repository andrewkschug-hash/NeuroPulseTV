package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "canonical_channels",
    indices = [Index("normalizedName"), Index("epgId"), Index("country")]
)
data class CanonicalChannelEntity(
    @androidx.room.PrimaryKey val id: String,
    val canonicalName: String,
    val country: String,
    val epgId: String,
    val logoUrl: String? = null,
    val category: String? = null,
    /** Pipe-separated alternate names, e.g. "ESPN HD|ESPN East|ESPN US" */
    val aliases: String,
    val normalizedName: String
)
