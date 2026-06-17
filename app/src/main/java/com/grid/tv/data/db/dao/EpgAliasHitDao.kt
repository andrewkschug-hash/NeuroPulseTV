package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.EpgAliasHitEntity

@Dao
interface EpgAliasHitDao {
    @Query("SELECT * FROM epg_alias_hits WHERE normalizedAlias = :normalized LIMIT 1")
    suspend fun get(normalized: String): EpgAliasHitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EpgAliasHitEntity)

    @Query("SELECT * FROM epg_alias_hits ORDER BY hitCount DESC LIMIT :limit")
    suspend fun topAliases(limit: Int): List<EpgAliasHitEntity>

    @Query("DELETE FROM epg_alias_hits")
    suspend fun deleteAll()
}
