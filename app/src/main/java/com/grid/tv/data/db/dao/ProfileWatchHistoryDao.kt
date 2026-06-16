package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileWatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProfileWatchHistoryEntity)

    @Query("SELECT * FROM profile_watch_history WHERE profileId = :profileId AND channelId = :channelId")
    suspend fun get(profileId: Long, channelId: Long): ProfileWatchHistoryEntity?

    @Query("SELECT * FROM profile_watch_history WHERE profileId = :profileId ORDER BY lastWatched DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int): Flow<List<ProfileWatchHistoryEntity>>

    @Query("SELECT * FROM profile_watch_history WHERE profileId = :profileId ORDER BY totalWatchMs DESC LIMIT :limit")
    fun observeTop(profileId: Long, limit: Int): Flow<List<ProfileWatchHistoryEntity>>

    @Query("DELETE FROM profile_watch_history WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)

    @Query("DELETE FROM profile_watch_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM profile_watch_history WHERE profileId = :profileId AND channelId < 0")
    fun observeVodPositions(profileId: Long): Flow<List<ProfileWatchHistoryEntity>>
}
