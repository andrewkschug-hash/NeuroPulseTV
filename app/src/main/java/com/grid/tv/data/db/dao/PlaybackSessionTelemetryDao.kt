package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity

@Dao
interface PlaybackSessionTelemetryDao {
    @Insert
    suspend fun insert(session: PlaybackSessionTelemetryEntity): Long

    @Insert
    suspend fun insertAll(sessions: List<PlaybackSessionTelemetryEntity>)

    @Query("SELECT * FROM playback_session_telemetry WHERE channelId = :channelId ORDER BY sessionStart DESC LIMIT :limit")
    suspend fun recentForChannel(channelId: Long, limit: Int): List<PlaybackSessionTelemetryEntity>

    @Query("SELECT * FROM playback_session_telemetry WHERE streamId = :streamId AND channelId = :channelId ORDER BY sessionStart DESC LIMIT :limit")
    suspend fun recentForStream(channelId: Long, streamId: String, limit: Int): List<PlaybackSessionTelemetryEntity>

    @Query("SELECT * FROM playback_session_telemetry WHERE providerId = :providerId ORDER BY sessionStart DESC LIMIT :limit")
    suspend fun recentForProvider(providerId: Long, limit: Int): List<PlaybackSessionTelemetryEntity>

    @Query("SELECT COUNT(*) FROM playback_session_telemetry")
    suspend fun count(): Int

    @Query("SELECT * FROM playback_session_telemetry ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<PlaybackSessionTelemetryEntity>

    @Query("DELETE FROM playback_session_telemetry")
    suspend fun deleteAll()
}
