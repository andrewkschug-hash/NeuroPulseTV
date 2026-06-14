package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduledRecordingEntity): Long

    @Update
    suspend fun update(item: ScheduledRecordingEntity)

    @Query("SELECT * FROM scheduled_recordings WHERE status IN ('SCHEDULED','RECORDING') ORDER BY startTime ASC")
    fun observeUpcomingAndActive(): Flow<List<ScheduledRecordingEntity>>

    @Query("SELECT * FROM scheduled_recordings WHERE id = :id")
    suspend fun getById(id: Long): ScheduledRecordingEntity?

    @Query("SELECT * FROM scheduled_recordings WHERE status = 'SCHEDULED' ORDER BY startTime")
    suspend fun getScheduled(): List<ScheduledRecordingEntity>

    @Query("SELECT COUNT(*) FROM scheduled_recordings WHERE status = 'RECORDING'")
    suspend fun activeCount(): Int

    @Query("DELETE FROM scheduled_recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_recordings")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COUNT(*) FROM scheduled_recordings
        WHERE channelId = :channelId
          AND startTime = :startTime
          AND programTitle = :programTitle
          AND status IN ('SCHEDULED', 'RECORDING')
        """
    )
    suspend fun countExisting(channelId: Long, startTime: Long, programTitle: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM scheduled_recordings
        WHERE status IN ('SCHEDULED', 'RECORDING')
          AND (programTitle LIKE :seriesTitle || '%' OR programTitle LIKE '%' || :seriesTitle || '%')
        """
    )
    suspend fun countUpcomingMatchingSeries(seriesTitle: String): Int
}
