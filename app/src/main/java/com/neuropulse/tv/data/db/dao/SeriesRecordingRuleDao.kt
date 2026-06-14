package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.SeriesRecordingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesRecordingRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SeriesRecordingRuleEntity): Long

    @Query("SELECT * FROM series_recording_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SeriesRecordingRuleEntity>>

    @Query("SELECT * FROM series_recording_rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<SeriesRecordingRuleEntity>

    @Query("SELECT * FROM series_recording_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SeriesRecordingRuleEntity?

    @Query("DELETE FROM series_recording_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM series_recording_rules")
    suspend fun deleteAll()
}
