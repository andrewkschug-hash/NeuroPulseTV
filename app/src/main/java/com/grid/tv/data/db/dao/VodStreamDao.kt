package com.grid.tv.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.VodStreamEntity
import kotlin.DeprecationLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface VodStreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VodStreamEntity>)

    @Query("DELETE FROM vod_streams WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Query(
        """
        DELETE FROM vod_streams
        WHERE playlistId = :playlistId
          AND syncGeneration != :syncGeneration
        """
    )
    suspend fun deleteStaleByPlaylist(playlistId: Long, syncGeneration: Long): Int

    @Query("DELETE FROM vod_streams")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM vod_streams")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vod_streams")
    suspend fun countTotal(): Int

    @Query("SELECT COUNT(*) FROM vod_streams WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageByPlaylist(playlistId: Long, limit: Int, offset: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT COUNT(*) FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        """
    )
    suspend fun countFiltered(categoryId: String?, searchPrefix: String): Int

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun vodPage(
        categoryId: String?,
        searchPrefix: String,
        limit: Int,
        offset: Int
    ): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId AND streamId = :streamId
        LIMIT 1
        """
    )
    suspend fun findByStreamId(playlistId: Long, streamId: Long): VodStreamEntity?

    @Deprecated(
        message = "Use findByStreamId(playlistId, streamId). Global lookup causes cross-playlist collisions.",
        level = DeprecationLevel.ERROR
    )
    @Query("SELECT * FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun findAnyByStreamId(streamId: Long): VodStreamEntity?

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun recentForPlaylist(playlistId: Long, limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
          AND rating IS NOT NULL AND TRIM(rating) != ''
        ORDER BY CAST(rating AS REAL) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun topRatedForPlaylist(playlistId: Long, limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
          AND (UPPER(title) LIKE '%4K%' OR UPPER(title) LIKE '%UHD%' OR UPPER(title) LIKE '%2160P%')
        ORDER BY title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun fourKForPlaylist(playlistId: Long, limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun recent(limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE rating IS NOT NULL AND TRIM(rating) != ''
        ORDER BY CAST(rating AS REAL) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun topRated(limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE UPPER(title) LIKE '%4K%' OR UPPER(title) LIKE '%UHD%' OR UPPER(title) LIKE '%2160P%'
        ORDER BY title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun fourK(limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE categoryId = :categoryId
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :categoryId || ',%'
           ))
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun byCategory(categoryId: String, limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
          AND (
            categoryId = :categoryId
            OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :categoryId || ',%'
            ))
          )
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun byCategoryForPlaylist(playlistId: Long, categoryId: String, limit: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT COUNT(*) FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND playlistId = :playlistId
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        """
    )
    suspend fun countFilteredForPlaylist(playlistId: Long, categoryId: String?, searchPrefix: String): Int

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND playlistId = :playlistId
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun vodPageForPlaylist(
        playlistId: Long,
        categoryId: String?,
        searchPrefix: String,
        limit: Int,
        offset: Int
    ): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE searchTitle LIKE :searchPrefix ESCAPE '\'
           OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\'
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(searchPrefix: String, limit: Int): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE rowId IN (:rowIds)")
    suspend fun byRowIds(rowIds: List<Long>): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        ORDER BY IFNULL(addedEpochSec, 0) DESC, rowId DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun samplePage(limit: Int, offset: Int): List<VodStreamEntity>

    @Query(
        """
        SELECT title FROM vod_streams
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun titleBatch(limit: Int, offset: Int): List<String>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        """
    )
    fun vodPagingSource(categoryId: String?, searchPrefix: String): PagingSource<Int, VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND playlistId = :playlistId
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        """
    )
    fun vodPagingSourceForPlaylist(
        playlistId: Long,
        categoryId: String?,
        searchPrefix: String
    ): PagingSource<Int, VodStreamEntity>

    @Query(
        """
        SELECT COUNT(*) FROM vod_streams
        WHERE (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds)
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :csv0 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv1 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv2 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv3 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv4 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv5 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv6 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv7 || ',%'
           )))
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        """
    )
    suspend fun countFilteredByIds(
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM vod_streams
        WHERE playlistId = :playlistId
          AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds)
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :csv0 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv1 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv2 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv3 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv4 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv5 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv6 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv7 || ',%'
           )))
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        """
    )
    suspend fun countFilteredByIdsForPlaylist(
        playlistId: Long,
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): Int

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds)
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :csv0 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv1 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv2 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv3 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv4 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv5 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv6 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv7 || ',%'
           )))
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        """
    )
    fun vodPagingSourceByIds(
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): PagingSource<Int, VodStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams
        WHERE playlistId = :playlistId
          AND (:matchAll = 1 OR IFNULL(categoryId, '') IN (:categoryIds)
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :csv0 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv1 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv2 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv3 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv4 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv5 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv6 || ',%'
                OR (',' || categoryIdsCsv || ',') LIKE '%,' || :csv7 || ',%'
           )))
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\' OR LOWER(IFNULL(genre, '')) LIKE :searchPrefix ESCAPE '\')
        ORDER BY IFNULL(addedEpochSec, 0) DESC, title COLLATE NOCASE
        """
    )
    fun vodPagingSourceByIdsForPlaylist(
        playlistId: Long,
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): PagingSource<Int, VodStreamEntity>

    @Query(
        """
        SELECT DISTINCT playlistId, categoryId FROM vod_streams
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
        ORDER BY categoryId COLLATE NOCASE
        """
    )
    suspend fun distinctCategoryPairs(): List<VodCategoryPairRow>

    @Query(
        """
        SELECT playlistId, categoryId, MIN(genre) AS genre FROM vod_streams
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
          AND genre IS NOT NULL AND TRIM(genre) != ''
        GROUP BY playlistId, categoryId
        """
    )
    suspend fun distinctCategoryGenreHints(): List<VodCategoryGenreHintRow>

    /**
     * Distinct playlist/category pairs ranked by title count (for browse rows when category API is empty).
     */
    @Query(
        """
        SELECT playlistId, categoryId, COUNT(*) AS streamCount FROM vod_streams
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
        GROUP BY playlistId, categoryId
        ORDER BY streamCount DESC, categoryId COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun topCategoryPairsByStreamCount(limit: Int): List<VodCategoryCountRow>
}
