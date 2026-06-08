package com.neuropulse.tv.domain.repository

import com.neuropulse.tv.domain.model.AppSettings
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.Recommendation
import com.neuropulse.tv.domain.model.SeriesSeason
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.StreamHealth
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.model.WatchHistory
import com.neuropulse.tv.domain.model.XtreamAccountInfo
import kotlinx.coroutines.flow.Flow
import android.content.ContentResolver
import android.net.Uri
import java.io.File

interface IptvRepository {
    fun playlists(): Flow<List<Playlist>>
    fun groups(): Flow<List<String>>
    fun channels(group: String?, search: String, favoritesOnly: Boolean): Flow<List<Channel>>
    fun programs(epgIds: List<String>, fromTime: Long): Flow<List<Program>>
    fun searchPrograms(query: String): Flow<List<Program>>
    fun recordings(): Flow<List<String>>
    fun recommendedChannels(limit: Int = 10): Flow<List<Recommendation>>
    fun continueWatching(limit: Int = 5): Flow<List<Channel>>
    fun topChannels(limit: Int = 8): Flow<List<Channel>>
    fun recentlyAdded(limit: Int = 8): Flow<List<Channel>>
    fun liveSportsNow(): Flow<List<Program>>
    fun moviesStartingSoon(now: Long): Flow<List<Program>>
    suspend fun programsWindow(epgIds: List<String>, start: Long, end: Long): List<Program>

    fun profiles(): Flow<List<UserProfile>>
    suspend fun createProfile(name: String, avatarColor: String, pin: String?, isParental: Boolean): Long
    suspend fun setActiveProfile(profileId: Long)
    suspend fun verifyProfilePin(profileId: Long, pin: String): Boolean
    suspend fun purgeDefaultProfiles()
    suspend fun activeProfileId(): Long

    fun healthBest(limit: Int = 10): Flow<List<StreamHealth>>
    fun healthWorst(limit: Int = 10): Flow<List<StreamHealth>>
    suspend fun reportStreamSession(channelId: Long, loadMs: Long, bufferEvents: Int, success: Boolean)

    suspend fun addPlaylistFromUrl(name: String, url: String, epgUrl: String?, refreshHours: Int)
    suspend fun addXtreamPlaylist(name: String, serverUrl: String, username: String, password: String, epgUrl: String?, refreshHours: Int)
    suspend fun addPlaylistFromLocal(name: String, content: String, epgUrl: String?, refreshHours: Int)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun refreshEpgNow()
    suspend fun refreshXtreamEpg(streamId: Long): List<Pair<Long, Long>>

    fun xtreamAccounts(): Flow<List<XtreamAccountInfo>>
    fun vodStreams(): Flow<List<VodItem>>
    fun seriesShows(): Flow<List<SeriesShow>>
    suspend fun seriesSeasons(seriesId: Long): List<SeriesSeason>

    suspend fun toggleFavorite(channelId: Long, enabled: Boolean)
    fun isFavorite(channelId: Long): Flow<Boolean>

    suspend fun saveWatchPosition(channelId: Long, position: Long)
    suspend fun watchHistory(channelId: Long): WatchHistory?

    suspend fun channelById(channelId: Long): Channel?
    suspend fun channelByNumber(number: Int): Channel?

    suspend fun loadSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)

    fun buildCatchupUrl(program: Program, channel: Channel): String?

    suspend fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File): String
}
