package com.grid.tv.domain.model

data class Channel(
    val id: Long,
    val number: Int,
    val name: String,
    val group: String,
    val logoUrl: String?,
    val epgId: String?,
    val streamUrl: String,
    val backupStreamUrl: String? = null,
    val playlistId: Long,
    val playlistName: String? = null,
    val isFavorite: Boolean,
    val reliabilityScore: Int = 50,
    val currentProgram: String? = null,
    val catchupDays: Int = 0,
    val catchupSource: String? = null,
    val epgResolutionStatus: EpgResolutionStatus = EpgResolutionStatus.UNRESOLVED,
    val epgResolutionConfidence: Int = 0,
    val epgResolutionSource: String? = null
)
