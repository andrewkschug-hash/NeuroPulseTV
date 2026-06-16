package com.grid.tv.data.db.entity

import androidx.room.Entity

/**
 * Per-profile "Taste Genome" vector (68 dimensions).
 *
 * Stored as a compact comma-separated float list to avoid Room type converters for now.
 * Server-side enrichment can later overwrite this with higher quality signals.
 */
@Entity(tableName = "profile_taste_genome", primaryKeys = ["profileId"])
data class ProfileTasteGenomeEntity(
    val profileId: Long,
    /** 68 comma-separated floats. */
    val tasteVector: String,
    val updatedAt: Long = System.currentTimeMillis()
)

