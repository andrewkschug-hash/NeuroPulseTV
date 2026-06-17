package com.grid.tv.domain.repository

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.domain.model.ConnectionFormFields
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistConnectResult
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.Recommendation
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.StreamHealth
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import kotlinx.coroutines.flow.Flow
import android.content.ContentResolver
import android.net.Uri
import java.io.File

interface IptvRepository {
    fun playlists(): Flow<List<Playlist>>
    suspend fun hasActiveConnection(): Boolean
    fun groups(): Flow<List<String>>
    fun channels(group: String?, search: String, favoritesOnly: Boolean, favoriteGroupId: Long? = null): Flow<List<Channel>>
    fun programs(epgIds: List<String>, fromTime: Long): Flow<List<Program>>
    fun searchPrograms(query: String): Flow<List<Program>>
    fun recordings(): Flow<List<String>>
    fun recommendedChannels(limit: Int = 10): Flow<List<Recommendation>>
    fun continueWatching(limit: Int = 5): Flow<List<Channel>>
    fun continueWatchingItems(limit: Int = 5): Flow<List<ContinueWatchingItem>>
    fun topChannels(limit: Int = 8): Flow<List<Channel>>
    fun recentChannels(limit: Int = 20): Flow<List<Channel>>
    fun recentlyAdded(limit: Int = 8): Flow<List<Channel>>
    fun liveSportsNow(): Flow<List<Program>>
    fun moviesStartingSoon(now: Long): Flow<List<Program>>
    suspend fun programsWindow(epgIds: List<String>, start: Long, end: Long): List<Program>

    fun profiles(): Flow<List<UserProfile>>
    suspend fun createProfile(name: String, avatarColor: String, pin: String?, isParental: Boolean): Long
    suspend fun updateProfileName(profileId: Long, name: String)
    suspend fun updateProfileAvatarColor(profileId: Long, avatarColor: String)
    suspend fun updateProfilePin(profileId: Long, pin: String?)
    suspend fun deleteProfile(profileId: Long)
    suspend fun setActiveProfile(profileId: Long)
    suspend fun verifyProfilePin(profileId: Long, pin: String): Boolean
    suspend fun purgeDefaultProfiles()
    suspend fun activeProfileId(): Long
    suspend fun activeProfile(): UserProfile?

    fun healthBest(limit: Int = 10): Flow<List<StreamHealth>>
    fun healthWorst(limit: Int = 10): Flow<List<StreamHealth>>
    suspend fun reportStreamSession(channelId: Long, loadMs: Long, bufferEvents: Int, success: Boolean)

    suspend fun addPlaylistFromUrl(name: String, url: String, epgUrl: String?, refreshHours: Int)
    suspend fun addXtreamPlaylist(name: String, serverUrl: String, username: String, password: String, epgUrl: String?, refreshHours: Int)
    suspend fun updateM3uPlaylist(playlistId: Long, name: String, url: String, epgUrl: String?, refreshHours: Int)
    suspend fun updateXtreamPlaylist(
        playlistId: Long,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        epgUrl: String?,
        refreshHours: Int
    )
    suspend fun connectM3uPlaylist(name: String, url: String): PlaylistConnectResult
    suspend fun connectXtreamPlaylist(name: String, serverUrl: String, username: String, password: String): PlaylistConnectResult
    suspend fun connectStalkerPlaylist(name: String, portalUrl: String, macAddress: String): PlaylistConnectResult
    suspend fun addPlaylistFromLocal(name: String, content: String, epgUrl: String?, refreshHours: Int)
    suspend fun connectionFormForPlaylist(playlist: Playlist): ConnectionFormFields
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun refreshEpgNow()
    suspend fun refreshXtreamEpg(streamId: Long): List<Pair<Long, Long>>

    fun xtreamAccounts(): Flow<List<XtreamAccountInfo>>
    fun vodStreams(): Flow<List<VodItem>>
    fun vodCategories(): Flow<List<com.grid.tv.domain.model.VodCategory>>
    fun vodCatalogLoading(): Flow<Boolean>
    fun seriesShows(): Flow<List<SeriesShow>>
    suspend fun saveVodWatchPosition(streamId: Long, positionMs: Long, title: String, durationMs: Long)
    fun vodWatchProgress(): Flow<Map<Long, Long>>
    suspend fun seriesSeasons(seriesId: Long): List<SeriesSeason>

    suspend fun toggleFavorite(channelId: Long, enabled: Boolean)
    fun isFavorite(channelId: Long): Flow<Boolean>
    fun favoriteGroups(): Flow<List<FavoriteGroup>>
    suspend fun createFavoriteGroup(name: String): Long
    suspend fun addChannelToFavoriteGroup(channelId: Long, groupId: Long)
    suspend fun removeChannelFromFavorites(channelId: Long)

    suspend fun saveWatchPosition(channelId: Long, position: Long, programTitle: String? = null)
    suspend fun watchHistory(channelId: Long): WatchHistory?

    suspend fun channelById(channelId: Long): Channel?
    suspend fun channelByNumber(number: Int): Channel?

    suspend fun loadSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)
    suspend fun lastFullScanAt(): Long?
    suspend fun updateLastFullScanAt(timestamp: Long)
    suspend fun preferredSearchInput(): SearchInputMode
    suspend fun setPreferredSearchInput(mode: SearchInputMode)

    fun buildCatchupUrl(program: Program, channel: Channel): String?

    suspend fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File): String

    suspend fun refreshVodSeriesCatalog()

    suspend fun shouldShowWhatsNew(currentVersion: String): Boolean
    suspend fun markVersionSeen(currentVersion: String)
    suspend fun exportBackup(file: File): String
    suspend fun clearAppCache()
    suspend fun resetSettingsToDefaults()
    suspend fun resetApp()
}
