package com.neuropulse.tv.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.ChannelScanDao
import com.neuropulse.tv.data.db.dao.FavoriteGroupDao
import com.neuropulse.tv.data.db.dao.PlaylistDao
import com.neuropulse.tv.data.db.dao.ProfileDao
import com.neuropulse.tv.data.db.dao.ProfileFavoriteDao
import com.neuropulse.tv.data.db.dao.ProfileSettingsDao
import com.neuropulse.tv.data.db.dao.ProfileWatchHistoryDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.RecordingDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.dao.StreamHealthDao
import com.neuropulse.tv.data.db.entity.ActiveProfileEntity
import com.neuropulse.tv.data.db.entity.PlaylistEntity
import com.neuropulse.tv.data.db.entity.ProfileFavoriteEntity
import com.neuropulse.tv.data.db.entity.ProfileSettingsEntity
import com.neuropulse.tv.data.db.entity.ProfileWatchHistoryEntity
import com.neuropulse.tv.data.db.entity.StreamHealthEntity
import com.neuropulse.tv.data.db.entity.UserProfileEntity
import com.neuropulse.tv.data.network.RemoteTextFetcher
import com.neuropulse.tv.data.network.parser.M3uParser
import com.neuropulse.tv.data.network.parser.XtreamParser
import com.neuropulse.tv.data.network.parser.XmlTvParser
import com.neuropulse.tv.data.network.stalker.StalkerPortalClient
import com.neuropulse.tv.data.security.SecureCredentialStore
import com.neuropulse.tv.domain.model.PlaylistConnectResult
import com.neuropulse.tv.domain.model.AppSettings
import com.neuropulse.tv.domain.model.SearchInputMode
import com.neuropulse.tv.util.MAX_HOUSEHOLD_PROFILES
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.FavoriteGroup
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.ProgramGenre
import com.neuropulse.tv.domain.model.Recommendation
import com.neuropulse.tv.domain.model.SeriesSeason
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.StreamHealth
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.model.WatchHistory
import com.neuropulse.tv.domain.model.XtreamAccountInfo
import com.neuropulse.tv.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neuropulse.tv.feature.epg.EpgBlockCache
import com.neuropulse.tv.data.db.entity.FavoriteGroupEntity
import com.neuropulse.tv.feature.backup.GridBackupManager
import com.neuropulse.tv.feature.health.StreamHealthEngine
import com.neuropulse.tv.feature.recommendation.RecommendationEngine
import com.neuropulse.tv.feature.recommendation.WatchStat
import com.neuropulse.tv.feature.tivimate.TiviMateImporter
import com.neuropulse.tv.worker.EpgScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val profileDao: ProfileDao,
    private val profileFavoriteDao: ProfileFavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val profileWatchHistoryDao: ProfileWatchHistoryDao,
    private val profileSettingsDao: ProfileSettingsDao,
    private val programDao: ProgramDao,
    private val recordingDao: RecordingDao,
    private val scheduledRecordingDao: ScheduledRecordingDao,
    private val recordedMediaDao: RecordedMediaDao,
    private val streamHealthDao: StreamHealthDao,
    private val channelScanDao: ChannelScanDao,
    private val remoteTextFetcher: RemoteTextFetcher,
    private val m3uParser: M3uParser,
    private val xtreamParser: XtreamParser,
    private val xmlTvParser: XmlTvParser,
    private val tiviMateImporter: TiviMateImporter,
    private val epgScheduler: EpgScheduler,
    private val secureCredentialStore: SecureCredentialStore,
    private val stalkerPortalClient: StalkerPortalClient,
    private val gridBackupManager: GridBackupManager
) : IptvRepository {

    companion object {
        private const val CONNECT_ERROR = "Login or URL invalid"
    }

    private val recommendationEngine = RecommendationEngine()
    private val healthEngine = StreamHealthEngine()
    private val epgCache = EpgBlockCache(maxBlocks = 6)

    private var cachedSettings = AppSettings()
    private var activeProfileId = 1L
    private val vodCache = MutableStateFlow<List<VodItem>>(emptyList())
    private val seriesCache = MutableStateFlow<List<SeriesShow>>(emptyList())
    private val seriesSeasonsCache = linkedMapOf<Long, List<SeriesSeason>>()

    private fun encode(v: String): String = URLEncoder.encode(v, Charsets.UTF_8.name())

    private fun normalizeServerUrl(raw: String): String {
        val t = raw.trim().removeSuffix("/")
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    private fun buildXtreamApiUrl(serverUrl: String, username: String, password: String, action: String? = null, extra: String? = null): String {
        val base = "${normalizeServerUrl(serverUrl)}/player_api.php?username=${encode(username)}&password=${encode(password)}"
        val withAction = if (action != null) "$base&action=$action" else base
        return if (!extra.isNullOrBlank()) "$withAction&$extra" else withAction
    }

    private suspend fun ensureDefaultProfile() {
        profileDao.deleteDefaultProfiles()
        val activeId = profileDao.activeProfile()?.profileId
        val activeEntity = activeId?.let { profileDao.getProfile(it) }
        if (activeEntity != null && activeEntity.name != "Default") {
            activeProfileId = activeEntity.id
        } else {
            val first = profileDao.firstUserProfile()
            if (first != null) {
                activeProfileId = first.id
                profileDao.setActive(ActiveProfileEntity(profileId = first.id))
            }
        }
    }

    private fun channelFromEntity(entity: com.neuropulse.tv.data.db.entity.ChannelEntity, playlistName: String? = null): Channel {
        return Channel(
            id = entity.id,
            number = entity.number,
            name = entity.name,
            group = entity.groupName,
            logoUrl = entity.logoUrl,
            epgId = entity.epgId,
            streamUrl = entity.streamUrl,
            backupStreamUrl = entity.backupStreamUrl,
            playlistId = entity.playlistId,
            playlistName = playlistName,
            isFavorite = false,
            reliabilityScore = 50,
            catchupDays = entity.catchupDays,
            catchupSource = entity.catchupSource,
            epgResolutionStatus = runCatching { EpgResolutionStatus.valueOf(entity.epgResolutionStatus) }.getOrDefault(EpgResolutionStatus.UNRESOLVED),
            epgResolutionConfidence = entity.epgResolutionConfidence,
            epgResolutionSource = entity.epgResolutionSource
        )
    }

    override fun playlists(): Flow<List<Playlist>> = playlistDao.observeAll().map { rows ->
        rows.map {
            Playlist(
                id = it.id,
                name = it.name,
                url = it.url,
                lastRefreshed = it.lastRefreshed,
                refreshIntervalHours = it.refreshIntervalHours,
                epgUrl = it.epgUrl,
                type = runCatching { PlaylistType.valueOf(it.type) }.getOrDefault(PlaylistType.M3U),
                xtreamServerUrl = it.xtreamServerUrl,
                xtreamUsername = it.xtreamUsername,
                xtreamAccountStatus = it.xtreamAccountStatus,
                xtreamExpiryDateEpochSec = it.xtreamExpiryDateEpochSec,
                xtreamMaxConnections = it.xtreamMaxConnections,
                stalkerPortalUrl = it.stalkerPortalUrl,
                stalkerMacAddress = it.stalkerMacAddress
            )
        }
    }

    private fun resolveXtreamPassword(playlist: PlaylistEntity): String? =
        secureCredentialStore.getXtreamPassword(playlist.id) ?: playlist.xtreamPassword

    override fun groups(): Flow<List<String>> = channelDao.observeGroups()

    override fun channels(
        group: String?,
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?
    ): Flow<List<Channel>> {
        val groupFilter = favoriteGroupId ?: -1L
        return combine(
            channelDao.observeChannels(group, search, favoritesOnly, activeProfileId, groupFilter),
            streamHealthDao.observeAll(),
            profileFavoriteDao.observeForProfile(activeProfileId)
        ) { rows, healthRows, favs ->
            val health = healthRows.associateBy { it.channelId }
            val favIds = favs.map { it.channelId }.toSet()
            rows.map { entity ->
                channelFromEntity(entity).copy(
                    isFavorite = entity.id in favIds,
                    reliabilityScore = health[entity.id]?.reliabilityScore ?: 50
                )
            }
        }
    }

    override fun programs(epgIds: List<String>, fromTime: Long): Flow<List<Program>> =
        programDao.observeGrid(epgIds, fromTime).map { rows ->
            rows.map {
                Program(
                    id = it.id,
                    channelEpgId = it.channelEpgId,
                    title = it.title,
                    description = it.description,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    genre = runCatching { ProgramGenre.valueOf(it.genre) }.getOrDefault(ProgramGenre.GENERAL),
                    catchupUrl = it.catchupUrl
                )
            }
        }

    override fun searchPrograms(query: String): Flow<List<Program>> =
        programDao.observeSearch(query).map { rows ->
            rows.map {
                Program(
                    id = it.id,
                    channelEpgId = it.channelEpgId,
                    title = it.title,
                    description = it.description,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    genre = runCatching { ProgramGenre.valueOf(it.genre) }.getOrDefault(ProgramGenre.GENERAL),
                    catchupUrl = it.catchupUrl
                )
            }
        }

    override fun recordings(): Flow<List<String>> = recordingDao.observeAll().map { rows ->
        rows.map { "${it.programTitle} (${it.status})" }
    }

    override fun recommendedChannels(limit: Int): Flow<List<Recommendation>> {
        return combine(channels(null, "", false), profileWatchHistoryDao.observeTop(activeProfileId, 500)) { allChannels, hist ->
            val stats = hist.groupBy { it.channelId }.mapValues { (_, items) ->
                WatchStat(items.size, items.map { it.hourBucket }.average().toInt(), items.firstOrNull()?.genreHint)
            }
            recommendationEngine.score(allChannels, stats, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), null).take(limit)
        }
    }

    override fun continueWatching(limit: Int): Flow<List<Channel>> =
        continueWatchingItems(limit).map { items -> items.map { it.channel } }

    override fun continueWatchingItems(limit: Int): Flow<List<ContinueWatchingItem>> =
        combine(
            profileWatchHistoryDao.observeRecent(activeProfileId, limit),
            streamHealthDao.observeAll(),
            profileFavoriteDao.observeForProfile(activeProfileId)
        ) { rows, healthRows, favs ->
            val all = channelDao.all().associateBy { it.id }
            val health = healthRows.associateBy { it.channelId }
            val favIds = favs.map { it.channelId }.toSet()
            rows.mapNotNull { hist ->
                val entity = all[hist.channelId] ?: return@mapNotNull null
                ContinueWatchingItem(
                    channel = channelFromEntity(entity).copy(
                        isFavorite = entity.id in favIds,
                        reliabilityScore = health[entity.id]?.reliabilityScore ?: 50
                    ),
                    lastPosition = hist.lastPosition,
                    lastWatched = hist.lastWatched,
                    programTitle = hist.lastProgramTitle
                )
            }
        }

    override fun topChannels(limit: Int): Flow<List<Channel>> =
        profileWatchHistoryDao.observeTop(activeProfileId, limit).map { rows ->
            val all = channelDao.all().associateBy { it.id }
            rows.mapNotNull { all[it.channelId] }.map { channelFromEntity(it) }
        }

    override fun recentlyAdded(limit: Int): Flow<List<Channel>> =
        channelDao.observeRecentlyAdded(limit).map { rows -> rows.map { channelFromEntity(it) } }

    override fun liveSportsNow(): Flow<List<Program>> =
        programDao.observeSports(System.currentTimeMillis()).map { rows ->
            rows.map {
                Program(it.id, it.channelEpgId, it.title, it.description, it.startTime, it.endTime, ProgramGenre.SPORTS, it.catchupUrl)
            }
        }

    override fun moviesStartingSoon(now: Long): Flow<List<Program>> =
        programDao.observeSearch("").map { rows ->
            rows.filter { it.genre == "MOVIES" && it.startTime in now..(now + 30 * 60 * 1000) }
                .map { Program(it.id, it.channelEpgId, it.title, it.description, it.startTime, it.endTime, ProgramGenre.MOVIES, it.catchupUrl) }
        }

    override suspend fun programsWindow(epgIds: List<String>, start: Long, end: Long): List<Program> =
        withContext(Dispatchers.IO) {
            val key = "${start / 1000}-${end / 1000}"
            epgCache.get(key)?.let { return@withContext it }
            val loaded = programDao.loadWindow(epgIds, start, end).map {
                Program(
                    id = it.id,
                    channelEpgId = it.channelEpgId,
                    title = it.title,
                    description = it.description,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    genre = runCatching { ProgramGenre.valueOf(it.genre) }.getOrDefault(ProgramGenre.GENERAL),
                    catchupUrl = it.catchupUrl
                )
            }
            epgCache.put(key, loaded)
            loaded
        }

    override fun profiles(): Flow<List<UserProfile>> = profileDao.observeProfiles().map { rows ->
        rows
            .filter { it.name != "Default" }
            .map {
                UserProfile(
                    it.id,
                    it.name,
                    it.avatarColor,
                    !it.pin.isNullOrBlank(),
                    it.isParental,
                    it.allowedStartMinutes,
                    it.allowedEndMinutes
                )
            }
    }

    override suspend fun activeProfile(): UserProfile? =
        profileDao.getProfile(activeProfileId)?.let {
            UserProfile(
                it.id,
                it.name,
                it.avatarColor,
                !it.pin.isNullOrBlank(),
                it.isParental,
                it.allowedStartMinutes,
                it.allowedEndMinutes
            )
        }

    override suspend fun createProfile(name: String, avatarColor: String, pin: String?, isParental: Boolean): Long {
        profileDao.deleteDefaultProfiles()
        if (profileDao.countUserProfiles() >= MAX_HOUSEHOLD_PROFILES) return -1
        val id = profileDao.upsertProfile(
            UserProfileEntity(
                name = name,
                avatarColor = avatarColor,
                pin = pin,
                isParental = isParental,
                allowedStartMinutes = if (isParental) 7 * 60 else 0,
                allowedEndMinutes = if (isParental) 21 * 60 else 1439
            )
        )
        profileSettingsDao.upsert(ProfileSettingsEntity(profileId = id))
        if (profileDao.countUserProfiles() == 1) {
            setActiveProfile(id)
        }
        return id
    }

    override suspend fun updateProfileName(profileId: Long, name: String) {
        val profile = profileDao.getProfile(profileId) ?: return
        profileDao.upsertProfile(profile.copy(name = name.trim()))
    }

    override suspend fun updateProfileAvatarColor(profileId: Long, avatarColor: String) {
        val profile = profileDao.getProfile(profileId) ?: return
        profileDao.upsertProfile(profile.copy(avatarColor = avatarColor))
    }

    override suspend fun deleteProfile(profileId: Long) {
        profileFavoriteDao.deleteByProfile(profileId)
        profileWatchHistoryDao.deleteByProfile(profileId)
        favoriteGroupDao.deleteByProfile(profileId)
        profileSettingsDao.deleteByProfile(profileId)
        profileDao.deleteProfile(profileId)
        if (activeProfileId == profileId) {
            profileDao.firstUserProfile()?.id?.let { setActiveProfile(it) }
        }
    }

    override suspend fun setActiveProfile(profileId: Long) {
        profileDao.setActive(ActiveProfileEntity(profileId = profileId))
        activeProfileId = profileId
    }

    override suspend fun verifyProfilePin(profileId: Long, pin: String): Boolean {
        val profile = profileDao.getProfile(profileId) ?: return false
        return profile.pin == pin
    }

    override suspend fun purgeDefaultProfiles() {
        profileDao.deleteDefaultProfiles()
    }

    override suspend fun activeProfileId(): Long {
        ensureDefaultProfile()
        return activeProfileId
    }

    override fun healthBest(limit: Int): Flow<List<StreamHealth>> = streamHealthDao.best(limit).map { rows ->
        rows.map { StreamHealth(it.channelId, it.reliabilityScore, it.averageLoadTimeMs, it.bufferEventsPerSession, it.lastSuccessfulLoad) }
    }

    override fun healthWorst(limit: Int): Flow<List<StreamHealth>> = streamHealthDao.worst(limit).map { rows ->
        rows.map { StreamHealth(it.channelId, it.reliabilityScore, it.averageLoadTimeMs, it.bufferEventsPerSession, it.lastSuccessfulLoad) }
    }

    override suspend fun reportStreamSession(channelId: Long, loadMs: Long, bufferEvents: Int, success: Boolean) {
        val previous = streamHealthDao.get(channelId)?.let {
            StreamHealth(it.channelId, it.reliabilityScore, it.averageLoadTimeMs, it.bufferEventsPerSession, it.lastSuccessfulLoad)
        }
        val scored = healthEngine.compute(previous, loadMs, bufferEvents, success).copy(channelId = channelId)
        streamHealthDao.upsert(
            StreamHealthEntity(
                channelId = channelId,
                lastSuccessfulLoad = scored.lastSuccessfulLoad,
                bufferEventsPerSession = scored.bufferEventsPerSession,
                averageLoadTimeMs = scored.averageLoadTimeMs,
                reliabilityScore = scored.reliabilityScore,
                sessions = (streamHealthDao.get(channelId)?.sessions ?: 0) + 1
            )
        )
    }

    override suspend fun addPlaylistFromUrl(name: String, url: String, epgUrl: String?, refreshHours: Int) {
        withContext(Dispatchers.IO) {
            insertM3uPlaylist(name, url, epgUrl, refreshHours)
        }
    }

    override suspend fun addXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        epgUrl: String?,
        refreshHours: Int
    ) {
        withContext(Dispatchers.IO) {
            insertXtreamPlaylist(name, serverUrl, username, password, epgUrl, refreshHours)
        }
    }

    override suspend fun connectM3uPlaylist(name: String, url: String): PlaylistConnectResult =
        withContext(Dispatchers.IO) {
            val displayName = name.ifBlank { "My Playlist" }
            try {
                val trimmedUrl = url.trim()
                if (trimmedUrl.isBlank()) {
                    return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
                }
                val playlistId = insertM3uPlaylist(displayName, trimmedUrl, null, 24)
                val count = channelDao.countByPlaylist(playlistId)
                if (count == 0) {
                    playlistDao.delete(playlistId)
                    secureCredentialStore.removePlaylistCredentials(playlistId)
                    return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
                }
                PlaylistConnectResult(true, displayName, count)
            } catch (_: Exception) {
                PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
        }

    override suspend fun connectXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String
    ): PlaylistConnectResult = withContext(Dispatchers.IO) {
        val displayName = name.ifBlank { "Xtream TV" }
        try {
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
            val authUrl = buildXtreamApiUrl(serverUrl, username, password)
            val auth = xtreamParser.parseAuth(remoteTextFetcher.fetch(authUrl))
            if (auth.status.equals("Expired", ignoreCase = true)) {
                return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
            val playlistId = insertXtreamPlaylist(displayName, serverUrl, username, password, null, 24)
            val count = channelDao.countByPlaylist(playlistId)
            if (count == 0) {
                playlistDao.delete(playlistId)
                secureCredentialStore.removePlaylistCredentials(playlistId)
                return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
            PlaylistConnectResult(true, displayName, count)
        } catch (_: Exception) {
            PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
        }
    }

    override suspend fun connectStalkerPlaylist(
        name: String,
        portalUrl: String,
        macAddress: String
    ): PlaylistConnectResult = withContext(Dispatchers.IO) {
        val displayName = name.ifBlank { "Stalker Portal" }
        try {
            if (portalUrl.isBlank() || macAddress.isBlank()) {
                return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
            val playlistId = insertStalkerPlaylist(displayName, portalUrl, macAddress, 24)
            val count = channelDao.countByPlaylist(playlistId)
            if (count == 0) {
                playlistDao.delete(playlistId)
                secureCredentialStore.removePlaylistCredentials(playlistId)
                return@withContext PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
            }
            PlaylistConnectResult(true, displayName, count)
        } catch (_: Exception) {
            PlaylistConnectResult(false, displayName, errorMessage = CONNECT_ERROR)
        }
    }

    private suspend fun insertM3uPlaylist(name: String, url: String, epgUrl: String?, refreshHours: Int): Long {
        val startedAt = System.currentTimeMillis()
        val normalizedUrl = remoteTextFetcher.normalizeRemoteUrl(url)
        val raw = remoteTextFetcher.fetch(normalizedUrl)
        val parsed = m3uParser.parseAsFlow(playlistId = 0L, raw).first { it.done }
        if (parsed.channels.isEmpty()) {
            throw IllegalStateException("No channels in playlist")
        }
        val playlistId = playlistDao.insert(
            PlaylistEntity(
                name = name,
                url = normalizedUrl,
                epgUrl = epgUrl,
                refreshIntervalHours = refreshHours,
                type = PlaylistType.M3U.name
            )
        )
        channelDao.insertAll(parsed.channels.map { it.copy(playlistId = playlistId) })
        secureCredentialStore.saveM3uUrl(playlistId, normalizedUrl)
        epgScheduler.runResolverForNewChannels(startedAt)
        return playlistId
    }

    private suspend fun insertXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        epgUrl: String?,
        refreshHours: Int
    ): Long {
        val startedAt = System.currentTimeMillis()
        val authUrl = buildXtreamApiUrl(serverUrl, username, password)
        val auth = xtreamParser.parseAuth(remoteTextFetcher.fetch(authUrl))
        val normalizedServer = if (auth.serverUrl.isNotBlank()) auth.serverUrl else normalizeServerUrl(serverUrl)

        val playlistId = playlistDao.insert(
            PlaylistEntity(
                name = name,
                url = normalizedServer,
                epgUrl = epgUrl,
                refreshIntervalHours = refreshHours,
                type = PlaylistType.XTREAM.name,
                xtreamServerUrl = normalizedServer,
                xtreamUsername = username,
                xtreamPassword = null,
                xtreamAccountStatus = auth.status,
                xtreamExpiryDateEpochSec = auth.expiryDateEpochSec,
                xtreamMaxConnections = auth.maxConnections
            )
        )
        secureCredentialStore.saveXtreamPassword(playlistId, password)

        val categoriesRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(normalizedServer, username, password, action = "get_live_categories"))
        val liveRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(normalizedServer, username, password, action = "get_live_streams"))
        val vodRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(normalizedServer, username, password, action = "get_vod_streams"))
        val seriesRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(normalizedServer, username, password, action = "get_series"))

        val categories = xtreamParser.parseLiveCategories(categoriesRaw)
        val liveChannels = xtreamParser.parseLiveChannels(playlistId, liveRaw, username, password, normalizedServer, categories)

        channelDao.clearByPlaylist(playlistId)
        channelDao.insertAll(liveChannels)
        vodCache.value = xtreamParser.parseVod(vodRaw, username, password, normalizedServer)
        seriesCache.value = xtreamParser.parseSeries(seriesRaw)
        seriesSeasonsCache.clear()
        epgScheduler.runResolverForNewChannels(startedAt)
        return playlistId
    }

    private suspend fun insertStalkerPlaylist(
        name: String,
        portalUrl: String,
        macAddress: String,
        refreshHours: Int
    ): Long {
        val startedAt = System.currentTimeMillis()
        val normalizedMac = macAddress.uppercase()
        val session = stalkerPortalClient.connect(portalUrl, normalizedMac)
        val playlistId = playlistDao.insert(
            PlaylistEntity(
                name = name,
                url = session.portalBase,
                refreshIntervalHours = refreshHours,
                type = PlaylistType.STALKER.name,
                stalkerPortalUrl = session.portalBase,
                stalkerMacAddress = normalizedMac
            )
        )
        secureCredentialStore.saveStalkerCredentials(playlistId, session.portalBase, normalizedMac)
        val channels = stalkerPortalClient.fetchChannels(session, playlistId)
        channelDao.clearByPlaylist(playlistId)
        channelDao.insertAll(channels)
        epgScheduler.runResolverForNewChannels(startedAt)
        return playlistId
    }

    override suspend fun addPlaylistFromLocal(name: String, content: String, epgUrl: String?, refreshHours: Int) =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val playlistId = playlistDao.insert(PlaylistEntity(name = name, url = "local://$name", epgUrl = epgUrl, refreshIntervalHours = refreshHours, isLocalFile = true, type = PlaylistType.M3U.name))
            val final = m3uParser.parseAsFlow(playlistId, content).first { it.done }
            channelDao.clearByPlaylist(playlistId)
            channelDao.insertAll(final.channels)
            epgScheduler.runResolverForNewChannels(startedAt)
        }

    override suspend fun deletePlaylist(playlistId: Long) {
        secureCredentialStore.removePlaylistCredentials(playlistId)
        playlistDao.delete(playlistId)
    }

    override suspend fun refreshEpgNow() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val threshold = now - 24L * 60 * 60 * 1000
        programDao.purgeOlderThan(threshold)
        playlistDao.all().forEach { playlist ->
            val epgUrl = playlist.epgUrl ?: return@forEach
            runCatching {
                val xml = remoteTextFetcher.fetch(epgUrl)
                val parsed = xmlTvParser.parse(xml)
                programDao.insertAll(parsed.programs)
                playlistDao.update(playlist.copy(lastRefreshed = now))
            }
        }
    }

    override suspend fun refreshXtreamEpg(streamId: Long): List<Pair<Long, Long>> {
        val playlist = playlistDao.all().firstOrNull { it.type == PlaylistType.XTREAM.name } ?: return emptyList()
        val server = playlist.xtreamServerUrl ?: return emptyList()
        val user = playlist.xtreamUsername ?: return emptyList()
        val pass = resolveXtreamPassword(playlist) ?: return emptyList()
        val raw = remoteTextFetcher.fetch(
            buildXtreamApiUrl(server, user, pass, action = "get_simple_data_table", extra = "stream_id=$streamId")
        )
        return xtreamParser.parseSimpleDataTable(raw)
    }

    override fun xtreamAccounts(): Flow<List<XtreamAccountInfo>> = playlists().map { rows ->
        rows.filter { it.type == PlaylistType.XTREAM }.map {
            XtreamAccountInfo(
                playlistId = it.id,
                playlistName = it.name,
                status = it.xtreamAccountStatus ?: "Unknown",
                expiryDateEpochSec = it.xtreamExpiryDateEpochSec,
                maxConnections = it.xtreamMaxConnections,
                serverUrl = it.xtreamServerUrl ?: it.url
            )
        }
    }

    override suspend fun refreshVodSeriesCatalog() = withContext(Dispatchers.IO) {
        val xtreamPlaylists = playlistDao.all().filter { it.type == PlaylistType.XTREAM.name }
        if (xtreamPlaylists.isEmpty()) return@withContext
        val playlist = xtreamPlaylists.first()
        val server = playlist.xtreamServerUrl ?: return@withContext
        val user = playlist.xtreamUsername ?: return@withContext
        val pass = resolveXtreamPassword(playlist) ?: return@withContext
        val vodRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(server, user, pass, action = "get_vod_streams"))
        val seriesRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(server, user, pass, action = "get_series"))
        vodCache.value = xtreamParser.parseVod(vodRaw, user, pass, server)
        seriesCache.value = xtreamParser.parseSeries(seriesRaw)
        seriesSeasonsCache.clear()
    }

    override fun vodStreams(): Flow<List<VodItem>> = vodCache

    override fun seriesShows(): Flow<List<SeriesShow>> = seriesCache

    override suspend fun seriesSeasons(seriesId: Long): List<SeriesSeason> {
        seriesSeasonsCache[seriesId]?.let { return it }
        val playlist = playlistDao.all().firstOrNull { it.type == PlaylistType.XTREAM.name } ?: return emptyList()
        val server = playlist.xtreamServerUrl ?: return emptyList()
        val user = playlist.xtreamUsername ?: return emptyList()
        val pass = resolveXtreamPassword(playlist) ?: return emptyList()
        val raw = remoteTextFetcher.fetch(
            buildXtreamApiUrl(server, user, pass, action = "get_series_info", extra = "series_id=$seriesId")
        )
        val seasons = xtreamParser.parseSeriesInfo(raw, user, pass, server)
        seriesSeasonsCache[seriesId] = seasons
        return seasons
    }

    override suspend fun toggleFavorite(channelId: Long, enabled: Boolean) {
        ensureDefaultProfile()
        if (enabled) profileFavoriteDao.upsert(ProfileFavoriteEntity(activeProfileId, channelId))
        else profileFavoriteDao.remove(activeProfileId, channelId)
    }

    override fun isFavorite(channelId: Long): Flow<Boolean> = profileFavoriteDao.observeIsFavorite(activeProfileId, channelId)

    override fun favoriteGroups(): Flow<List<FavoriteGroup>> =
        favoriteGroupDao.observeForProfile(activeProfileId).map { rows ->
            rows.map { FavoriteGroup(it.id, it.name, it.sortOrder) }
        }

    override suspend fun createFavoriteGroup(name: String): Long {
        ensureDefaultProfile()
        return favoriteGroupDao.insert(
            FavoriteGroupEntity(profileId = activeProfileId, name = name, sortOrder = 0)
        )
    }

    override suspend fun addChannelToFavoriteGroup(channelId: Long, groupId: Long) {
        ensureDefaultProfile()
        profileFavoriteDao.upsert(
            ProfileFavoriteEntity(profileId = activeProfileId, channelId = channelId, groupId = groupId)
        )
    }

    override suspend fun removeChannelFromFavorites(channelId: Long) {
        ensureDefaultProfile()
        profileFavoriteDao.remove(activeProfileId, channelId)
    }

    override suspend fun saveWatchPosition(channelId: Long, position: Long, programTitle: String?) {
        ensureDefaultProfile()
        val existing = profileWatchHistoryDao.get(activeProfileId, channelId)
        profileWatchHistoryDao.upsert(
            ProfileWatchHistoryEntity(
                profileId = activeProfileId,
                channelId = channelId,
                lastPosition = position,
                lastWatched = System.currentTimeMillis(),
                totalWatchMs = (existing?.totalWatchMs ?: 0L) + 30_000L,
                hourBucket = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                genreHint = null,
                lastProgramTitle = programTitle ?: existing?.lastProgramTitle
            )
        )
    }

    override suspend fun watchHistory(channelId: Long): WatchHistory? =
        profileWatchHistoryDao.get(activeProfileId, channelId)?.let { WatchHistory(it.channelId, it.lastPosition, it.lastWatched) }

    override suspend fun channelById(channelId: Long): Channel? = channelDao.getById(channelId)?.let { channelFromEntity(it) }

    override suspend fun channelByNumber(number: Int): Channel? = channelDao.getByNumber(number)?.let { channelFromEntity(it) }

    override suspend fun loadSettings(): AppSettings {
        ensureDefaultProfile()
        val db = profileSettingsDao.get(activeProfileId) ?: return cachedSettings
        return AppSettings(
            streamRetries = db.streamRetries,
            preferredAudioLanguage = db.preferredAudioLanguage,
            epgRowHeight = runCatching { EpgRowHeight.valueOf(db.epgRowHeight) }.getOrDefault(EpgRowHeight.NORMAL),
            miniPlayerAudioEnabled = db.previewEnabled,
            pinProtectedGroups = emptySet(),
            sleepTimerMinutes = db.sleepTimerMinutes,
            hideAdultContent = db.hideAdultContent,
            sleepTimerAutoEnabled = db.sleepTimerAutoEnabled,
            autoScanEnabled = db.autoScanEnabled,
            scanIntervalMinutes = db.scanIntervalMinutes,
            concurrentChecks = db.concurrentChecks,
            scanOnMetered = db.scanOnMetered,
            preferredSearchInput = SearchInputMode.fromStored(db.preferredSearchInput)
        )
    }

    override suspend fun saveSettings(settings: AppSettings) {
        ensureDefaultProfile()
        cachedSettings = settings
        val old = profileSettingsDao.get(activeProfileId)
        profileSettingsDao.upsert(
            ProfileSettingsEntity(
                profileId = activeProfileId,
                preferredAudioLanguage = settings.preferredAudioLanguage,
                epgRowHeight = settings.epgRowHeight.name,
                streamRetries = settings.streamRetries,
                previewEnabled = settings.miniPlayerAudioEnabled,
                gameLockEnabled = old?.gameLockEnabled ?: false,
                lastSleepTimer = settings.sleepTimerMinutes,
                recordingStoragePath = old?.recordingStoragePath,
                lastSeenVersion = old?.lastSeenVersion,
                sleepTimerMinutes = settings.sleepTimerMinutes,
                hideAdultContent = settings.hideAdultContent,
                sleepTimerAutoEnabled = settings.sleepTimerAutoEnabled,
                autoScanEnabled = settings.autoScanEnabled,
                scanIntervalMinutes = settings.scanIntervalMinutes,
                concurrentChecks = settings.concurrentChecks,
                scanOnMetered = settings.scanOnMetered,
                lastFullScanAt = old?.lastFullScanAt,
                preferredSearchInput = settings.preferredSearchInput.name
            )
        )
    }

    override suspend fun preferredSearchInput(): SearchInputMode {
        ensureDefaultProfile()
        return SearchInputMode.fromStored(profileSettingsDao.get(activeProfileId)?.preferredSearchInput)
    }

    override suspend fun setPreferredSearchInput(mode: SearchInputMode) {
        ensureDefaultProfile()
        val old = profileSettingsDao.get(activeProfileId) ?: ProfileSettingsEntity(profileId = activeProfileId)
        profileSettingsDao.upsert(old.copy(preferredSearchInput = mode.name))
        cachedSettings = cachedSettings.copy(preferredSearchInput = mode)
    }

    override suspend fun lastFullScanAt(): Long? {
        ensureDefaultProfile()
        return profileSettingsDao.get(activeProfileId)?.lastFullScanAt
    }

    override suspend fun updateLastFullScanAt(timestamp: Long) {
        ensureDefaultProfile()
        val old = profileSettingsDao.get(activeProfileId) ?: ProfileSettingsEntity(profileId = activeProfileId)
        profileSettingsDao.upsert(old.copy(lastFullScanAt = timestamp))
    }

    override fun buildCatchupUrl(program: Program, channel: Channel): String? {
        program.catchupUrl?.takeIf { it.isNotBlank() }?.let { return it }
        val template = channel.catchupSource ?: return null
        if (channel.catchupDays <= 0) return null
        return template.replace("{start}", program.startTime.toString())
            .replace("{end}", program.endTime.toString())
            .replace("{duration}", ((program.endTime - program.startTime) / 1000).toString())
    }

    override suspend fun shouldShowWhatsNew(currentVersion: String): Boolean {
        ensureDefaultProfile()
        val seen = profileSettingsDao.get(activeProfileId)?.lastSeenVersion
        return seen != currentVersion
    }

    override suspend fun markVersionSeen(currentVersion: String) {
        ensureDefaultProfile()
        val old = profileSettingsDao.get(activeProfileId) ?: ProfileSettingsEntity(profileId = activeProfileId)
        profileSettingsDao.upsert(old.copy(lastSeenVersion = currentVersion))
    }

    override suspend fun exportBackup(file: File): String {
        ensureDefaultProfile()
        return gridBackupManager.exportTo(file, activeProfileId)
    }

    override suspend fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File): String {
        ensureDefaultProfile()
        val summary = tiviMateImporter.importZip(contentResolver, uri, cacheDir, activeProfileId)
        return "Imported ${summary.channels} channels, ${summary.favorites} favorites, ${summary.playlists} playlists"
    }

    override suspend fun resetApp() = withContext(Dispatchers.IO) {
        ensureDefaultProfile()
        playlistDao.all().forEach { playlist ->
            channelDao.clearByPlaylist(playlist.id)
            secureCredentialStore.removePlaylistCredentials(playlist.id)
            playlistDao.delete(playlist.id)
        }
        programDao.clearAll()
        profileFavoriteDao.deleteAll()
        profileWatchHistoryDao.deleteAll()
        favoriteGroupDao.deleteAll()
        streamHealthDao.deleteAll()
        channelScanDao.deleteAll()
        recordingDao.deleteAll()
        scheduledRecordingDao.deleteAll()
        recordedMediaDao.deleteAll()
        epgCache.clear()
        cachedSettings = AppSettings()
        saveSettings(AppSettings())
    }
}
