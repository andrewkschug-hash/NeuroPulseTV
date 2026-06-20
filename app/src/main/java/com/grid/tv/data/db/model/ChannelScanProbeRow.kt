package com.grid.tv.data.db.model

/** Minimal channel fields for health-probe scans (avoids loading full [ChannelEntity] rows). */
data class ChannelScanProbeRow(
    val id: Long,
    val streamUrl: String
)
