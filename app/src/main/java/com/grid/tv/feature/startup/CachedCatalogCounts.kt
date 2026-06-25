package com.grid.tv.feature.startup

/** In-memory / persisted catalog sizes — safe to read without SQLite COUNT queries. */
data class CachedCatalogCounts(
    val movies: Int,
    val series: Int,
    val channels: Int,
    val isValid: Boolean
)
