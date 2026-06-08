package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.StreamHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StreamHealthEntity)

    @Query("SELECT * FROM stream_health WHERE channelId = :channelId")
    suspend fun get(channelId: Long): StreamHealthEntity?

    @Query("SELECT * FROM stream_health ORDER BY reliabilityScore DESC LIMIT :limit")
    fun best(limit: Int): Flow<List<StreamHealthEntity>>

    @Query("SELECT * FROM stream_health ORDER BY reliabilityScore ASC LIMIT :limit")
    fun worst(limit: Int): Flow<List<StreamHealthEntity>>
}
