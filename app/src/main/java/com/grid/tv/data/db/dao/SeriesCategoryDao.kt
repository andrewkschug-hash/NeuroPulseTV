package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.SeriesCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesCategoryEntity>)

    @Query("DELETE FROM series_categories WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Query("DELETE FROM series_categories")
    suspend fun clearAll()

    @Query("SELECT * FROM series_categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SeriesCategoryEntity>>

    @Query("SELECT * FROM series_categories ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SeriesCategoryEntity>

    @Query("SELECT COUNT(*) FROM series_categories")
    suspend fun countTotal(): Int
}
