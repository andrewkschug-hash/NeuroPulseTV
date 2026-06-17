package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.StreamFailoverStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamFailoverStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StreamFailoverStatsEntity)

    @Query("SELECT * FROM stream_failover_stats WHERE channelId = :channelId")
    suspend fun get(channelId: Long): StreamFailoverStatsEntity?

    @Query(
        """
        SELECT * FROM stream_failover_stats
        WHERE failoverCount > 0
        ORDER BY failoverCount DESC, lastFailoverAt DESC
        LIMIT :limit
        """
    )
    fun problematicChannels(limit: Int = 20): Flow<List<StreamFailoverStatsEntity>>

    @Query("SELECT * FROM stream_failover_stats")
    fun observeAll(): Flow<List<StreamFailoverStatsEntity>>
}
