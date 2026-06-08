package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.ProfileFavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProfileFavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(item: ProfileFavoriteEntity)

    @Query("DELETE FROM profile_favorites WHERE profileId = :profileId AND channelId = :channelId")
    suspend fun remove(profileId: Long, channelId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM profile_favorites WHERE profileId = :profileId AND channelId = :channelId)")
    fun observeIsFavorite(profileId: Long, channelId: Long): Flow<Boolean>

    @Query("SELECT * FROM profile_favorites WHERE profileId = :profileId ORDER BY sortOrder, createdAt")
    fun observeForProfile(profileId: Long): Flow<List<ProfileFavoriteEntity>>

    @Query("SELECT channelId FROM profile_favorites WHERE profileId = :profileId AND (:groupId IS NULL OR groupId = :groupId)")
    suspend fun channelIdsForGroup(profileId: Long, groupId: Long?): List<Long>
}
