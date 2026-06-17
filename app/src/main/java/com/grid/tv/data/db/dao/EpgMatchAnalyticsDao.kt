package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.EpgMatchAnalyticsEntity

@Dao
interface EpgMatchAnalyticsDao {
    @Query("SELECT * FROM epg_match_analytics WHERE id = 1")
    suspend fun get(): EpgMatchAnalyticsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EpgMatchAnalyticsEntity)

    @Query("DELETE FROM epg_match_analytics")
    suspend fun deleteAll()
}
