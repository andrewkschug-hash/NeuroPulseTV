package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.EpgSourceChannelEntity

@Dao
interface EpgSourceChannelDao {
    @Query("SELECT * FROM epg_source_channels WHERE source = :source")
    suspend fun bySource(source: String): List<EpgSourceChannelEntity>

    @Query("SELECT MAX(cachedAt) FROM epg_source_channels WHERE source = :source")
    suspend fun lastCachedAt(source: String): Long?

    @Query("DELETE FROM epg_source_channels WHERE source = :source")
    suspend fun clearBySource(source: String)

    @Query("DELETE FROM epg_source_channels")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM epg_source_channels")
    suspend fun count(): Int

    @Query("SELECT * FROM epg_source_channels")
    suspend fun all(): List<EpgSourceChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgSourceChannelEntity>)

    @Query(
        """
        SELECT * FROM epg_source_channels
        WHERE normalizedName LIKE '%' || :normalized || '%'
        ORDER BY normalizedName ASC
        LIMIT 50
        """
    )
    suspend fun searchNormalized(normalized: String): List<EpgSourceChannelEntity>

    @Query("SELECT * FROM epg_source_channels WHERE epgId = :epgId LIMIT 1")
    suspend fun findByEpgId(epgId: String): EpgSourceChannelEntity?
}
