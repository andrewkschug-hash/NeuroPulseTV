package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun schedule(recording: RecordingEntity)

    @Query("SELECT * FROM recordings ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
