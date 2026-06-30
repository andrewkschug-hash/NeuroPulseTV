package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Query

data class VodSearchHitRow(
    val contentType: String,
    val rowId: Long,
    val sortTitle: String
)

@Dao
interface VodSearchDao {
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT rowId FROM vod_streams
            WHERE (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
              AND (:playlistScoped = 0 OR playlistId = :playlistId)
              AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds))
            UNION ALL
            SELECT rowId FROM series_shows
            WHERE (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
              AND (:playlistScoped = 0 OR playlistId = :playlistId)
              AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds))
        )
        """
    )
    suspend fun unifiedSearchCount(
        searchPrefix: String,
        playlistScoped: Boolean,
        playlistId: Long,
        matchAll: Boolean,
        categoryIds: List<String>
    ): Int

    @Query(
        """
        SELECT contentType, rowId, sortTitle FROM (
            SELECT 'movie' AS contentType, rowId AS rowId, searchTitle AS sortTitle
            FROM vod_streams
            WHERE (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
              AND (:playlistScoped = 0 OR playlistId = :playlistId)
              AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds))
            UNION ALL
            SELECT 'series' AS contentType, rowId AS rowId, searchTitle AS sortTitle
            FROM series_shows
            WHERE (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
              AND (:playlistScoped = 0 OR playlistId = :playlistId)
              AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds))
        )
        ORDER BY sortTitle COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun unifiedSearchHits(
        searchPrefix: String,
        playlistScoped: Boolean,
        playlistId: Long,
        matchAll: Boolean,
        categoryIds: List<String>,
        limit: Int,
        offset: Int
    ): List<VodSearchHitRow>
}
