package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.model.ChannelScanProbeRow
import com.grid.tv.data.db.model.GroupChannelCountRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun clearByPlaylist(playlistId: Long)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query(
        """
        SELECT playlistId, groupName, COUNT(*) AS channelCount
        FROM channels
        WHERE groupName IS NOT NULL AND TRIM(groupName) != ''
        GROUP BY playlistId, groupName
        ORDER BY playlistId, groupName COLLATE NOCASE
        """
    )
    fun observeGroupChannelCounts(): Flow<List<GroupChannelCountRow>>

    @Query(
        """
        SELECT c.* FROM channels c
      LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (:filterPlaylistId < 0 OR c.playlistId = :filterPlaylistId)
          AND (:filterGroupName IS NULL OR c.groupName = :filterGroupName)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
        ORDER BY CASE WHEN f.sortOrder IS NULL THEN c.number ELSE f.sortOrder END, c.number
        """
    )
    fun observeChannels(
        filterPlaylistId: Long,
        filterGroupName: String?,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long = -1L
    ): Flow<List<ChannelEntity>>

    @Query(
      """
      SELECT c.* FROM channels c
      INNER JOIN playlists p ON p.id = c.playlistId
      WHERE c.name LIKE '%' || :query || '%' OR p.name LIKE '%' || :query || '%'
      ORDER BY c.number
      """
    )
    fun searchAllPlaylists(query: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun getByNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getById(channelId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels")
    fun observeTotalCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM channels c
        LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (:filterPlaylistId < 0 OR c.playlistId = :filterPlaylistId)
          AND (:filterGroupName IS NULL OR c.groupName = :filterGroupName)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
        """
    )
    suspend fun countChannels(
        filterPlaylistId: Long,
        filterGroupName: String?,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM channels c
        LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (:filterPlaylistId < 0 OR c.playlistId = :filterPlaylistId)
          AND (:filterGroupName IS NULL OR c.groupName = :filterGroupName)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
          AND (:matchSports = 0 OR c.epgId IN (:sportsEpgIds))
        """
    )
    suspend fun countChannelsFiltered(
        filterPlaylistId: Long,
        filterGroupName: String?,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long,
        matchSports: Boolean,
        sportsEpgIds: List<String>
    ): Int

    @Query(
        """
        SELECT c.* FROM channels c
        LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (:filterPlaylistId < 0 OR c.playlistId = :filterPlaylistId)
          AND (:filterGroupName IS NULL OR c.groupName = :filterGroupName)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
          AND (:matchSports = 0 OR c.epgId IN (:sportsEpgIds))
        ORDER BY CASE WHEN f.sortOrder IS NULL THEN c.number ELSE f.sortOrder END, c.number
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun channelsPageFiltered(
        filterPlaylistId: Long,
        filterGroupName: String?,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long,
        matchSports: Boolean,
        sportsEpgIds: List<String>,
        limit: Int,
        offset: Int
    ): List<ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (:filterPlaylistId < 0 OR c.playlistId = :filterPlaylistId)
          AND (:filterGroupName IS NULL OR c.groupName = :filterGroupName)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
        ORDER BY CASE WHEN f.sortOrder IS NULL THEN c.number ELSE f.sortOrder END, c.number
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun channelsPage(
        filterPlaylistId: Long,
        filterGroupName: String?,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long,
        limit: Int,
        offset: Int
    ): List<ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        LEFT JOIN profile_favorites f ON f.channelId = c.id AND f.profileId = :profileId
        WHERE (c.playlistId || char(31) || c.groupName) IN (:groupKeys)
          AND (:onlyFavorites = 0 OR f.channelId IS NOT NULL)
          AND (:favoriteGroupId < 0 OR f.groupId = :favoriteGroupId)
          AND c.name LIKE '%' || :search || '%'
        ORDER BY CASE WHEN f.sortOrder IS NULL THEN c.number ELSE f.sortOrder END, c.number
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun channelsPageInGroups(
        groupKeys: List<String>,
        search: String,
        onlyFavorites: Boolean,
        profileId: Long,
        favoriteGroupId: Long,
        limit: Int,
        offset: Int
    ): List<ChannelEntity>

    @Query("SELECT id, streamUrl FROM channels ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun scanProbeBatch(limit: Int, offset: Int): List<ChannelScanProbeRow>

    @Query("SELECT id, streamUrl FROM channels WHERE id IN (:ids)")
    suspend fun scanProbeByIds(ids: List<Long>): List<ChannelScanProbeRow>

    @Query("SELECT * FROM channels ORDER BY number")
    suspend fun all(): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun countTotal(): Int

    @Query("SELECT id FROM channels WHERE id IN (:ids)")
    suspend fun filterExistingIds(ids: List<Long>): List<Long>

    @Query("SELECT COUNT(*) FROM channels WHERE epgResolutionStatus = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT MAX(epgLastAttemptAt) FROM channels")
    suspend fun lastResolvedAt(): Long?

    @Query(
      """
      SELECT * FROM channels
      WHERE (
        (
          (epgId IS NULL OR epgId = '')
          AND epgResolutionStatus IN ('UNRESOLVED', 'SUGGESTED', 'AUTO_MATCHED')
        )
        OR (
          epgResolutionStatus = 'UNRESOLVABLE' AND epgLastAttemptAt <= :rerunUnresolvableBefore
        )
      )
      AND (:createdAfter <= 0 OR createdAt >= :createdAfter)
      ORDER BY id
      LIMIT :limit OFFSET :offset
      """
    )
    suspend fun unresolvedBatch(limit: Int, offset: Int, createdAfter: Long, rerunUnresolvableBefore: Long): List<ChannelEntity>

    @Query("UPDATE channels SET epgId = :epgId, epgResolutionStatus = :status, epgResolutionConfidence = :confidence, epgResolutionSource = :source, epgLastAttemptAt = :attemptAt WHERE id = :channelId")
    suspend fun applyResolution(channelId: Long, epgId: String?, status: String, confidence: Int, source: String?, attemptAt: Long)

    @Query(
        """
        UPDATE channels
        SET epgResolutionStatus = 'UNRESOLVED', epgResolutionConfidence = 0, epgLastAttemptAt = 0
        WHERE playlistId = :playlistId
          AND epgId IS NOT NULL AND TRIM(epgId) != ''
          AND epgId NOT IN (SELECT epgId FROM epg_source_channels WHERE source = :sourceKey)
          AND epgResolutionStatus NOT IN ('CONFIRMED', 'MANUAL')
        """
    )
    suspend fun markUnlinkedEpgIdsUnresolved(playlistId: Long, sourceKey: String): Int

    @Query("SELECT * FROM channels WHERE epgId = :epgId AND playlistId = :playlistId LIMIT 1")
    suspend fun getByEpgIdAndPlaylist(epgId: String, playlistId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE epgResolutionStatus IN ('UNRESOLVED', 'UNRESOLVABLE') ORDER BY name")
    suspend fun unresolvedForManual(): List<ChannelEntity>

    @Query("SELECT DISTINCT epgId FROM channels WHERE epgId IS NOT NULL AND epgId != ''")
    suspend fun allDistinctEpgIds(): List<String>
}
