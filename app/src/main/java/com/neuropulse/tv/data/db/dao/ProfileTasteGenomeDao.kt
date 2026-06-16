package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.ProfileTasteGenomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileTasteGenomeDao {

    @Query("SELECT * FROM profile_taste_genome WHERE profileId = :profileId LIMIT 1")
    suspend fun get(profileId: Long): ProfileTasteGenomeEntity?

    @Query("SELECT * FROM profile_taste_genome WHERE profileId = :profileId LIMIT 1")
    fun observe(profileId: Long): Flow<ProfileTasteGenomeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileTasteGenomeEntity)
}

