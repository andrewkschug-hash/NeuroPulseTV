package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.SmartGroupCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartGroupCacheDao {
    @Query("DELETE FROM smart_group_cache WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SmartGroupCacheEntity>)

    @Query("SELECT * FROM smart_group_cache ORDER BY country, category, normalizedName")
    fun observeAll(): Flow<List<SmartGroupCacheEntity>>

    @Query("SELECT * FROM smart_group_cache ORDER BY country, category, normalizedName")
    suspend fun getAll(): List<SmartGroupCacheEntity>

    @Query(
        """
        SELECT groupKey FROM smart_group_cache
        WHERE country = :country AND category = :category
        """
    )
    suspend fun groupKeysForBucket(country: String, category: String): List<String>
}
