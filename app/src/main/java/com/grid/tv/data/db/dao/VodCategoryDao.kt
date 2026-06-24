package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.VodCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VodCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VodCategoryEntity>)

    @Query("DELETE FROM vod_categories WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Query("DELETE FROM vod_categories")
    suspend fun clearAll()

    @Query("SELECT * FROM vod_categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VodCategoryEntity>>

    @Query("SELECT * FROM vod_categories ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<VodCategoryEntity>

    @Query("SELECT * FROM vod_categories ORDER BY name COLLATE NOCASE LIMIT :limit")
    suspend fun topCategories(limit: Int): List<VodCategoryEntity>

    @Query("SELECT COUNT(*) FROM vod_categories")
    suspend fun countTotal(): Int

    /**
     * Categories ranked by number of titles in [vod_streams], largest first.
     */
    @Query(
        """
        SELECT c.playlistId, c.categoryId, c.name
        FROM vod_categories c
        INNER JOIN vod_streams s
            ON s.playlistId = c.playlistId AND s.categoryId = c.categoryId
        GROUP BY c.playlistId, c.categoryId, c.name
        ORDER BY COUNT(*) DESC, c.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun topCategoriesByStreamCount(limit: Int): List<VodCategoryEntity>
}
