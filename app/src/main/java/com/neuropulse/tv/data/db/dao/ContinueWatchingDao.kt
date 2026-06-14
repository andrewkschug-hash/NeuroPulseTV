package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.ContinueWatchingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContinueWatchingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContinueWatchingEntity)

    @Query(
        """
        SELECT * FROM continue_watching
        WHERE profileId = :profileId
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
        """
    )
    fun observeForProfile(profileId: Long, limit: Int): Flow<List<ContinueWatchingEntity>>

    @Query("SELECT * FROM continue_watching WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun get(profileId: Long, contentKey: String): ContinueWatchingEntity?

    @Query("DELETE FROM continue_watching WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun delete(profileId: Long, contentKey: String)

    @Query("DELETE FROM continue_watching WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)

    @Query("DELETE FROM continue_watching")
    suspend fun deleteAll()
}
