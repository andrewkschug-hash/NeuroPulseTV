package com.grid.tv.domain.model

/**
 * Per-playlist series catalog hydration lifecycle.
 *
 * - [NEVER_FETCHED]: no successful series ingest attempt yet — tab open may auto-fetch once.
 * - [EMPTY]: provider confirmed zero series — skip auto-fetch on tab open.
 * - [POPULATED]: series rows exist on disk — skip auto-fetch on tab open.
 */
enum class SeriesCatalogHydrationState {
    NEVER_FETCHED,
    EMPTY,
    POPULATED,
}
