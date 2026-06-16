package com.grid.tv.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "epg_resolution_suggestions",
    primaryKeys = ["channelId", "suggestedEpgId"]
)
data class EpgResolutionSuggestionEntity(
    val channelId: String,
    val suggestedEpgId: String,
    val suggestedEpgName: String,
    val confidence: Int,
    val source: String,
    val isDismissed: Boolean = false
)
