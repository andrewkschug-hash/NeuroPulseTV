package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.EpgResolutionSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgResolutionSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpgResolutionSuggestionEntity)

    @Query("DELETE FROM epg_resolution_suggestions WHERE channelId = :channelId")
    suspend fun clearForChannel(channelId: String)

    @Query("UPDATE epg_resolution_suggestions SET isDismissed = 1 WHERE channelId = :channelId")
    suspend fun dismissByChannel(channelId: String)

    @Query("SELECT * FROM epg_resolution_suggestions WHERE isDismissed = 0")
    fun observeActive(): Flow<List<EpgResolutionSuggestionEntity>>

    @Query("SELECT * FROM epg_resolution_suggestions WHERE isDismissed = 0")
    suspend fun activeNow(): List<EpgResolutionSuggestionEntity>

    @Query("DELETE FROM epg_resolution_suggestions")
    suspend fun deleteAll()
}
