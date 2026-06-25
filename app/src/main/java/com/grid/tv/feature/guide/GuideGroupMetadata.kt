package com.grid.tv.feature.guide

/** Coalesced channel-group metadata from a single Room GROUP BY emission. */
data class GuideGroupMetadata(
    val groups: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap()
) {
    companion object {
        val EMPTY = GuideGroupMetadata()
    }
}
