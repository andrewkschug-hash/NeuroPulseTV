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
}
