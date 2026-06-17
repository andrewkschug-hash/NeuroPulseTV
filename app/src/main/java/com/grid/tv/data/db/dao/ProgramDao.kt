package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE endTime < :minEndTime")
    suspend fun purgeOlderThan(minEndTime: Long)

    @Query("DELETE FROM programs")
    suspend fun clearAll()

    @Query("SELECT * FROM programs WHERE channelEpgId IN (:epgIds) AND endTime >= :fromTime ORDER BY startTime")
    fun observeGrid(epgIds: List<String>, fromTime: Long): Flow<List<ProgramEntity>>

        @Query("SELECT * FROM programs WHERE channelEpgId IN (:epgIds) AND startTime < :windowEnd AND endTime > :windowStart ORDER BY startTime")
        suspend fun loadWindow(epgIds: List<String>, windowStart: Long, windowEnd: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE LOWER(channelEpgId) IN (:epgIdsLower)
            AND startTime < :windowEnd AND endTime > :windowStart
        ORDER BY startTime
        """
    )
    suspend fun loadWindowIgnoreCase(
        epgIdsLower: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE title LIKE '%' || :query || '%' ORDER BY startTime LIMIT 100")
    fun observeSearch(query: String): Flow<List<ProgramEntity>>

        @Query(
                """
                SELECT * FROM programs
                WHERE (genre = 'SPORTS' OR title LIKE '%live%' OR title LIKE '%vs%' OR title LIKE '%match%')
                    AND endTime >= :fromTime
                ORDER BY startTime
                """
        )
        fun observeSports(fromTime: Long): Flow<List<ProgramEntity>>

    @Query("SELECT DISTINCT channelEpgId FROM programs WHERE channelEpgId != ''")
    suspend fun distinctChannelEpgIds(): List<String>

    @Query(
        """
        SELECT * FROM programs
        WHERE endTime >= :fromTime
          AND (
            title LIKE :seriesTitle || '%'
            OR title LIKE '%' || :seriesTitle || '%'
          )
        ORDER BY startTime ASC
        """
    )
    suspend fun findUpcomingBySeriesTitle(fromTime: Long, seriesTitle: String): List<ProgramEntity>
}
