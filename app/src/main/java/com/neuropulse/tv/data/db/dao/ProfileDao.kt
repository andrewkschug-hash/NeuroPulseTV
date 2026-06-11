package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.ActiveProfileEntity
import com.neuropulse.tv.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY id")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getProfile(id: Long): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity): Long

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAllProfiles()

    @Query("DELETE FROM active_profile")
    suspend fun deleteActiveProfile()

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun countProfiles(): Int

    @Query("DELETE FROM user_profiles WHERE name = 'Default'")
    suspend fun deleteDefaultProfiles()

    @Query("SELECT COUNT(*) FROM user_profiles WHERE name != 'Default'")
    suspend fun countUserProfiles(): Int

    @Query("SELECT * FROM user_profiles WHERE name != 'Default' ORDER BY id LIMIT 1")
    suspend fun firstUserProfile(): UserProfileEntity?

    @Query("SELECT * FROM active_profile WHERE singletonId = 1")
    suspend fun activeProfile(): ActiveProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActive(entity: ActiveProfileEntity)
}
