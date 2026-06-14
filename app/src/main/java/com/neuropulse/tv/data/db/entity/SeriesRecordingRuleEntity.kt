package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "series_recording_rules")
data class SeriesRecordingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesTitle: String,
    val seriesId: Long? = null,
    val recordNewOnly: Boolean = true,
    val playlistId: Long,
    val paddingStartMins: Int = 2,
    val paddingEndMins: Int = 5,
    val maxEpisodesToKeep: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
