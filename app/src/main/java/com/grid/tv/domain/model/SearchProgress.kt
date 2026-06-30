package com.grid.tv.domain.model

data class SearchProgress(
    val results: UnifiedSearchResults,
    val channelsReady: Boolean,
    val vodReady: Boolean,
    val seriesReady: Boolean,
    val isComplete: Boolean,
) {
    val hasAnyResults: Boolean get() = !results.isEmpty
}
