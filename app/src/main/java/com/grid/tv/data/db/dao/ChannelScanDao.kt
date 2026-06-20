package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ChannelScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChannelScanEntity)

    @Query("SELECT * FROM channel_scan WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ChannelScanEntity?

    @Query("SELECT * FROM channel_scan")
    fun observeAll(): Flow<List<ChannelScanEntity>>

    @Query("SELECT * FROM channel_scan ORDER BY lastCheckedAt DESC LIMIT :limit")
    suspend fun recentLimited(limit: Int): List<ChannelScanEntity>

    @Query("DELETE FROM channel_scan WHERE channelId NOT IN (SELECT id FROM channels)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM channel_scan")
    suspend fun deleteAll()
}
