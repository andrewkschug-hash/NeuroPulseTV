package com.grid.tv.domain.repository

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.domain.model.ConnectionFormFields
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.EpgRefreshReport
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistConnectResult
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.Recommendation
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.StreamHealth
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import kotlinx.coroutines.flow.Flow
import android.content.ContentResolver
import android.net.Uri
import androidx.paging.PagingData
import java.io.File

interface IptvRepository {
    fun playlists(): Flow<List<Playlist>>
    suspend fun hasActiveConnection(): Boolean
    fun groups(): Flow<List<String>>
    fun groupChannelCounts(): Flow<Map<String, Int>>
    fun channels(group: String?, search: String, favoritesOnly: Boolean, favoriteGroupId: Long? = null): Flow<List<Channel>>
    suspend fun channelsPage(
        groups: Set<String> = emptySet(),
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long? = null,
        limit: Int,
        offset: Int
    ): List<Channel>
    fun hasChannels(): Flow<Boolean>
    suspend fun searchChannels(query: String, limit: Int = 50): List<Channel>
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
    suspend fun programsWindowForChannels(channels: List<Channel>, start: Long, end: Long): List<Program>
    suspend fun allDistinctEpgIds(): List<String>

    fun profiles(): Flow<List<UserProfile>>
    suspend fun createProfile(name: String, avatarColor: String, pin: String?, isParental: Boolean): Long
    suspend fun updateProfileName(profileId: Long, name: String)
    suspend fun updateProfileAvatarColor(profileId: Long, avatarColor: String)
    suspend fun updateProfilePin(profileId: Long, pin: String?)
    suspend fun deleteProfile(profileId: Long)
    suspend fun setActiveProfile(profileId: Long)
    suspend fun verifyProfilePin(profileId: Long, pin: String): Boolean
    suspend fun purgeDefaultProfiles()
    suspend fun enterGuestSession()
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
    suspend fun refreshEpgNow(): EpgRefreshReport
    suspend fun refreshXtreamEpg(streamId: Long): List<Pair<Long, Long>>
    fun epgDataRevision(): Flow<Long>
    suspend fun notifyEpgLinksUpdated()

    fun xtreamAccounts(): Flow<List<XtreamAccountInfo>>
    fun vodCatalogRevision(): Flow<Long>
    fun vodStreamCount(): Flow<Int>
    fun seriesShowCount(): Flow<Int>
    fun vodCategories(): Flow<List<com.grid.tv.domain.model.VodCategory>>
    fun vodCatalogLoading(): Flow<Boolean>
    fun vodCatalogProgress(): Flow<com.grid.tv.domain.model.VodCatalogProgress>
    fun vodCatalogStatus(): Flow<com.grid.tv.domain.model.VodCatalogStatus>
    suspend fun vodPage(
        categoryId: String? = null,
        search: String = "",
        limit: Int,
        offset: Int
    ): List<VodItem>
    fun vodMoviesPaging(categoryId: String? = null, search: String = ""): Flow<PagingData<VodItem>>
    suspend fun vodFilteredCount(categoryId: String? = null, search: String = ""): Int
    suspend fun findVodStream(playlistId: Long, streamId: Long): VodItem?
    suspend fun vodRecent(limit: Int): List<VodItem>
    suspend fun vodSampleForRecommendations(sampleSize: Int = 500): List<VodItem>
    suspend fun loadMovieBrowseRows(itemsPerRow: Int = 20, maxRows: Int = 16): List<VodBrowseRow>
    suspend fun seriesPage(
        category: String = "All",
        search: String = "",
        limit: Int,
        offset: Int
    ): List<SeriesShow>
    suspend fun seriesFilteredCount(category: String = "All", search: String = ""): Int
    suspend fun findSeriesShow(seriesId: Long): SeriesShow?
    suspend fun loadSeriesBrowseRows(itemsPerRow: Int = 20, maxRows: Int = 16): List<VodBrowseRow>
    suspend fun searchVod(query: String, limit: Int = 40): List<VodItem>
    suspend fun searchSeriesShows(query: String, limit: Int = 40): List<SeriesShow>
    suspend fun distinctSeriesCategories(): List<String>
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
    suspend fun saveGuideChannelFilter(groups: Set<String>, configured: Boolean)
    suspend fun lastFullScanAt(): Long?
    suspend fun updateLastFullScanAt(timestamp: Long)
    suspend fun preferredSearchInput(): SearchInputMode
    suspend fun setPreferredSearchInput(mode: SearchInputMode)

    fun buildCatchupUrl(program: Program, channel: Channel): String?

    suspend fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File): String

    suspend fun ensureVodCatalogLoaded(trigger: VodRefreshTrigger)

    /** Loads VOD rows from local DB and warms the first channel page — no network. */
    suspend fun warmLocalUiCache()

    suspend fun refreshVodSeriesCatalog(
        trigger: VodRefreshTrigger = VodRefreshTrigger.UNKNOWN,
        force: Boolean = false
    )

    suspend fun shouldShowWhatsNew(currentVersion: String): Boolean
    suspend fun markVersionSeen(currentVersion: String)
    suspend fun exportBackup(file: File): String
    suspend fun clearAppCache()
    suspend fun resetSettingsToDefaults()
    suspend fun resetApp()
}
