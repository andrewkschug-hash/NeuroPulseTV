package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.ChannelScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChannelScanEntity)

    @Query("SELECT * FROM channel_scan WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ChannelScanEntity?

    @Query("SELECT * FROM channel_scan")
    fun observeAll(): Flow<List<ChannelScanEntity>>

    @Query("SELECT * FROM channel_scan")
    suspend fun all(): List<ChannelScanEntity>

    @Query("DELETE FROM channel_scan")
    suspend fun deleteAll()
}
