package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.CanonicalChannelEntity

@Dao
interface CanonicalChannelDao {
    @Query("SELECT COUNT(*) FROM canonical_channels")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CanonicalChannelEntity>)

    @Query("SELECT * FROM canonical_channels")
    suspend fun all(): List<CanonicalChannelEntity>

    @Query("SELECT * FROM canonical_channels WHERE normalizedName = :normalized LIMIT 1")
    suspend fun byNormalizedName(normalized: String): CanonicalChannelEntity?

    @Query("DELETE FROM canonical_channels")
    suspend fun deleteAll()
}
