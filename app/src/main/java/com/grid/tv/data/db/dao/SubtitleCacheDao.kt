package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.SubtitleCacheEntity

@Dao
interface SubtitleCacheDao {
    @Query("SELECT * FROM subtitle_cache WHERE imdbId = :imdbId AND language = :language LIMIT 1")
    suspend fun get(imdbId: String, language: String): SubtitleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubtitleCacheEntity)
}
