package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.EpgLearnedMappingEntity

@Dao
interface EpgLearnedMappingDao {
    @Query("SELECT * FROM epg_learned_mappings WHERE normalizedOriginalName = :normalized LIMIT 1")
    suspend fun get(normalized: String): EpgLearnedMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: EpgLearnedMappingEntity)

    @Query("SELECT * FROM epg_learned_mappings ORDER BY learnedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EpgLearnedMappingEntity>

    @Query("DELETE FROM epg_learned_mappings")
    suspend fun deleteAll()
}
