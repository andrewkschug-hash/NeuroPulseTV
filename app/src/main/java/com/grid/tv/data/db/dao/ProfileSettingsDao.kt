package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ProfileSettingsEntity

@Dao
interface ProfileSettingsDao {
    @Query("SELECT * FROM profile_settings WHERE profileId = :profileId")
    suspend fun get(profileId: Long): ProfileSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileSettingsEntity)

    @Query("DELETE FROM profile_settings WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)

    @Query("DELETE FROM profile_settings")
    suspend fun deleteAll()
}
