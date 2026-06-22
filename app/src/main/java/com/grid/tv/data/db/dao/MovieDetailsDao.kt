package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.MovieDetailsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDetailsDao {

    @Query("SELECT * FROM movie_details WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun get(tmdbId: Long): MovieDetailsEntity?

    @Query("SELECT * FROM movie_details WHERE tmdbId = :tmdbId LIMIT 1")
    fun observe(tmdbId: Long): Flow<MovieDetailsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MovieDetailsEntity)
}
