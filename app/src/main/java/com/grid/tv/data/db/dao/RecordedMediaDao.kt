package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.RecordedMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecordedMediaEntity)

    @Query("SELECT * FROM recorded_media ORDER BY recordedAt DESC")
    fun observeAllByDate(): Flow<List<RecordedMediaEntity>>

    @Query("SELECT * FROM recorded_media ORDER BY channelName ASC")
    fun observeAllByChannel(): Flow<List<RecordedMediaEntity>>

    @Query("SELECT * FROM recorded_media ORDER BY durationMs DESC")
    fun observeAllByDuration(): Flow<List<RecordedMediaEntity>>

    @Query("SELECT * FROM recorded_media ORDER BY fileSizeBytes DESC")
    fun observeAllBySize(): Flow<List<RecordedMediaEntity>>

    @Query("SELECT * FROM recorded_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecordedMediaEntity?

    @Query("UPDATE recorded_media SET playbackPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long)

    @Query("DELETE FROM recorded_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recorded_media")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COUNT(*) FROM recorded_media
        WHERE programTitle LIKE :seriesTitle || '%'
           OR programTitle LIKE '%' || :seriesTitle || '%'
        """
    )
    suspend fun countMatchingSeriesTitle(seriesTitle: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM recorded_media
            WHERE programTitle = :programTitle
              AND ABS(recordedAt - :programStartTime) < :windowMs
        )
        """
    )
    suspend fun hasRecordedEpisode(programTitle: String, programStartTime: Long, windowMs: Long = 86_400_000L): Boolean
}
