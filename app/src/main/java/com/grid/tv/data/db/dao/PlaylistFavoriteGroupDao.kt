package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.PlaylistFavoriteGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistFavoriteGroupDao {
    @Query(
        """
        SELECT groupKey FROM playlist_favorite_groups
        WHERE playlistId = :playlistId
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeGroupKeys(playlistId: Long): Flow<List<String>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_favorite_groups
            WHERE playlistId = :playlistId AND groupKey = :groupKey
        )
        """
    )
    fun observeIsFavorite(playlistId: Long, groupKey: String): Flow<Boolean>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_favorite_groups
            WHERE playlistId = :playlistId AND groupKey = :groupKey
        )
        """
    )
    suspend fun isFavorite(playlistId: Long, groupKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistFavoriteGroupEntity)

    @Query(
        """
        DELETE FROM playlist_favorite_groups
        WHERE playlistId = :playlistId AND groupKey = :groupKey
        """
    )
    suspend fun delete(playlistId: Long, groupKey: String)

    @Query(
        """
        SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_favorite_groups
        WHERE playlistId = :playlistId
        """
    )
    suspend fun maxSortOrder(playlistId: Long): Int

    @Query("DELETE FROM playlist_favorite_groups WHERE playlistId = :playlistId")
    suspend fun deleteAllForPlaylist(playlistId: Long)
}
