package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.FeaturedBannerStatsEntity
import com.grid.tv.data.db.entity.ProfileGenreAffinityEntity

@Dao
interface FeaturedCurationDao {
    @Query("SELECT * FROM profile_genre_affinity WHERE profileId = :profileId")
    suspend fun genreAffinitiesForProfile(profileId: Long): List<ProfileGenreAffinityEntity>

    @Query("SELECT * FROM profile_genre_affinity WHERE profileId = :profileId AND genre = :genre LIMIT 1")
    suspend fun genreAffinity(profileId: Long, genre: String): ProfileGenreAffinityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenreAffinity(entity: ProfileGenreAffinityEntity)

    @Query("SELECT * FROM featured_banner_stats WHERE profileId = :profileId")
    suspend fun bannerStatsForProfile(profileId: Long): List<FeaturedBannerStatsEntity>

    @Query("SELECT * FROM featured_banner_stats WHERE profileId = :profileId AND contentKey = :contentKey LIMIT 1")
    suspend fun bannerStats(profileId: Long, contentKey: String): FeaturedBannerStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBannerStats(entity: FeaturedBannerStatsEntity)
}
