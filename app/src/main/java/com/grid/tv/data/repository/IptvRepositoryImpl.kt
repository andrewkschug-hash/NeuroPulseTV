package com.grid.tv.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.FavoriteDao
import com.grid.tv.data.db.dao.FavoriteGroupDao
import com.grid.tv.data.db.dao.PlaylistDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileFavoriteDao
import com.grid.tv.data.db.dao.ProfileSettingsDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.ProgramDao
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.dao.RecordingDao
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import com.grid.tv.data.db.dao.SeriesRecordingRuleDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.WatchHistoryDao
import com.grid.tv.data.db.entity.ActiveProfileEntity
import com.grid.tv.data.db.entity.PlaylistEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import com.grid.tv.data.db.entity.ProfileSettingsEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.UserProfileEntity
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.network.RemoteTextFetcher
import com.grid.tv.data.network.parser.M3uParser
import com.grid.tv.data.network.parser.XtreamParser
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.data.network.stalker.StalkerPortalClient
import com.grid.tv.data.security.SecureCredentialStore
import com.grid.tv.data.session.GuestSessionPreferences
import com.grid.tv.domain.model.ConnectionFormFields
import com.grid.tv.domain.model.PlaylistConnectResult
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.RecordQuality
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.util.MAX_HOUSEHOLD_PROFILES
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.EpgResolutionStatus
import com.grid.tv.domain.model.EpgRowHeight
import com.grid.tv.domain.model.AspectRatioSetting
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.ClockDisplay
import com.grid.tv.domain.model.DpadSensitivity
import com.grid.tv.domain.model.MaxContentRating
import com.grid.tv.domain.model.StreamQuality
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import com.grid.tv.domain.model.Recommendation
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.StreamHealth
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.grid.tv.feature.epg.EpgBlockCache
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.data.db.entity.FavoriteGroupEntity
import com.grid.tv.feature.backup.GridBackupManager
import com.grid.tv.feature.health.StreamHealthEngine
import com.grid.tv.feature.recommendation.RecommendationEngine
import com.grid.tv.feature.recommendation.WatchStat
import com.grid.tv.feature.tivimate.TiviMateImporter
import com.grid.tv.worker.EpgScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.net.URLEncoder
import java.net.URI
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
    private val seriesRecordingRuleDao: SeriesRecordingRuleDao,
    private val recordedMediaDao: RecordedMediaDao,
    private val streamHealthDao: StreamHealthDao,
    private val channelScanDao: ChannelScanDao,
    private val favoriteDao: FavoriteDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val epgSourceChannelDao: EpgSourceChannelDao,
    private val epgResolutionSuggestionDao: EpgResolutionSuggestionDao,
    private val remoteTextFetcher: RemoteTextFetcher,
    private val appHttpClient: AppHttpClient,
    private val m3uParser: M3uParser,
    private val xtreamParser: XtreamParser,
    private val xmlTvParser: XmlTvParser,
    private val tiviMateImporter: TiviMateImporter,
    private val epgScheduler: EpgScheduler,
    private val secureCredentialStore: SecureCredentialStore,
    private val stalkerPortalClient: StalkerPortalClient,
    private val gridBackupManager: GridBackupManager,
    private val favoritesRepository: FavoritesRepository,
    private val guestSessionPreferences: GuestSessionPreferences
) : IptvRepository {

    companion object {
        private const val CONNECT_ERROR = "Login or URL invalid"
        private const val IPTV_REPO_LOG_TAG = "IptvRepository"
        private const val CHANNEL_INSERT_CHUNK = 400
        const val CHANNEL_PAGE_SIZE = 200
    }

    private val recommendationEngine = RecommendationEngine()
    private val healthEngine = StreamHealthEngine()
    private val epgCache = EpgBlockCache(maxBlocks = 6)
    private val _epgDataRevision = MutableStateFlow(0L)

    private var cachedSettings = AppSettings()
    private var activeProfileId = 1L
    private val vodCacheByPlaylist = MutableStateFlow<Map<Long, List<VodItem>>>(emptyMap())
    private val vodCategoriesByPlaylist = MutableStateFlow<Map<Long, List<VodCategory>>>(emptyMap())
    private val seriesCacheByPlaylist = MutableStateFlow<Map<Long, List<SeriesShow>>>(emptyMap())
    private val vodCatalogLoading = MutableStateFlow(false)
    private val seriesSeasonsCache = linkedMapOf<Pair<Long, Long>, List<SeriesSeason>>()

    private fun encode(v: String): String = URLEncoder.encode(v, Charsets.UTF_8.name())

    private fun normalizeServerUrl(raw: String): String = xtreamParser.normalizeServerUrl(raw)

    private fun resolveXtreamServerUrl(userEntered: String, auth: XtreamParser.AuthPayload): String =
        xtreamParser.resolveServerUrl(userEntered, auth)

    private fun buildXtreamApiUrl(serverUrl: String, username: String, password: String, action: String? = null, extra: String? = null): String {
        val base = "${normalizeServerUrl(serverUrl)}/player_api.php?username=${encode(username)}&password=${encode(password)}"
        val withAction = if (action != null) "$base&action=$action" else base
        return if (!extra.isNullOrBlank()) "$withAction&$extra" else withAction
    }

    private fun buildXtreamXmlTvUrl(serverUrl: String, username: String, password: String): String {
        val base = normalizeServerUrl(serverUrl).trimEnd('/')
        return "$base/xmltv.php?username=${encode(username)}&password=${encode(password)}"
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
        favoritesRepository.ensureDefaultGroups(activeProfileId)
    }

    private fun clearSeriesSeasonsForPlaylist(playlistId: Long) {
        seriesSeasonsCache.keys.removeAll { it.first == playlistId }
    }

    /** Local deduplication when loading provider channel lists — no server-side pipeline. */
    private fun deduplicateChannels(
        channels: List<com.grid.tv.data.db.entity.ChannelEntity>
    ): List<com.grid.tv.data.db.entity.ChannelEntity> {
        val seenUrls = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        return deduplicateChannelBatch(channels, seenUrls, seenNames)
    }

    private fun deduplicateChannelBatch(
        channels: List<com.grid.tv.data.db.entity.ChannelEntity>,
        seenUrls: MutableSet<String>,
        seenNames: MutableSet<String>
    ): List<com.grid.tv.data.db.entity.ChannelEntity> =
        channels.filter { channel ->
            val urlKey = channel.streamUrl.trim().lowercase()
            val nameKey = "${channel.number}:${channel.name.trim().lowercase()}"
            val urlUnique = urlKey.isBlank() || seenUrls.add(urlKey)
            val nameUnique = seenNames.add(nameKey)
            urlUnique && nameUnique
        }

    private suspend fun insertParsedChannelBatches(
        playlistId: Long,
        seenUrls: MutableSet<String>,
        seenNames: MutableSet<String>,
        batches: List<com.grid.tv.data.db.entity.ChannelEntity>
    ): Int {
        if (batches.isEmpty()) return 0
        val unique = deduplicateChannelBatch(
            batches.map { it.copy(playlistId = playlistId) },
            seenUrls,
            seenNames
        )
        unique.chunked(CHANNEL_INSERT_CHUNK).forEach { channelDao.insertAll(it) }
        return unique.size
    }

    private suspend fun mapChannelEntities(entities: List<com.grid.tv.data.db.entity.ChannelEntity>): List<Channel> {
        if (entities.isEmpty()) return emptyList()
        val playlistNames = playlistNameMap()
        val playlists = playlistDao.all().associateBy { it.id }
        val health = streamHealthDao.observeAll().first().associateBy { it.channelId }
        val favIds = profileFavoriteDao.observeForProfile(activeProfileId).first().map { it.channelId }.toSet()
        val resolved = entities.map { entity ->
            resolveXtreamPlaybackEntity(entity, playlists[entity.playlistId])
        }
        return mapChannelsWithPlaylists(resolved, playlistNames, health, favIds)
    }

    private fun resolveXtreamPlaybackEntity(
        entity: com.grid.tv.data.db.entity.ChannelEntity,
        playlist: PlaylistEntity?
    ): com.grid.tv.data.db.entity.ChannelEntity {
        if (playlist?.type != PlaylistType.XTREAM.name) return entity
        val server = playlist.xtreamServerUrl ?: playlist.url
        val username = playlist.xtreamUsername ?: return entity
        val password = resolveXtreamPassword(playlist) ?: return entity
        val streamId = extractXtreamStreamId(entity.streamUrl) ?: return entity
        val extension = entity.streamUrl.substringAfterLast('.', "m3u8").substringBefore('?')
        val streamUrl = xtreamParser.buildLiveStreamUrl(
            serverUrl = server,
            username = username,
            password = password,
            streamId = streamId,
            extension = extension
        )
        val backupExt = if (extension.equals("ts", ignoreCase = true)) "m3u8" else "ts"
        val backupStreamUrl = xtreamParser.buildLiveStreamUrl(
            serverUrl = server,
            username = username,
            password = password,
            streamId = streamId,
            extension = backupExt
        ).takeIf { it != streamUrl }
        return entity.copy(
            streamUrl = streamUrl,
            backupStreamUrl = backupStreamUrl
        )
    }

    private fun extractXtreamStreamId(streamUrl: String): String? {
        val trimmed = streamUrl.trim()
        if (trimmed.isBlank()) return null
        val segment = runCatching { URI(trimmed).path }.getOrNull()
            ?.trim('/')
            ?.substringAfterLast('/')
            ?: trimmed.substringAfterLast('/')
        val match = Regex("""^(\d+)\.[\w]+$""").matchEntire(segment) ?: return null
        return match.groupValues[1]
    }

    private fun mapChannelsWithPlaylists(
        rows: List<com.grid.tv.data.db.entity.ChannelEntity>,
        playlistNames: Map<Long, String>,
        health: Map<Long, StreamHealthEntity>,
        favIds: Set<Long>
    ): List<Channel> = rows.map { entity ->
        channelFromEntity(entity, playlistNames[entity.playlistId]).copy(
            isFavorite = entity.id in favIds,
            reliabilityScore = health[entity.id]?.reliabilityScore ?: 50
        )
    }

    private suspend fun playlistNameMap(): Map<Long, String> =
        playlistDao.all().associate { it.id to it.name }

    private fun channelFromEntity(entity: com.grid.tv.data.db.entity.ChannelEntity, playlistName: String? = null): Channel {
        return Channel(
            id = entity.id,
            number = entity.number,
            name = entity.name,
            group = entity.groupName,
            logoUrl = entity.logoUrl,
            epgId = entity.epgId,
            streamUrl = entity.streamUrl,
            backupStreamUrl = entity.backupStreamUrl,
            backupStreamUrl2 = entity.backupStreamUrl2,
            backupStreamUrl3 = entity.backupStreamUrl3,
            playlistId = entity.playlistId,
            playlistName = playlistName,
            isFavorite = false,
            reliabilityScore = 50,
            catchupDays = entity.catchupDays,
            catchupSource = entity.catchupSource,
            catchupMode = entity.catchupMode,
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

    override suspend fun hasActiveConnection(): Boolean = playlistDao.all().isNotEmpty()

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
            playlistDao.observeAll(),
            streamHealthDao.observeAll(),
            profileFavoriteDao.observeForProfile(activeProfileId)
        ) { rows, playlists, healthRows, favs ->
            val playlistNames = playlists.associate { it.id to it.name }
            val playlistById = playlists.associateBy { it.id }
            val health = healthRows.associateBy { it.channelId }
            val favIds = favs.map { it.channelId }.toSet()
            val resolved = rows.map { entity ->
                resolveXtreamPlaybackEntity(entity, playlistById[entity.playlistId])
            }
            mapChannelsWithPlaylists(resolved, playlistNames, health, favIds)
        }
    }

    override suspend fun channelsPage(
        groups: Set<String>,
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?,
        limit: Int,
        offset: Int
    ): List<Channel> = withContext(Dispatchers.IO) {
        val groupFilter = favoriteGroupId ?: -1L
        val rows = if (groups.isEmpty()) {
            channelDao.channelsPage(
                groupName = null,
                search = search,
                onlyFavorites = favoritesOnly,
                profileId = activeProfileId,
                favoriteGroupId = groupFilter,
                limit = limit,
                offset = offset
            )
        } else {
            channelDao.channelsPageInGroups(
                groupNames = groups.toList(),
                search = search,
                onlyFavorites = favoritesOnly,
                profileId = activeProfileId,
                favoriteGroupId = groupFilter,
                limit = limit,
                offset = offset
            )
        }
        mapChannelEntities(rows)
    }

    override fun hasChannels(): Flow<Boolean> =
        channelDao.observeTotalCount().map { it > 0 }

    override suspend fun searchChannels(query: String, limit: Int): List<Channel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        mapChannelEntities(channelDao.channelsPage(null, trimmed, false, activeProfileId, -1L, limit, 0))
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
        return combine(
            profileWatchHistoryDao.observeTop(activeProfileId, 500),
            playlistDao.observeAll()
        ) { hist, playlists ->
            val stats = hist.groupBy { it.channelId }.mapValues { (_, items) ->
                WatchStat(items.size, items.map { it.hourBucket }.average().toInt(), items.firstOrNull()?.genreHint)
            }
            val channelIds = stats.keys.toList()
            val channels = if (channelIds.isEmpty()) {
                emptyList()
            } else {
                channelDao.getByIds(channelIds).map { channelFromEntity(it, playlists.associate { p -> p.id to p.name }[it.playlistId]) }
            }
            recommendationEngine.score(channels, stats, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), null).take(limit)
        }
    }

    override fun continueWatching(limit: Int): Flow<List<Channel>> = flowOf(emptyList())

    override fun continueWatchingItems(limit: Int): Flow<List<ContinueWatchingItem>> = flowOf(emptyList())

    override fun topChannels(limit: Int): Flow<List<Channel>> =
        combine(
            profileWatchHistoryDao.observeTop(activeProfileId, limit),
            playlistDao.observeAll()
        ) { rows, playlists ->
            val ids = rows.map { it.channelId }
            if (ids.isEmpty()) return@combine emptyList()
            val playlistById = playlists.associateBy { it.id }
            val channels = channelDao.getByIds(ids).associateBy { it.id }
            val names = playlists.associate { it.id to it.name }
            rows.mapNotNull { channels[it.channelId] }.map { entity ->
                channelFromEntity(
                    resolveXtreamPlaybackEntity(entity, playlistById[entity.playlistId]),
                    names[entity.playlistId]
                )
            }
        }

    override fun recentChannels(limit: Int): Flow<List<Channel>> =
        combine(
            profileWatchHistoryDao.observeRecent(activeProfileId, limit),
            playlistDao.observeAll()
        ) { rows, playlists ->
            val ids = rows.map { it.channelId }
            if (ids.isEmpty()) return@combine emptyList()
            val playlistById = playlists.associateBy { it.id }
            val channels = channelDao.getByIds(ids).associateBy { it.id }
            val names = playlists.associate { it.id to it.name }
            rows.mapNotNull { channels[it.channelId] }.map { entity ->
                channelFromEntity(
                    resolveXtreamPlaybackEntity(entity, playlistById[entity.playlistId]),
                    names[entity.playlistId]
                )
            }
        }

    override fun recentlyAdded(limit: Int): Flow<List<Channel>> =
        combine(channelDao.observeRecentlyAdded(limit), playlistDao.observeAll()) { rows, playlists ->
            val names = playlists.associate { it.id to it.name }
            val playlistById = playlists.associateBy { it.id }
            rows.map { entity ->
                channelFromEntity(
                    resolveXtreamPlaybackEntity(entity, playlistById[entity.playlistId]),
                    names[entity.playlistId]
                )
            }
        }

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
            if (epgIds.isEmpty()) return@withContext emptyList()
            val key = "${start / 1000}-${end / 1000}-${epgIds.size}-${epgIds.hashCode()}"
            epgCache.get(key)?.let { return@withContext it }
            val entities = epgIds.chunked(400).flatMap { chunk ->
                programDao.loadWindow(chunk, start, end)
            }
            val foundLower = entities.map { it.channelEpgId.lowercase() }.toSet()
            val missingLower = epgIds.map { it.lowercase() }.filter { it !in foundLower }.distinct()
            val caseInsensitive = if (missingLower.isEmpty()) {
                emptyList()
            } else {
                missingLower.chunked(400).flatMap { chunk ->
                    programDao.loadWindowIgnoreCase(chunk, start, end)
                }
            }
            val loaded = (entities + caseInsensitive)
                .distinctBy { it.id }
                .map { row ->
                    Program(
                        id = row.id,
                        channelEpgId = row.channelEpgId,
                        title = row.title,
                        description = row.description,
                        startTime = row.startTime,
                        endTime = row.endTime,
                        genre = runCatching { ProgramGenre.valueOf(row.genre) }.getOrDefault(ProgramGenre.GENERAL),
                        catchupUrl = row.catchupUrl
                    )
                }
            epgCache.put(key, loaded)
            loaded
        }

    override suspend fun allDistinctEpgIds(): List<String> = withContext(Dispatchers.IO) {
        channelDao.allDistinctEpgIds()
    }

    override fun epgDataRevision(): Flow<Long> = _epgDataRevision.asStateFlow()

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
        val guestProfileId = if (guestSessionPreferences.isGuestSession()) {
            guestSessionPreferences.guestProfileId()
        } else {
            -1L
        }
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
        if (guestProfileId > 0L && guestSessionPreferences.isGuestSession()) {
            migrateGuestProfileData(fromProfileId = guestProfileId, toProfileId = id)
            guestSessionPreferences.clearGuestSession()
            setActiveProfile(id)
        } else {
            if (profileSettingsDao.get(id) == null) {
                profileSettingsDao.upsert(ProfileSettingsEntity(profileId = id))
            }
            if (profileDao.countUserProfiles() == 1) {
                setActiveProfile(id)
            }
        }
        return id
    }

    override suspend fun enterGuestSession() {
        ensureDefaultProfile()
        if (profileSettingsDao.get(activeProfileId) == null) {
            profileSettingsDao.upsert(ProfileSettingsEntity(profileId = activeProfileId))
        }
        guestSessionPreferences.startGuestSession(activeProfileId)
    }

    private suspend fun migrateGuestProfileData(fromProfileId: Long, toProfileId: Long) {
        if (fromProfileId == toProfileId) {
            favoritesRepository.ensureDefaultGroups(toProfileId)
            return
        }
        profileSettingsDao.get(fromProfileId)?.let { settings ->
            profileSettingsDao.upsert(settings.copy(profileId = toProfileId))
        } ?: profileSettingsDao.upsert(ProfileSettingsEntity(profileId = toProfileId))

        val groupIdMap = mutableMapOf<Long, Long>()
        favoriteGroupDao.getAllForProfile(fromProfileId).forEach { group ->
            val newGroupId = favoriteGroupDao.insert(
                group.copy(id = 0, profileId = toProfileId)
            )
            groupIdMap[group.id] = newGroupId
        }
        favoritesRepository.ensureDefaultGroups(toProfileId)

        profileFavoriteDao.allForProfile(fromProfileId).forEach { favorite ->
            profileFavoriteDao.upsert(
                favorite.copy(
                    profileId = toProfileId,
                    groupId = favorite.groupId?.let { groupIdMap[it] ?: it }
                )
            )
        }
        profileWatchHistoryDao.allForProfile(fromProfileId).forEach { entry ->
            profileWatchHistoryDao.upsert(entry.copy(profileId = toProfileId))
        }
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
        guestSessionPreferences.clearGuestSession()
    }

    override suspend fun verifyProfilePin(profileId: Long, pin: String): Boolean {
        val profile = profileDao.getProfile(profileId) ?: return false
        return profile.pin == pin
    }

    override suspend fun updateProfilePin(profileId: Long, pin: String?) {
        val profile = profileDao.getProfile(profileId) ?: return
        profileDao.upsertProfile(profile.copy(pin = pin?.takeIf { it.length == 4 }))
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
            val playlistId = insertXtreamPlaylist(displayName, serverUrl, username, password, null, 24)
            val count = channelDao.countByPlaylist(playlistId)
            if (count == 0) {
                playlistDao.delete(playlistId)
                secureCredentialStore.removePlaylistCredentials(playlistId)
                return@withContext PlaylistConnectResult(
                    false,
                    displayName,
                    errorMessage = "Provider returned no live channels"
                )
            }
            PlaylistConnectResult(true, displayName, count)
        } catch (e: Exception) {
            Log.e(IPTV_REPO_LOG_TAG, "Xtream connect failed", e)
            PlaylistConnectResult(
                false,
                displayName,
                errorMessage = e.message?.takeIf { it.isNotBlank() } ?: CONNECT_ERROR
            )
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

    private suspend fun importM3uChannels(playlistId: Long, url: String) {
        val normalizedUrl = remoteTextFetcher.normalizeRemoteUrl(url)
        channelDao.clearByPlaylist(playlistId)
        val seenUrls = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        var totalInserted = 0
        m3uParser.parseAsFlow(playlistId, remoteTextFetcher.fetch(normalizedUrl)).collect { progress ->
            totalInserted += insertParsedChannelBatches(playlistId, seenUrls, seenNames, progress.batch)
            if (progress.done && totalInserted == 0) {
                throw IllegalStateException("No channels in playlist")
            }
        }
        secureCredentialStore.saveM3uUrl(playlistId, normalizedUrl)
    }

    private fun scheduleEpgImportWork(startedAt: Long) {
        epgScheduler.runResolverForNewChannels(startedAt)
        epgScheduler.runEpgRefreshNow()
    }

    private suspend fun insertM3uPlaylist(name: String, url: String, epgUrl: String?, refreshHours: Int): Long {
        val startedAt = System.currentTimeMillis()
        val normalizedUrl = remoteTextFetcher.normalizeRemoteUrl(url)
        val playlistId = playlistDao.insert(
            PlaylistEntity(
                name = name,
                url = normalizedUrl,
                epgUrl = epgUrl,
                refreshIntervalHours = refreshHours,
                type = PlaylistType.M3U.name
            )
        )
        importM3uChannels(playlistId, url)
        scheduleEpgImportWork(startedAt)
        return playlistId
    }

    override suspend fun updateM3uPlaylist(
        playlistId: Long,
        name: String,
        url: String,
        epgUrl: String?,
        refreshHours: Int
    ) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")
        val startedAt = System.currentTimeMillis()
        val normalizedUrl = remoteTextFetcher.normalizeRemoteUrl(url)
        playlistDao.update(
            existing.copy(
                name = name,
                url = normalizedUrl,
                epgUrl = epgUrl,
                refreshIntervalHours = refreshHours
            )
        )
        importM3uChannels(playlistId, url)
        scheduleEpgImportWork(startedAt)
    }

    private suspend fun importXtreamCatalog(
        playlistId: Long,
        normalizedServer: String,
        username: String,
        password: String,
        auth: XtreamParser.AuthPayload
    ): PlaylistEntity {
        Log.i(IPTV_REPO_LOG_TAG, "Fetching Xtream live catalog from $normalizedServer")
        val categoriesRaw = remoteTextFetcher.fetch(
            buildXtreamApiUrl(normalizedServer, username, password, action = "get_live_categories")
        )
        val liveRaw = remoteTextFetcher.fetch(
            buildXtreamApiUrl(normalizedServer, username, password, action = "get_live_streams")
        )
        Log.i(IPTV_REPO_LOG_TAG, "Live streams payload: ${liveRaw.length} bytes")

        val categories = withContext(Dispatchers.Default) {
            xtreamParser.parseLiveCategories(categoriesRaw)
        }
        val liveChannels = withContext(Dispatchers.Default) {
            xtreamParser.parseLiveChannels(
                playlistId, liveRaw, username, password, normalizedServer, categories
            )
        }
        Log.i(IPTV_REPO_LOG_TAG, "Parsed ${liveChannels.size} live channels (${categories.size} categories)")

        channelDao.clearByPlaylist(playlistId)
        val seenUrls = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        var inserted = 0
        liveChannels.chunked(CHANNEL_INSERT_CHUNK).forEach { chunk ->
            inserted += insertParsedChannelBatches(playlistId, seenUrls, seenNames, chunk)
        }

        if (inserted == 0) {
            val preview = liveRaw.trim().take(240)
            Log.e(IPTV_REPO_LOG_TAG, "No live channels parsed. Response preview: $preview")
            throw IllegalStateException(
                if (liveRaw.trimStart().startsWith("[")) {
                    "Provider returned an empty live channel list"
                } else {
                    "Unexpected live streams response from provider"
                }
            )
        }
        Log.i(IPTV_REPO_LOG_TAG, "Inserted $inserted live channels into database")
        clearSeriesSeasonsForPlaylist(playlistId)
        vodCacheByPlaylist.update { it - playlistId }
        vodCategoriesByPlaylist.update { it - playlistId }
        seriesCacheByPlaylist.update { it - playlistId }

        val existing = playlistDao.getById(playlistId) ?: throw IllegalStateException("Playlist not found")
        return existing.copy(
            url = normalizedServer,
            xtreamServerUrl = normalizedServer,
            xtreamUsername = username,
            xtreamAccountStatus = auth.status,
            xtreamExpiryDateEpochSec = auth.expiryDateEpochSec,
            xtreamMaxConnections = auth.maxConnections
        )
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
        val authRaw = remoteTextFetcher.fetch(authUrl)
        if (!xtreamParser.isAuthSuccessful(authRaw)) {
            throw IllegalStateException(CONNECT_ERROR)
        }
        val auth = xtreamParser.parseAuth(authRaw)
        if (auth.status.equals("Expired", ignoreCase = true)) {
            throw IllegalStateException("Xtream account is expired")
        }
        val normalizedServer = resolveXtreamServerUrl(serverUrl, auth)

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
        try {
            importXtreamCatalog(playlistId, normalizedServer, username, password, auth)
        } catch (e: Exception) {
            playlistDao.delete(playlistId)
            secureCredentialStore.removePlaylistCredentials(playlistId)
            vodCacheByPlaylist.update { it - playlistId }
            vodCategoriesByPlaylist.update { it - playlistId }
            seriesCacheByPlaylist.update { it - playlistId }
            throw e
        }
        scheduleEpgImportWork(startedAt)
        return playlistId
    }

    override suspend fun updateXtreamPlaylist(
        playlistId: Long,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        epgUrl: String?,
        refreshHours: Int
    ) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getById(playlistId)
            ?: throw IllegalArgumentException("Playlist not found")
        val startedAt = System.currentTimeMillis()
        val authRaw = remoteTextFetcher.fetch(buildXtreamApiUrl(serverUrl, username, password))
        if (!xtreamParser.isAuthSuccessful(authRaw)) {
            throw IllegalStateException(CONNECT_ERROR)
        }
        val auth = xtreamParser.parseAuth(authRaw)
        if (auth.status.equals("Expired", ignoreCase = true)) {
            throw IllegalStateException("Xtream account is expired")
        }
        val normalizedServer = resolveXtreamServerUrl(serverUrl, auth)
        val updatedMeta = importXtreamCatalog(playlistId, normalizedServer, username, password, auth)
        secureCredentialStore.saveXtreamPassword(playlistId, password)
        playlistDao.update(
            updatedMeta.copy(
                name = name,
                epgUrl = epgUrl,
                refreshIntervalHours = refreshHours,
                type = PlaylistType.XTREAM.name
            )
        )
        scheduleEpgImportWork(startedAt)
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
        val seenUrls = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        var totalInserted = 0
        channels.chunked(CHANNEL_INSERT_CHUNK).forEach { chunk ->
            totalInserted += insertParsedChannelBatches(playlistId, seenUrls, seenNames, chunk)
        }
        if (totalInserted == 0) {
            throw IllegalStateException("No channels in playlist")
        }
        scheduleEpgImportWork(startedAt)
        return playlistId
    }

    override suspend fun addPlaylistFromLocal(name: String, content: String, epgUrl: String?, refreshHours: Int) =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val playlistId = playlistDao.insert(PlaylistEntity(name = name, url = "local://$name", epgUrl = epgUrl, refreshIntervalHours = refreshHours, isLocalFile = true, type = PlaylistType.M3U.name))
            channelDao.clearByPlaylist(playlistId)
            val seenUrls = linkedSetOf<String>()
            val seenNames = linkedSetOf<String>()
            var totalInserted = 0
            m3uParser.parseAsFlow(playlistId, content).collect { progress ->
                totalInserted += insertParsedChannelBatches(playlistId, seenUrls, seenNames, progress.batch)
                if (progress.done && totalInserted == 0) {
                    throw IllegalStateException("No channels in playlist")
                }
            }
            scheduleEpgImportWork(startedAt)
        }

    override suspend fun connectionFormForPlaylist(playlist: Playlist): ConnectionFormFields =
        withContext(Dispatchers.IO) {
            when (playlist.type) {
                PlaylistType.M3U -> {
                    val storedUrl = secureCredentialStore.getM3uUrl(playlist.id) ?: playlist.url
                    val displayUrl = if (storedUrl.startsWith("local://")) {
                        storedUrl.removePrefix("local://")
                    } else {
                        storedUrl
                    }
                    ConnectionFormFields(
                        name = playlist.name,
                        playlistType = PlaylistType.M3U,
                        m3uUrl = displayUrl,
                        epgUrl = playlist.epgUrl.orEmpty(),
                        refreshHours = playlist.refreshIntervalHours.toString()
                    )
                }
                PlaylistType.XTREAM -> ConnectionFormFields(
                    name = playlist.name,
                    playlistType = PlaylistType.XTREAM,
                    epgUrl = playlist.epgUrl.orEmpty(),
                    refreshHours = playlist.refreshIntervalHours.toString(),
                    xtreamServer = playlist.xtreamServerUrl ?: playlist.url,
                    xtreamUser = playlist.xtreamUsername.orEmpty(),
                    xtreamPassword = secureCredentialStore.getXtreamPassword(playlist.id).orEmpty()
                )
                PlaylistType.STALKER -> ConnectionFormFields(
                    name = playlist.name,
                    playlistType = PlaylistType.STALKER,
                    m3uUrl = playlist.stalkerPortalUrl ?: playlist.url,
                    epgUrl = playlist.epgUrl.orEmpty(),
                    refreshHours = playlist.refreshIntervalHours.toString()
                )
            }
        }

    override suspend fun deletePlaylist(playlistId: Long) {
        secureCredentialStore.removePlaylistCredentials(playlistId)
        playlistDao.delete(playlistId)
        vodCacheByPlaylist.update { it - playlistId }
        vodCategoriesByPlaylist.update { it - playlistId }
        seriesCacheByPlaylist.update { it - playlistId }
        clearSeriesSeasonsForPlaylist(playlistId)
    }

    override suspend fun refreshEpgNow() = withContext(Dispatchers.IO) {
        epgCache.clear()
        val now = System.currentTimeMillis()
        val threshold = now - 24L * 60 * 60 * 1000
        programDao.purgeOlderThan(threshold)
        playlistDao.all().forEach { playlist ->
            val epgUrl = playlist.epgUrl
            val resolvedEpgUrl = when {
                !epgUrl.isNullOrBlank() -> epgUrl
                playlist.type == PlaylistType.XTREAM.name -> {
                    val server = playlist.xtreamServerUrl ?: playlist.url
                    val user = playlist.xtreamUsername ?: return@forEach
                    val pass = resolveXtreamPassword(playlist) ?: return@forEach
                    buildXtreamXmlTvUrl(server, user, pass)
                }
                else -> return@forEach
            }
            runCatching {
                val xml = remoteTextFetcher.fetch(resolvedEpgUrl)
                val parsed = xmlTvParser.parse(xml)
                if (parsed.programs.isNotEmpty()) {
                    programDao.insertAll(parsed.programs)
                    playlistDao.update(playlist.copy(lastRefreshed = now))
                    Log.i(
                        IPTV_REPO_LOG_TAG,
                        "EPG refresh: ${parsed.programs.size} programmes for ${playlist.name}"
                    )
                } else {
                    Log.w(IPTV_REPO_LOG_TAG, "EPG refresh: no programmes parsed from $resolvedEpgUrl")
                }
            }.onFailure { error ->
                Log.e(IPTV_REPO_LOG_TAG, "EPG refresh failed for ${playlist.name}", error)
            }
        }
        _epgDataRevision.update { it + 1 }
    }

    override suspend fun refreshXtreamEpg(streamId: Long): List<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        val merged = mutableListOf<Pair<Long, Long>>()
        playlistDao.all()
            .filter { it.type == PlaylistType.XTREAM.name }
            .forEach { playlist ->
                val server = playlist.xtreamServerUrl ?: return@forEach
                val user = playlist.xtreamUsername ?: return@forEach
                val pass = resolveXtreamPassword(playlist) ?: return@forEach
                runCatching {
                    val raw = remoteTextFetcher.fetch(
                        buildXtreamApiUrl(
                            server,
                            user,
                            pass,
                            action = "get_simple_data_table",
                            extra = "stream_id=$streamId"
                        )
                    )
                    merged += xtreamParser.parseSimpleDataTable(raw)
                }
            }
        merged
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
        val playlists = playlistDao.all().filter { it.type == PlaylistType.XTREAM.name }
        if (playlists.isEmpty()) return@withContext
        vodCatalogLoading.value = true
        try {
            playlists.forEach { playlist ->
                refreshVodCatalogForPlaylist(playlist)
            }
            playlists.forEach { playlist ->
                refreshSeriesCatalogForPlaylist(playlist)
            }
        } finally {
            vodCatalogLoading.value = false
        }
    }

    private suspend fun refreshVodCatalogForPlaylist(playlist: PlaylistEntity) {
        val server = playlist.xtreamServerUrl ?: playlist.url
        val user = playlist.xtreamUsername ?: return
        val pass = resolveXtreamPassword(playlist) ?: return
        runCatching {
            val vodRaw = remoteTextFetcher.fetch(
                buildXtreamApiUrl(server, user, pass, action = "get_vod_streams")
            )
            val vodCategoriesRaw = remoteTextFetcher.fetch(
                buildXtreamApiUrl(server, user, pass, action = "get_vod_categories")
            )
            val parsedVod = xtreamParser.parseVod(vodRaw, user, pass, server, playlist.id)
            vodCacheByPlaylist.update { current ->
                current + (playlist.id to parsedVod)
            }
            vodCategoriesByPlaylist.update { current ->
                current + (playlist.id to xtreamParser.parseVodCategories(vodCategoriesRaw, playlist.id))
            }
            Log.i(
                IPTV_REPO_LOG_TAG,
                "Loaded ${parsedVod.size} VOD titles for playlist ${playlist.id} (${playlist.name})"
            )
        }.onFailure { error ->
            Log.w(
                IPTV_REPO_LOG_TAG,
                "VOD catalog refresh failed for playlist ${playlist.id} (${playlist.name})",
                error
            )
        }
    }

    private suspend fun refreshSeriesCatalogForPlaylist(playlist: PlaylistEntity) {
        val server = playlist.xtreamServerUrl ?: playlist.url
        val user = playlist.xtreamUsername ?: return
        val pass = resolveXtreamPassword(playlist) ?: return
        runCatching {
            val seriesRaw = remoteTextFetcher.fetch(
                buildXtreamApiUrl(server, user, pass, action = "get_series")
            )
            val parsedSeries = xtreamParser.parseSeries(seriesRaw, playlist.id)
            seriesCacheByPlaylist.update { current ->
                current + (playlist.id to parsedSeries)
            }
            clearSeriesSeasonsForPlaylist(playlist.id)
            Log.i(
                IPTV_REPO_LOG_TAG,
                "Loaded ${parsedSeries.size} series for playlist ${playlist.id} (${playlist.name})"
            )
        }.onFailure { error ->
            Log.w(
                IPTV_REPO_LOG_TAG,
                "Series catalog refresh failed for playlist ${playlist.id} (${playlist.name})",
                error
            )
        }
    }

    override fun vodCatalogLoading(): Flow<Boolean> = vodCatalogLoading

    override fun vodStreams(): Flow<List<VodItem>> =
        vodCacheByPlaylist.map { cache -> cache.values.flatten() }

    override fun vodCategories(): Flow<List<VodCategory>> =
        vodCategoriesByPlaylist.map { cache -> cache.values.flatten() }

    override fun seriesShows(): Flow<List<SeriesShow>> =
        seriesCacheByPlaylist.map { cache -> cache.values.flatten() }

    override suspend fun seriesSeasons(seriesId: Long): List<SeriesSeason> {
        val playlistId = seriesCacheByPlaylist.value.entries.firstOrNull { (_, shows) ->
            shows.any { it.id == seriesId }
        }?.key ?: return emptyList()
        val cacheKey = playlistId to seriesId
        seriesSeasonsCache[cacheKey]?.let { return it }
        val playlist = playlistDao.getById(playlistId) ?: return emptyList()
        val server = playlist.xtreamServerUrl ?: return emptyList()
        val user = playlist.xtreamUsername ?: return emptyList()
        val pass = resolveXtreamPassword(playlist) ?: return emptyList()
        val raw = remoteTextFetcher.fetch(
            buildXtreamApiUrl(server, user, pass, action = "get_series_info", extra = "series_id=$seriesId")
        )
        val seasons = xtreamParser.parseSeriesInfo(raw, user, pass, server)
        seriesSeasonsCache[cacheKey] = seasons
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
        return favoritesRepository.createGroup(activeProfileId, name)
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

    override suspend fun saveVodWatchPosition(streamId: Long, positionMs: Long, title: String, durationMs: Long) {
        ensureDefaultProfile()
        val syntheticId = -streamId
        val existing = profileWatchHistoryDao.get(activeProfileId, syntheticId)
        profileWatchHistoryDao.upsert(
            ProfileWatchHistoryEntity(
                profileId = activeProfileId,
                channelId = syntheticId,
                lastPosition = positionMs.coerceAtLeast(0L),
                lastWatched = System.currentTimeMillis(),
                totalWatchMs = (existing?.totalWatchMs ?: 0L) + 5_000L,
                hourBucket = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                genreHint = durationMs.takeIf { it > 0L }?.toString(),
                lastProgramTitle = title
            )
        )
    }

    override fun vodWatchProgress(): Flow<Map<Long, Long>> =
        profileWatchHistoryDao.observeVodPositions(activeProfileId).map { rows ->
            rows.associate { (-it.channelId) to it.lastPosition }
        }

    private suspend fun mapChannelEntity(entity: com.grid.tv.data.db.entity.ChannelEntity): Channel? =
        mapChannelEntities(listOf(entity)).firstOrNull()

    override suspend fun channelById(channelId: Long): Channel? = withContext(Dispatchers.IO) {
        if (channelId <= 0L) return@withContext null
        val entity = channelDao.getById(channelId) ?: return@withContext null
        mapChannelEntity(entity)
    }

    override suspend fun channelByNumber(number: Int): Channel? = withContext(Dispatchers.IO) {
        if (number <= 0) return@withContext null
        val entity = channelDao.getByNumber(number) ?: return@withContext null
        mapChannelEntity(entity)
    }

    override suspend fun loadSettings(): AppSettings {
        ensureDefaultProfile()
        val db = profileSettingsDao.get(activeProfileId) ?: return cachedSettings.also {
            appHttpClient.applySettings(cachedSettings)
        }
        return AppSettings(
            streamRetries = db.streamRetries,
            preferredAudioLanguage = db.preferredAudioLanguage,
            epgRowHeight = enumValueOrDefault(db.epgRowHeight, EpgRowHeight.NORMAL),
            miniPlayerAudioEnabled = db.previewEnabled,
            pinProtectedGroups = emptySet(),
            sleepTimerMinutes = db.sleepTimerMinutes,
            hideAdultContent = db.hideAdultContent,
            sleepTimerAutoEnabled = db.sleepTimerAutoEnabled,
            autoScanEnabled = db.autoScanEnabled,
            scanIntervalMinutes = db.scanIntervalMinutes,
            concurrentChecks = db.concurrentChecks,
            scanOnMetered = db.scanOnMetered,
            preferredSearchInput = SearchInputMode.fromStored(db.preferredSearchInput),
            parentalPinLockEnabled = db.parentalPinLockEnabled,
            maxContentRating = enumValueOrDefault(db.maxContentRating, MaxContentRating.ALL_AGES),
            connectionTimeoutSeconds = db.connectionTimeoutSeconds,
            useProxy = db.useProxy,
            proxyUrl = db.proxyUrl,
            showEpgProgramInfoOnSidebar = db.showEpgProgramInfoOnSidebar,
            startChannelFromBeginningOnCatchup = db.startChannelFromBeginningOnCatchup,
            defaultStreamQuality = enumValueOrDefault(db.defaultStreamQuality, StreamQuality.AUTO),
            bufferSize = enumValueOrDefault(db.bufferSize, BufferSize.MEDIUM),
            autoReconnectOnDrop = db.autoReconnectOnDrop,
            preferHardwareDecoding = db.preferHardwareDecoding,
            aspectRatio = enumValueOrDefault(db.aspectRatio, AspectRatioSetting.AUTO),
            subtitlesEnabled = db.subtitlesEnabled,
            subtitleLanguage = db.subtitleLanguage,
            subtitleFontSize = enumValueOrDefault(db.subtitleFontSize, SubtitleFontSize.MEDIUM),
            subtitlePosition = enumValueOrDefault(db.subtitlePosition, SubtitlePosition.BOTTOM),
            subtitleDelayMs = db.subtitleDelayMs,
            deinterlacingEnabled = db.deinterlacingEnabled,
            miniPlayerEnabled = db.miniPlayerEnabled,
            sidebarAutoHideSeconds = db.sidebarAutoHideSeconds,
            showChannelNumbers = db.showChannelNumbers,
            dpadSidebarSensitivity = enumValueOrDefault(db.dpadSidebarSensitivity, DpadSensitivity.NORMAL),
            clockDisplay = enumValueOrDefault(db.clockDisplay, ClockDisplay.OFF),
            recordQuality = enumValueOrDefault(db.recordQuality, RecordQuality.ORIGINAL),
            recordedPlaybackSpeed = db.recordedPlaybackSpeed.coerceIn(0.5f, 2f).let {
                if (it <= 0f) 1f else it
            },
            themeId = AppThemeId.fromStored(db.themeId),
            pictureInPictureEnabled = db.pictureInPictureEnabled,
            guideChannelGroups = GuideChannelFilter.decode(db.guideChannelGroups),
            guideFiltersConfigured = db.guideFiltersConfigured
        ).also {
            cachedSettings = it
            appHttpClient.applySettings(it)
        }
    }

    override suspend fun saveSettings(settings: AppSettings) {
        ensureDefaultProfile()
        cachedSettings = settings
        appHttpClient.applySettings(settings)
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
                preferredSearchInput = settings.preferredSearchInput.name,
                parentalPinLockEnabled = settings.parentalPinLockEnabled,
                maxContentRating = settings.maxContentRating.name,
                connectionTimeoutSeconds = settings.connectionTimeoutSeconds,
                useProxy = settings.useProxy,
                proxyUrl = settings.proxyUrl,
                showEpgProgramInfoOnSidebar = settings.showEpgProgramInfoOnSidebar,
                startChannelFromBeginningOnCatchup = settings.startChannelFromBeginningOnCatchup,
                defaultStreamQuality = settings.defaultStreamQuality.name,
                bufferSize = settings.bufferSize.name,
                autoReconnectOnDrop = settings.autoReconnectOnDrop,
                preferHardwareDecoding = settings.preferHardwareDecoding,
                aspectRatio = settings.aspectRatio.name,
                subtitlesEnabled = settings.subtitlesEnabled,
                subtitleLanguage = settings.subtitleLanguage,
                subtitleFontSize = settings.subtitleFontSize.name,
                subtitlePosition = settings.subtitlePosition.name,
                subtitleDelayMs = settings.subtitleDelayMs,
                deinterlacingEnabled = settings.deinterlacingEnabled,
                miniPlayerEnabled = settings.miniPlayerEnabled,
                sidebarAutoHideSeconds = settings.sidebarAutoHideSeconds,
                showChannelNumbers = settings.showChannelNumbers,
                dpadSidebarSensitivity = settings.dpadSidebarSensitivity.name,
                clockDisplay = settings.clockDisplay.name,
                recordQuality = settings.recordQuality.name,
                recordedPlaybackSpeed = settings.recordedPlaybackSpeed,
                themeId = settings.themeId.name,
                pictureInPictureEnabled = settings.pictureInPictureEnabled,
                guideChannelGroups = GuideChannelFilter.encode(settings.guideChannelGroups),
                guideFiltersConfigured = settings.guideFiltersConfigured
            )
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)

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

    override fun buildCatchupUrl(program: Program, channel: Channel): String? =
        com.grid.tv.domain.epg.CatchupUrlFormatter.build(program, channel)

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

    override suspend fun clearAppCache() = withContext(Dispatchers.IO) {
        epgCache.clear()
    }

    override suspend fun resetSettingsToDefaults() {
        ensureDefaultProfile()
        val old = profileSettingsDao.get(activeProfileId)
        val defaults = AppSettings()
        cachedSettings = defaults
        profileSettingsDao.upsert(
            ProfileSettingsEntity(
                profileId = activeProfileId,
                recordingStoragePath = old?.recordingStoragePath,
                lastSeenVersion = old?.lastSeenVersion,
                lastFullScanAt = old?.lastFullScanAt
            )
        )
    }

    override suspend fun resetApp() = withContext(Dispatchers.IO) {
        playlistDao.all().forEach { playlist ->
            secureCredentialStore.removePlaylistCredentials(playlist.id)
        }

        channelDao.deleteAll()
        playlistDao.deleteAll()
        secureCredentialStore.clearAll()

        programDao.clearAll()
        epgSourceChannelDao.deleteAll()
        epgResolutionSuggestionDao.deleteAll()
        streamHealthDao.deleteAll()
        channelScanDao.deleteAll()

        profileFavoriteDao.deleteAll()
        profileWatchHistoryDao.deleteAll()
        favoriteGroupDao.deleteAll()
        profileSettingsDao.deleteAll()
        favoriteDao.deleteAll()
        watchHistoryDao.deleteAll()

        profileDao.deleteActiveProfile()
        profileDao.deleteAllProfiles()

        recordingDao.deleteAll()
        scheduledRecordingDao.deleteAll()
        recordedMediaDao.deleteAll()
        seriesRecordingRuleDao.deleteAll()

        epgCache.clear()
        vodCacheByPlaylist.value = emptyMap()
        vodCategoriesByPlaylist.value = emptyMap()
        seriesCacheByPlaylist.value = emptyMap()
        seriesSeasonsCache.clear()
        activeProfileId = 0L
        cachedSettings = AppSettings()
        guestSessionPreferences.clearGuestSession()
        saveSettings(AppSettings())
    }
}
