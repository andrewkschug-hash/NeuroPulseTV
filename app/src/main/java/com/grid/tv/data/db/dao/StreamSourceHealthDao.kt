package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.StreamSourceHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSourceHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreamSourceHealthEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StreamSourceHealthEntity>)

    @Query("SELECT * FROM stream_source_health WHERE channelId = :channelId AND streamId = :streamId")
    suspend fun get(channelId: Long, streamId: String): StreamSourceHealthEntity?

    @Query("SELECT * FROM stream_source_health WHERE channelId = :channelId ORDER BY healthScore DESC")
    suspend fun forChannel(channelId: Long): List<StreamSourceHealthEntity>

    @Query("SELECT * FROM stream_source_health WHERE channelId = :channelId ORDER BY healthScore DESC")
    fun observeForChannel(channelId: Long): Flow<List<StreamSourceHealthEntity>>

    @Query("DELETE FROM stream_source_health")
    suspend fun deleteAll()
}
