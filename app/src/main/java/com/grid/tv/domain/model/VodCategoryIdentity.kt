package com.grid.tv.domain.model

/**
 * Playlist-scoped category identity for UI grouping, lookup, and browse rows.
 *
 * Database and DAO layers continue to use bare [categoryId]; this key is presentation-only.
 */
fun categoryKey(playlistId: Long, categoryId: String): String = "${playlistId}_$categoryId"

fun categoryKey(playlistId: String, categoryId: String): String = "${playlistId}_$categoryId"

fun categoryBrowseRowId(playlistId: Long, categoryId: String): String =
    "cat_${categoryKey(playlistId, categoryId)}"

/**
 * Parses [categoryBrowseRowId] payloads of the form `cat_{playlistId}_{categoryId}`.
 * [categoryId] may contain underscores; only the first segment after `cat_` is the playlist id.
 */
fun parseCategoryBrowseRowId(rowId: String): Pair<Long, String>? {
    if (!rowId.startsWith("cat_")) return null
    val rest = rowId.removePrefix("cat_")
    val splitIdx = rest.indexOf('_')
    if (splitIdx <= 0) return null
    val playlistId = rest.substring(0, splitIdx).toLongOrNull() ?: return null
    val categoryId = rest.substring(splitIdx + 1)
    if (categoryId.isBlank()) return null
    return playlistId to categoryId
}
