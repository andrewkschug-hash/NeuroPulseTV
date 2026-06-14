package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.FavoriteGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: FavoriteGroupEntity): Long

    @Query("SELECT * FROM favorite_groups WHERE profileId = :profileId ORDER BY sortOrder, name")
    fun observeForProfile(profileId: Long): Flow<List<FavoriteGroupEntity>>

    @Query("SELECT * FROM favorite_groups WHERE profileId = :profileId ORDER BY sortOrder, name")
    suspend fun getAllForProfile(profileId: Long): List<FavoriteGroupEntity>

    @Query("SELECT * FROM favorite_groups WHERE profileId = :profileId AND name = :name LIMIT 1")
    suspend fun getByName(profileId: Long, name: String): FavoriteGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(group: FavoriteGroupEntity)

    @Query("SELECT * FROM favorite_groups WHERE id = :id")
    suspend fun getById(id: Long): FavoriteGroupEntity?

    @Query("DELETE FROM favorite_groups WHERE id = :id AND profileId = :profileId")
    suspend fun delete(id: Long, profileId: Long)

    @Query("DELETE FROM favorite_groups WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)

    @Query("DELETE FROM favorite_groups")
    suspend fun deleteAll()
}
