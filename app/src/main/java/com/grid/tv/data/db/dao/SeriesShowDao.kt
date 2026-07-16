package com.grid.tv.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.SeriesShowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesShowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesShowEntity>)

    @Query("DELETE FROM series_shows WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Query(
        """
        DELETE FROM series_shows
        WHERE playlistId = :playlistId
          AND syncGeneration != :syncGeneration
        """
    )
    suspend fun deleteStaleByPlaylist(playlistId: Long, syncGeneration: Long): Int

    @Query("DELETE FROM series_shows")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM series_shows")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM series_shows")
    suspend fun countTotal(): Int

    @Query("SELECT COUNT(*) FROM series_shows WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query(
        """
        SELECT * FROM series_shows
        WHERE playlistId = :playlistId
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageByPlaylist(playlistId: Long, limit: Int, offset: Int): List<SeriesShowEntity>

    @Query(
        """
        SELECT COUNT(*) FROM series_shows
        WHERE (:category = 'All' OR IFNULL(categoryId, '') = :category OR IFNULL(genre, '') LIKE '%' || :category || '%')
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        """
    )
    suspend fun countFiltered(category: String, searchPrefix: String): Int

    @Query(
        """
        SELECT * FROM series_shows
        WHERE (:category = 'All' OR IFNULL(categoryId, '') = :category OR IFNULL(genre, '') LIKE '%' || :category || '%')
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun seriesPage(
        category: String,
        searchPrefix: String,
        limit: Int,
        offset: Int
    ): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE playlistId = :playlistId AND seriesId = :seriesId
        LIMIT 1
        """
    )
    suspend fun findBySeriesId(playlistId: Long, seriesId: Long): SeriesShowEntity?

    @Query(
        """
        SELECT * FROM series_shows
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun recent(limit: Int): List<SeriesShowEntity>

    @Query(
        """
        SELECT name FROM series_shows
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun nameBatch(limit: Int, offset: Int): List<String>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE UPPER(name) LIKE '%4K%' OR UPPER(name) LIKE '%UHD%' OR UPPER(name) LIKE '%2160P%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun fourK(limit: Int): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE IFNULL(categoryId, '') = :categoryId
           OR IFNULL(genre, '') LIKE '%' || :categoryId || '%'
           OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :categoryId || ',%'
           ))
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun byCategory(categoryId: String, limit: Int): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE playlistId = :playlistId
          AND (
            IFNULL(categoryId, '') = :categoryId
            OR IFNULL(genre, '') LIKE '%' || :categoryId || '%'
            OR (categoryIdsCsv IS NOT NULL AND (
                (',' || categoryIdsCsv || ',') LIKE '%,' || :categoryId || ',%'
            ))
          )
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun byCategoryForPlaylist(playlistId: Long, categoryId: String, limit: Int): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE searchTitle LIKE :searchPrefix ESCAPE '\'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(searchPrefix: String, limit: Int): List<SeriesShowEntity>

    @Query("SELECT * FROM series_shows WHERE rowId IN (:rowIds)")
    suspend fun byRowIds(rowIds: List<Long>): List<SeriesShowEntity>

    @Query(
        """
        SELECT DISTINCT categoryId FROM series_shows
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
        ORDER BY categoryId COLLATE NOCASE
        """
    )
    suspend fun distinctCategoryIds(): List<String>

    @Query(
        """
        SELECT DISTINCT playlistId, categoryId FROM series_shows
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
        ORDER BY categoryId COLLATE NOCASE
        """
    )
    suspend fun distinctCategoryPairs(): List<SeriesCategoryPairRow>

    @Query(
        """
        SELECT playlistId, categoryId, MIN(genre) AS genre FROM series_shows
        WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
          AND genre IS NOT NULL AND TRIM(genre) != ''
        GROUP BY playlistId, categoryId
        """
    )
    suspend fun distinctCategoryGenreHints(): List<SeriesCategoryGenreHintRow>

    @Query(
        """
        SELECT COUNT(*) FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
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
        SELECT COUNT(*) FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
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
        SELECT * FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun filteredBatchByIds(
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        limit: Int,
        offset: Int,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun filteredBatchByIdsForPlaylist(
        playlistId: Long,
        matchAll: Boolean,
        categoryIds: List<String>,
        searchPrefix: String,
        limit: Int,
        offset: Int,
        csv0: String,
        csv1: String,
        csv2: String,
        csv3: String,
        csv4: String,
        csv5: String,
        csv6: String,
        csv7: String,
    ): List<SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun seriesPagingSourceByIds(
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
    ): PagingSource<Int, SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
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
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun seriesPagingSourceByIdsForPlaylist(
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
    ): PagingSource<Int, SeriesShowEntity>

    @Query(
        """
        SELECT * FROM series_shows
        WHERE (:category = 'All' OR IFNULL(categoryId, '') = :category OR IFNULL(genre, '') LIKE '%' || :category || '%')
          AND (:searchPrefix = '' OR searchTitle LIKE :searchPrefix ESCAPE '\')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun seriesPagingSource(category: String, searchPrefix: String): PagingSource<Int, SeriesShowEntity>
}
