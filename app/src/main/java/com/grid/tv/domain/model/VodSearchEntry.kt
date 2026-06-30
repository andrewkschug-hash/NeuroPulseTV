package com.grid.tv.domain.model

/** Unified VOD hub search result — movie or series from a single query layer. */
sealed class VodSearchEntry {
    data class Movie(val item: VodItem) : VodSearchEntry()
    data class Series(val show: SeriesShow) : VodSearchEntry()
}
