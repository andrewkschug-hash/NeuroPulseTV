package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.SeriesFollowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesFollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeriesFollowEntity)

    @Query("SELECT * FROM series_follows WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun get(profileId: Long, seriesId: Long): SeriesFollowEntity?

    @Query(
        """
        SELECT * FROM series_follows
        WHERE profileId = :profileId AND following = 1
        ORDER BY followedAt DESC
        """
    )
    suspend fun followedForProfile(profileId: Long): List<SeriesFollowEntity>

    @Query(
        """
        SELECT * FROM series_follows
        WHERE profileId = :profileId AND following = 1
        ORDER BY followedAt DESC
        """
    )
    fun observeFollowed(profileId: Long): Flow<List<SeriesFollowEntity>>

    @Query("DELETE FROM series_follows WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM series_follows")
    suspend fun deleteAll()
}
