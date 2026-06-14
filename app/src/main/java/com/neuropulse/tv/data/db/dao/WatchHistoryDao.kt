package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.WatchHistoryEntity

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE channelId = :channelId")
    suspend fun get(channelId: Long): WatchHistoryEntity?

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}
