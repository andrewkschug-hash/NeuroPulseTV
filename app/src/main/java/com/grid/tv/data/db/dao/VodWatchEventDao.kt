package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.VodWatchEventEntity

@Dao
interface VodWatchEventDao {
    @Insert
    suspend fun insert(event: VodWatchEventEntity): Long

    @Query(
        """
        SELECT * FROM vod_watch_events
        WHERE profileId = :profileId AND seriesId = :seriesId
        ORDER BY lastWatched DESC
        LIMIT 1
        """
    )
    suspend fun latestForSeries(profileId: Long, seriesId: Long): VodWatchEventEntity?

    @Query(
        """
        SELECT * FROM vod_watch_events
        WHERE profileId = :profileId AND contentType = 'EPISODE'
        ORDER BY lastWatched DESC
        LIMIT :limit
        """
    )
    suspend fun recentEpisodes(profileId: Long, limit: Int): List<VodWatchEventEntity>

    @Query(
        """
        SELECT * FROM vod_watch_events
        WHERE profileId = :profileId
        ORDER BY lastWatched DESC
        LIMIT :limit
        """
    )
    suspend fun recentAll(profileId: Long, limit: Int): List<VodWatchEventEntity>

    @Query(
        """
        SELECT seriesId, COUNT(*) AS episodeCount
        FROM vod_watch_events
        WHERE profileId = :profileId AND seriesId IS NOT NULL AND contentType = 'EPISODE'
        GROUP BY seriesId
        HAVING COUNT(*) >= :minEpisodes
        """
    )
    suspend fun seriesWithMinEpisodes(profileId: Long, minEpisodes: Int): List<SeriesWatchCountRow>

    @Query("DELETE FROM vod_watch_events WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM vod_watch_events")
    suspend fun deleteAll()
}

data class SeriesWatchCountRow(
    val seriesId: Long,
    val episodeCount: Int
)
