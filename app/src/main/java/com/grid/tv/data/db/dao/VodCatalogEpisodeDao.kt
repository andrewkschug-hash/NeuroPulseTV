package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.VodCatalogEpisodeEntity

@Dao
interface VodCatalogEpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<VodCatalogEpisodeEntity>)

    @Query(
        """
        SELECT * FROM vod_catalog_episodes
        WHERE playlistId = :playlistId AND seriesId = :seriesId
        ORDER BY seasonNumber ASC, episodeNumber ASC
        """
    )
    suspend fun episodesForSeries(playlistId: Long, seriesId: Long): List<VodCatalogEpisodeEntity>

    @Query(
        """
        SELECT * FROM vod_catalog_episodes
        WHERE seriesId = :seriesId
        ORDER BY seasonNumber ASC, episodeNumber ASC
        """
    )
    suspend fun episodesForSeriesGlobal(seriesId: Long): List<VodCatalogEpisodeEntity>

    @Query(
        """
        SELECT * FROM vod_catalog_episodes
        WHERE playlistId = :playlistId AND seriesId = :seriesId
          AND seasonNumber = :season AND episodeNumber = :episode
        """
    )
    suspend fun getEpisode(
        playlistId: Long,
        seriesId: Long,
        season: Int,
        episode: Int
    ): VodCatalogEpisodeEntity?

    @Query("SELECT MAX(addedAt) FROM vod_catalog_episodes WHERE playlistId = :playlistId")
    suspend fun lastCatalogUpdate(playlistId: Long): Long?

    @Query(
        "DELETE FROM vod_catalog_episodes WHERE playlistId = :playlistId AND seriesId = :seriesId"
    )
    suspend fun deleteForSeries(playlistId: Long, seriesId: Long)

    @Query("DELETE FROM vod_catalog_episodes WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)

    @Query("DELETE FROM vod_catalog_episodes")
    suspend fun deleteAll()
}
