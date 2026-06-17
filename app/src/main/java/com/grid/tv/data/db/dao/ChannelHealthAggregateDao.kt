package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ChannelHealthAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelHealthAggregateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChannelHealthAggregateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChannelHealthAggregateEntity>)

    @Query("SELECT * FROM channel_health_aggregate WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ChannelHealthAggregateEntity?

    @Query("SELECT * FROM channel_health_aggregate WHERE channelId = :channelId")
    fun observe(channelId: Long): Flow<ChannelHealthAggregateEntity?>

    @Query("SELECT * FROM channel_health_aggregate ORDER BY healthScore DESC LIMIT :limit")
    suspend fun topReliable(limit: Int): List<ChannelHealthAggregateEntity>

    @Query("SELECT * FROM channel_health_aggregate ORDER BY healthScore ASC LIMIT :limit")
    suspend fun problemChannels(limit: Int): List<ChannelHealthAggregateEntity>

    @Query("SELECT AVG(healthScore) FROM channel_health_aggregate")
    suspend fun averageScore(): Double?

    @Query("DELETE FROM channel_health_aggregate")
    suspend fun deleteAll()
}
