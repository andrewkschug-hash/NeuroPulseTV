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
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.StreamHealth
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.feature.startup.CachedCatalogCounts
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
    /** Single Room emission — groups + counts derived together (avoids duplicate GROUP BY work). */
    fun observeGroupMetadata(): Flow<com.grid.tv.feature.guide.GuideGroupMetadata>

    /** Favourite live channel groups for the active playlist (not profile channel groups). */
    fun observeFavoriteChannelGroups(playlistId: Long): Flow<List<String>>
    fun observeIsFavoriteChannelGroup(playlistId: Long, groupKey: String): Flow<Boolean>
    suspend fun toggleFavoriteChannelGroup(playlistId: Long, groupKey: String)
    suspend fun getFavoriteChannelGroups(playlistId: Long): List<String>
    fun channels(group: String?, search: String, favoritesOnly: Boolean, favoriteGroupId: Long? = null): Flow<List<Channel>>
    suspend fun channelsPage(
        groups: Set<String> = emptySet(),
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long? = null,
        limit: Int,
        offset: Int
    ): List<Channel>
    fun channelsPaging(
        group: String? = null,
        search: String = "",
        favoritesOnly: Boolean = false,
        favoriteGroupId: Long? = null,
        sportsEpgIds: Set<String>? = null
    ): Flow<PagingData<Channel>>
    suspend fun channelsFilteredCount(
        group: String? = null,
        search: String = "",
        favoritesOnly: Boolean = false,
        favoriteGroupId: Long? = null,
        sportsEpgIds: Set<String>? = null
    ): Int
    fun hasChannels(): Flow<Boolean>
    suspend fun searchChannels(query: String, limit: Int = 50): List<Channel>
    fun programs(playlistId: Long, epgIds: List<String>, fromTime: Long): Flow<List<Program>>
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
    fun observeProgramsWindowForChannels(channels: List<Channel>, windowStart: Long, windowEnd: Long): Flow<List<Program>>
    /** Fetches a short EPG window from the provider for visible channels and upserts into the DB. */
    suspend fun fetchCurrentEpgForChannels(channelIds: List<String>): Int
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
    /** Purges seeded Default rows and reconciles the active profile before the picker is shown. */
    suspend fun prepareProfilePicker()
    suspend fun enterGuestSession()
    fun isGuestSession(): Boolean
    suspend fun activeProfileId(): Long
    suspend fun activeProfile(): UserProfile?

    fun activePlaylistId(): Flow<Long>
    suspend fun setActivePlaylist(playlistId: Long)

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
    fun seriesCategories(): Flow<List<com.grid.tv.domain.model.VodCategory>>
    fun vodCatalogLoading(): Flow<Boolean>
    fun vodCatalogProgress(): Flow<com.grid.tv.domain.model.VodCatalogProgress>
    fun vodCatalogStatus(): Flow<com.grid.tv.domain.model.VodCatalogStatus>
    suspend fun vodPage(
        categoryId: String? = null,
        search: String = "",
        limit: Int,
        offset: Int
    ): List<VodItem>
    fun vodMoviesPaging(
        categoryIds: Set<String>? = null,
        search: String = "",
        playlistId: Long? = null
    ): Flow<PagingData<VodItem>>
    fun seriesShowsPaging(
        categoryIds: Set<String>? = null,
        search: String = "",
        playlistId: Long? = null
    ): Flow<PagingData<SeriesShow>>
    suspend fun vodFilteredCount(
        categoryIds: Set<String>? = null,
        search: String = "",
        playlistId: Long? = null
    ): Int
    suspend fun findVodStream(playlistId: Long, streamId: Long): VodItem?
    suspend fun vodRecent(playlistId: Long, limit: Int): List<VodItem>
    suspend fun vodSampleForRecommendations(sampleSize: Int = 500): List<VodItem>
    suspend fun seriesRecentSample(limit: Int = 500): List<SeriesShow>
    suspend fun discoverVodContentLanguages(maxTitlesPerSource: Int = 8000): List<String>
    suspend fun loadMovieBrowseRows(itemsPerRow: Int = 20, maxRows: Int = 16, playlistId: Long? = null): List<VodBrowseRow>
    suspend fun seriesPage(
        category: String = "All",
        search: String = "",
        limit: Int,
        offset: Int
    ): List<SeriesShow>
    suspend fun seriesFilteredCount(
        categoryIds: Set<String>? = null,
        search: String = "",
        playlistId: Long? = null
    ): Int
    suspend fun seriesShowsBatch(
        categoryIds: Set<String>? = null,
        search: String = "",
        playlistId: Long? = null,
        limit: Int,
        offset: Int
    ): List<SeriesShow>
    suspend fun findSeriesShow(playlistId: Long, seriesId: Long): SeriesShow?
    suspend fun loadSeriesBrowseRows(itemsPerRow: Int = 20, maxRows: Int = 16): List<VodBrowseRow>
    suspend fun searchVod(query: String, limit: Int = 40): List<VodItem>
    suspend fun searchSeriesShows(query: String, limit: Int = 40): List<SeriesShow>
    suspend fun distinctSeriesCategories(): List<String>
    suspend fun saveVodWatchPosition(
        streamId: Long,
        positionMs: Long,
        title: String,
        durationMs: Long,
        playlistId: Long = 0L
    )
    fun vodWatchProgress(): Flow<Map<Pair<Long, Long>, Long>>
    suspend fun seriesSeasons(playlistId: Long, seriesId: Long): List<SeriesSeason>
    suspend fun loadSeriesDetail(playlistId: Long, seriesId: Long): SeriesDetail

    suspend fun toggleFavorite(channelId: Long, enabled: Boolean)
    fun isFavorite(channelId: Long): Flow<Boolean>
    fun favoriteGroups(): Flow<List<FavoriteGroup>>
    suspend fun createFavoriteGroup(name: String): Long
    suspend fun addChannelToFavoriteGroup(channelId: Long, groupId: Long)
    suspend fun removeChannelFromFavorites(channelId: Long)

    suspend fun saveWatchPosition(channelId: Long, position: Long, programTitle: String? = null)
    suspend fun watchHistory(channelId: Long): WatchHistory?

    suspend fun channelById(channelId: Long): Channel?
    suspend fun channelByNumber(playlistId: Long, number: Int): Channel?

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

    /** Phase 1 startup — profile, settings, persisted counts only (no SQLite COUNT). */
    suspend fun warmLocalUiCacheMinimal()

    /** Phase 2A — re-publish cached counts (SharedPreferences / memory only). */
    fun applyCachedCatalogCountsAtStartup()

    /** Read cached catalog sizes without SQLite COUNT. */
    fun getCachedCatalogCounts(): CachedCatalogCounts

    /** Phase 2B — chunked background COUNT queries + channel page warm (after interactive window). */
    suspend fun updateCountsInBackground()

    /** Phase 3 startup — network VOD sync scheduling only (returns immediately). */
    fun startDeferredVodMaintenance(trigger: VodRefreshTrigger)

    /** Loads VOD rows from local DB and warms the first channel page — no network. */
    suspend fun warmLocalUiCache()

    /** Network VOD sync, category repair, and full indexing — never blocks the caller. */
    fun scheduleDeferredVodCatalogRefresh(trigger: VodRefreshTrigger)

    /** Starts background VOD/series streaming ingest — returns immediately; UI reads Room via paging. */
    fun loadVodStreamed(trigger: VodRefreshTrigger = VodRefreshTrigger.VOD_HUB_MOUNT)

    /** Alias for [loadVodStreamed] — zero-load continuous sync entry point. */
    fun startContinuousVodSync(trigger: VodRefreshTrigger = VodRefreshTrigger.VOD_HUB_MOUNT) =
        loadVodStreamed(trigger)

    suspend fun refreshVodSeriesCatalog(
        trigger: VodRefreshTrigger = VodRefreshTrigger.UNKNOWN,
        force: Boolean = false
    )

    /**
     * When the Series tab opens: ensure series categories exist and run a series ingest only
     * while hydration state is [com.grid.tv.domain.model.SeriesCatalogHydrationState.NEVER_FETCHED].
     * [VodRefreshTrigger.MANUAL_RETRY] bypasses the gate.
     */
    fun ensureSeriesCatalogForTab(trigger: VodRefreshTrigger = VodRefreshTrigger.VOD_HUB_MOUNT)

    suspend fun shouldShowWhatsNew(currentVersion: String): Boolean
    suspend fun markVersionSeen(currentVersion: String)
    suspend fun exportBackup(file: File): String
    suspend fun clearAppCache()
    suspend fun resetSettingsToDefaults()
    suspend fun resetApp()
}
