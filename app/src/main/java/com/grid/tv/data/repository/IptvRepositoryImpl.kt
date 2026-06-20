package com.grid.tv.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
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
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import com.grid.tv.data.db.entity.PlaylistEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import com.grid.tv.data.db.entity.ProfileSettingsEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.UserProfileEntity
import com.grid.tv.data.cache.VodCatalogDiskCache
import com.grid.tv.data.db.dao.SeriesShowDao
import com.grid.tv.data.db.dao.VodCategoryDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.AppDatabase
import com.grid.tv.data.db.mapper.toDomain
import com.grid.tv.data.db.mapper.toEntity
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.network.EpgXmlTvFetchOutcome
import com.grid.tv.data.network.RemoteTextFetcher
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.data.network.parser.M3uParser
import com.grid.tv.data.network.parser.XtreamParser
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.data.network.stalker.StalkerPortalClient
import com.grid.tv.data.security.SecureCredentialStore
import com.grid.tv.data.session.GuestSessionPreferences
import com.grid.tv.domain.epg.ChannelNameNormalizer
import com.grid.tv.domain.epg.EpgIdNormalizer
import com.grid.tv.domain.epg.programmeLookupKeys
import com.grid.tv.domain.epg.EpgChannelLinkResolver
import com.grid.tv.domain.epg.XmlTvChannelRef
import com.grid.tv.domain.model.PlaylistConnectResult
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.RecordQuality
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.util.DEFAULT_CONNECTION_TIMEOUT_SECONDS
import com.grid.tv.util.MAX_HOUSEHOLD_PROFILES
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ConnectionFormFields
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.EpgFetchAttempt
import com.grid.tv.domain.model.EpgRefreshReport
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
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.grid.tv.feature.epg.EpgBlockCache
import com.grid.tv.feature.epg.EpgCoroutineDispatchers
import com.grid.tv.feature.epg.EpgFlowLogger
import com.grid.tv.feature.epg.EpgJobCoordinator
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.data.db.entity.FavoriteGroupEntity
import com.grid.tv.feature.backup.GridBackupManager
import com.grid.tv.feature.health.StreamHealthEngine
import com.grid.tv.feature.recommendation.RecommendationEngine
import com.grid.tv.feature.recommendation.WatchStat
import com.grid.tv.feature.tivimate.TiviMateImporter
import com.grid.tv.feature.scanner.ChannelScanGate
import com.grid.tv.feature.scanner.EpgDownloadTracker
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import java.net.URLEncoder
import java.net.URI
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
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
    private val epgLearnedMappingDao: EpgLearnedMappingDao,
    private val epgResolutionSuggestionDao: EpgResolutionSuggestionDao,
    private val remoteTextFetcher: RemoteTextFetcher,
    private val appHttpClient: AppHttpClient,
    private val m3uParser: M3uParser,
    private val xtreamParser: XtreamParser,
    private val xmlTvParser: XmlTvParser,
    private val tiviMateImporter: TiviMateImporter,
    private val epgJobCoordinator: EpgJobCoordinator,
    private val channelScanGate: Lazy<ChannelScanGate>,
    private val playlistImportCoordinator: PlaylistImportCoordinator,
    private val secureCredentialStore: SecureCredentialStore,
    private val stalkerPortalClient: StalkerPortalClient,
    private val gridBackupManager: GridBackupManager,
    private val favoritesRepository: FavoritesRepository,
    private val guestSessionPreferences: GuestSessionPreferences,
    private val channelNameNormalizer: ChannelNameNormalizer,
    private val vodStreamDao: VodStreamDao,
    private val vodCategoryDao: VodCategoryDao,
    private val seriesShowDao: SeriesShowDao,
    private val vodCatalogDiskCache: VodCatalogDiskCache,
    private val epgDownloadTracker: EpgDownloadTracker,
    private val epgDispatchers: EpgCoroutineDispatchers
) : IptvRepository {

    companion object {
        private const val CONNECT_ERROR = "Login or URL invalid"
        private const val IPTV_REPO_LOG_TAG = "IptvRepository"
        private const val EPG_MATCH_DEBUG_TAG = "EPG Match Debug"
        private const val EPG_FLOW_TAG = "EpgFlow"
        private const val VOD_FLOW_TAG = "VodCatalogPipeline"
        private const val CHANNEL_INSERT_CHUNK = 400
        const val CHANNEL_PAGE_SIZE = 200
        const val VOD_PAGING_PAGE_SIZE = 60
        const val VOD_PAGING_PREFETCH = 20
        /** Serve in-memory/disk cache without network for this long unless [force] refresh. */
        private const val VOD_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        private const val VIEWPORT_EPG_LOOKBACK_MS = 30L * 60L * 1000L
        private const val VIEWPORT_EPG_LOOKAHEAD_MS = 4L * 60L * 60L * 1000L
        private const val VIEWPORT_EPG_COOLDOWN_MS = 90L * 1000L
        private const val VIEWPORT_EPG_SHORT_LIMIT = 16
        private const val VIEWPORT_EPG_MAX_CONCURRENT_REQUESTS = 2
        /** Only purge programmes that ended more than a week ago — never today's grid cache. */
        private const val EPG_PURGE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L
    }

    private val recommendationEngine = RecommendationEngine()
    private val healthEngine = StreamHealthEngine()
    private val epgCache = EpgBlockCache(maxBlocks = 6)
    private val viewportEpgLastFetch = mutableMapOf<Long, Long>()
    private val _epgDataRevision = MutableStateFlow(0L)
    @Volatile
    private var epgLinkResolver: EpgChannelLinkResolver? = null

    private var cachedSettings = AppSettings()
    private var activeProfileId = 1L
    private val _vodCatalogRevision = MutableStateFlow(0L)
    private val vodCatalogLoading = MutableStateFlow(false)
    private val vodCatalogProgress = MutableStateFlow(VodCatalogProgress())
    private val vodCatalogStatus = MutableStateFlow(VodCatalogStatus())
    private val vodRefreshMutex = Mutex()
    private val epgRefreshMutex = Mutex()
    private val vodDiskLoadMutex = Mutex()
    private val vodRepositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var vodDiskCacheLoaded = false
    @Volatile
    private var lastVodRefreshCompletedAtMs = 0L
    @Volatile
    private var activeVodRefresh: CompletableDeferred<Unit>? = null
    private val seriesSeasonsCache = linkedMapOf<Pair<Long, Long>, List<SeriesSeason>>()

    init {
        vodRepositoryScope.launch {
            warmLocalUiCache()
            if (playlistImportCoordinator.isImportActive()) {
                playlistImportCoordinator.deferVodRefresh("repository_init_during_import")
                return@launch
            }
            if (!isVodCatalogFresh()) {
                runCatching {
                    refreshVodSeriesCatalog(trigger = VodRefreshTrigger.REPOSITORY_INIT, force = false)
                }
            }
        }
    }

    private suspend inline fun <T> withPlaylistImport(label: String, block: suspend () -> T): T {
        playlistImportCoordinator.beginImport(label)
        return try {
            block()
        } finally {
            if (playlistImportCoordinator.endImport(label)) {
                runDeferredPostImportWork()
            }
        }
    }

    private fun runDeferredPostImportWork() {
        vodRepositoryScope.launch {
            val shouldRefresh = playlistImportCoordinator.consumeDeferredVodRefresh() || !isVodCatalogFresh()
            if (shouldRefresh) {
                runCatching {
                    refreshVodSeriesCatalog(trigger = VodRefreshTrigger.REPOSITORY_INIT, force = false)
                }
            }
        }
    }

    private data class VodPlaylistRefreshResult(
        val rawLength: Int = 0,
        val parsedCount: Int = 0,
        val arrayLength: Int = 0,
        val error: String? = null,
        val skippedReason: String? = null
    )

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

    private fun bumpVodCatalogRevision() {
        _vodCatalogRevision.update { it + 1L }
    }

    private suspend fun clearVodCatalogForPlaylist(playlistId: Long) {
        vodStreamDao.clearByPlaylist(playlistId)
        vodCategoryDao.clearByPlaylist(playlistId)
        seriesShowDao.clearByPlaylist(playlistId)
        vodCatalogDiskCache.clear(playlistId)
        clearSeriesSeasonsForPlaylist(playlistId)
        bumpVodCatalogRevision()
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

    override fun groupChannelCounts(): Flow<Map<String, Int>> =
        channelDao.observeGroupChannelCounts().map { rows ->
            rows.associate { it.groupName to it.channelCount }
        }

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
            playlistDao.observeAll(),
            playlistImportCoordinator.importActive
        ) { hist, playlists, importing ->
            if (importing) return@combine emptyList()
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
        programsWindowForChannels(
            channels = epgIds.map { epgId ->
                Channel(
                    id = 0L,
                    number = 0,
                    name = epgId,
                    group = "",
                    logoUrl = null,
                    epgId = epgId,
                    streamUrl = "",
                    playlistId = 0L,
                    isFavorite = false
                )
            },
            start = start,
            end = end
        )

    override suspend fun programsWindowForChannels(channels: List<Channel>, start: Long, end: Long): List<Program> =
        withContext(Dispatchers.IO) {
            if (channels.isEmpty()) return@withContext emptyList()

            val resolver = ensureEpgLinkResolver()
            val xmlTvToPlaylist = linkedMapOf<String, MutableList<String>>()
            val xmlTvIdsToQuery = buildProgrammeQueryIds(channels, resolver, xmlTvToPlaylist)

            if (xmlTvIdsToQuery.isEmpty()) {
                val noEpgId = channels.count { it.epgId.isNullOrBlank() }
                Log.w(
                    EPG_FLOW_TAG,
                    "programsWindowForChannels: no XMLTV ids (${channels.size} channels, $noEpgId without epgId)"
                )
                logEpgMatchDebug(channels, resolver, emptyMap(), start)
                return@withContext emptyList()
            }

            val key = "${_epgDataRevision.value}-${start / 1000}-${end / 1000}-ch${channels.size}-${xmlTvIdsToQuery.hashCode()}"
            val cached = epgCache.get(key)
            val rawPrograms = if (cached != null) {
                cached
            } else {
                val loaded = loadProgramsForXmlTvIds(xmlTvIdsToQuery.toList(), start, end)
                epgCache.put(key, loaded)
                loaded
            }

            val remapped = remapProgramsToPlaylistKeys(rawPrograms, xmlTvToPlaylist)

            val programmesByPlaylistEpgId = remapped.groupBy { it.channelEpgId }
            logEpgMatchDebug(channels, resolver, programmesByPlaylistEpgId, start)
            remapped
        }

    override fun observeProgramsWindowForChannels(
        channels: List<Channel>,
        windowStart: Long,
        windowEnd: Long
    ): Flow<List<Program>> = flow {
        if (channels.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val resolver = ensureEpgLinkResolver()
        val xmlTvToPlaylist = linkedMapOf<String, MutableList<String>>()
        val queryIds = buildProgrammeQueryIds(channels, resolver, xmlTvToPlaylist)
        if (queryIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        programDao.observeWindow(queryIds.toList(), windowStart, windowEnd).collect { rows ->
            val programs = rows.map(::programFromEntity)
            emit(remapProgramsToPlaylistKeys(programs, xmlTvToPlaylist))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchCurrentEpgForChannels(channelIds: List<String>): Int = withContext(epgDispatchers.io) {
        if (channelIds.isEmpty()) return@withContext 0
        val ids = channelIds.mapNotNull { it.toLongOrNull() }.distinct()
        if (ids.isEmpty()) return@withContext 0

        val now = System.currentTimeMillis()
        val windowStart = now - VIEWPORT_EPG_LOOKBACK_MS
        val windowEnd = now + VIEWPORT_EPG_LOOKAHEAD_MS
        val playlistNames = playlistNameMap()
        val entities = channelDao.getByIds(ids)
        if (entities.isEmpty()) return@withContext 0

        val playlistsById = playlistDao.all().associateBy { it.id }
        val channels = entities.map { channelFromEntity(it, playlistNames[it.playlistId]) }
        val eligible = channels.filter { channel ->
            val lastFetch = viewportEpgLastFetch[channel.id] ?: 0L
            now - lastFetch >= VIEWPORT_EPG_COOLDOWN_MS
        }
        if (eligible.isEmpty()) {
            Log.d(EPG_FLOW_TAG, "Viewport EPG fetch skipped — all ${ids.size} channel(s) still in cooldown")
            return@withContext 0
        }

        Log.i(
            EPG_FLOW_TAG,
            "Viewport EPG fast-track for ${eligible.size}/${ids.size} channel(s), " +
                "window [$windowStart, $windowEnd]"
        )

        val semaphore = Semaphore(VIEWPORT_EPG_MAX_CONCURRENT_REQUESTS)
        val fetched = coroutineScope {
            eligible.map { channel ->
                async {
                    semaphore.withPermit {
                        fetchViewportEpgForChannel(
                            channel = channel,
                            playlist = playlistsById[channel.playlistId],
                            windowStart = windowStart,
                            windowEnd = windowEnd
                        )
                    }
                }
            }.awaitAll().flatten()
        }

        if (fetched.isEmpty()) return@withContext 0

        programDao.insertAll(fetched)
        epgCache.clear()
        eligible.forEach { channel -> viewportEpgLastFetch[channel.id] = now }
        _epgDataRevision.update { it + 1 }

        Log.i(
            EPG_FLOW_TAG,
            "Viewport EPG upserted ${fetched.size} programme(s) for ${eligible.size} channel(s)"
        )
        fetched.size
    }

    private suspend fun fetchViewportEpgForChannel(
        channel: Channel,
        playlist: PlaylistEntity?,
        windowStart: Long,
        windowEnd: Long
    ): List<com.grid.tv.data.db.entity.ProgramEntity> {
        if (playlist == null || playlist.type != PlaylistType.XTREAM.name) return emptyList()
        val streamId = resolveXtreamStreamId(channel) ?: return emptyList()
        val server = playlist.xtreamServerUrl ?: return emptyList()
        val user = playlist.xtreamUsername ?: return emptyList()
        val pass = resolveXtreamPassword(playlist) ?: return emptyList()
        val channelEpgId = channel.epgId?.trim()?.takeIf { it.isNotEmpty() } ?: streamId
        val url = buildXtreamApiUrl(
            serverUrl = server,
            username = user,
            password = pass,
            action = "get_short_epg",
            extra = "stream_id=$streamId&limit=$VIEWPORT_EPG_SHORT_LIMIT"
        )
        return runCatching {
            val raw = remoteTextFetcher.fetch(url)
            xtreamParser.parseShortEpg(
                raw = raw,
                channelEpgId = channelEpgId,
                windowStart = windowStart,
                windowEnd = windowEnd
            )
        }.onFailure { error ->
            Log.w(
                EPG_FLOW_TAG,
                "Viewport EPG failed for channel=${channel.name} streamId=$streamId: ${error.message}"
            )
        }.getOrDefault(emptyList())
    }

    private fun resolveXtreamStreamId(channel: Channel): String? {
        Regex("""/live/[^/]+/[^/]+/(\d+)\.""").find(channel.streamUrl)?.groupValues?.getOrNull(1)?.let {
            return it
        }
        val epgId = channel.epgId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return epgId.takeIf { it.all(Char::isDigit) }
    }

    private fun buildProgrammeQueryIds(
        channels: List<Channel>,
        resolver: EpgChannelLinkResolver,
        xmlTvToPlaylist: MutableMap<String, MutableList<String>>
    ): Set<String> {
        val xmlTvIdsToQuery = linkedSetOf<String>()
        channels.forEach { channel ->
            val lookupKeys = channel.programmeLookupKeys()
            if (lookupKeys.isEmpty()) return@forEach
            val link = resolver.resolve(channel.epgId, channel.name)
            val xmlTvIds = linkedSetOf<String>()
            link.xmlTvChannelId?.let { xmlTvIds += it }
            channel.epgId?.trim()?.takeIf { it.isNotEmpty() }?.let { xmlTvIds += it }
            lookupKeys.forEach { key ->
                xmlTvIds += key
                EpgIdNormalizer.normalize(key).takeIf { it.isNotEmpty() }?.let { xmlTvIds += it }
            }
            channel.epgId?.let { EpgIdNormalizer.normalize(it) }?.takeIf { it.isNotEmpty() }?.let {
                xmlTvIds += it
            }
            xmlTvIds.forEach { xmlTvId ->
                xmlTvIdsToQuery += xmlTvId
                val playlistKeys = xmlTvToPlaylist.getOrPut(xmlTvId) { mutableListOf() }
                lookupKeys.forEach { key ->
                    if (key !in playlistKeys) playlistKeys.add(key)
                }
            }
        }
        return xmlTvIdsToQuery
    }

    private fun remapProgramsToPlaylistKeys(
        rawPrograms: List<Program>,
        xmlTvToPlaylist: Map<String, List<String>>
    ): List<Program> =
        rawPrograms.flatMap { program ->
            val playlistEpgIds = xmlTvToPlaylist[program.channelEpgId]
                ?: xmlTvToPlaylist.entries.firstOrNull { (xmlTvId, _) ->
                    xmlTvId.equals(program.channelEpgId, ignoreCase = true) ||
                        EpgIdNormalizer.normalize(xmlTvId) == EpgIdNormalizer.normalize(program.channelEpgId)
                }?.value
                ?: listOf(program.channelEpgId)
            playlistEpgIds.map { playlistEpgId -> program.copy(channelEpgId = playlistEpgId) }
        }

    private fun logImportedProgrammeWindowSample(programs: List<com.grid.tv.data.db.entity.ProgramEntity>, now: Long) {
        if (programs.isEmpty()) return
        val windowStart = now - 90 * 60 * 1000L
        val windowEnd = now + 4 * 60 * 60 * 1000L
        val inWindow = programs.count { it.startTime < windowEnd && it.endTime > windowStart }
        val sample = programs.firstOrNull { it.startTime < windowEnd && it.endTime > windowStart }
        Log.i(
            EPG_FLOW_TAG,
            "EPG import window check: ${inWindow}/${programs.size} programmes overlap guide window " +
                "[$windowStart, $windowEnd]; sampleStart=${sample?.startTime} sampleEnd=${sample?.endTime} " +
                "sampleChannel=${sample?.channelEpgId}"
        )
    }

    private fun programFromEntity(row: com.grid.tv.data.db.entity.ProgramEntity): Program =
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

    private suspend fun loadProgramsForXmlTvIds(
        xmlTvIds: List<String>,
        start: Long,
        end: Long
    ): List<Program> {
        val entities = xmlTvIds.chunked(400).flatMap { chunk ->
            programDao.loadWindow(chunk, start, end)
        }
        val foundLower = entities.map { it.channelEpgId.lowercase() }.toSet()
        val missingLower = xmlTvIds.map { it.lowercase() }.filter { it !in foundLower }.distinct()
        val caseInsensitive = if (missingLower.isEmpty()) {
            emptyList()
        } else {
            missingLower.chunked(400).flatMap { chunk ->
                programDao.loadWindowIgnoreCase(chunk, start, end)
            }
        }
        return (entities + caseInsensitive)
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
    }

    private suspend fun ensureEpgLinkResolver(): EpgChannelLinkResolver {
        val cached = epgLinkResolver
        if (cached != null) return cached
        return rebuildEpgLinkResolver()
    }

    private suspend fun rebuildEpgLinkResolver(): EpgChannelLinkResolver {
        val refs = linkedMapOf<String, XmlTvChannelRef>()
        epgSourceChannelDao.all().forEach { source ->
            refs[source.epgId] = XmlTvChannelRef(source.epgId, source.displayName)
        }
        programDao.distinctChannelEpgIds().forEach { epgId ->
            refs.putIfAbsent(epgId, XmlTvChannelRef(epgId, epgId))
        }
        val learnedMappings = epgLearnedMappingDao.all().associate { mapping ->
            mapping.normalizedOriginalName to mapping.epgId
        }
        return EpgChannelLinkResolver(
            xmlTvChannels = refs.values.toList(),
            learnedMappings = learnedMappings,
            normalizer = channelNameNormalizer
        ).also { epgLinkResolver = it }
    }

    private fun logEpgMatchDebug(
        channels: List<Channel>,
        resolver: EpgChannelLinkResolver,
        programmesByPlaylistEpgId: Map<String, List<Program>>,
        windowStart: Long
    ) {
        val now = System.currentTimeMillis()
        channels.take(10).forEach { channel ->
            val lookupKeys = channel.programmeLookupKeys()
            val tvgId = channel.epgId
            val link = resolver.resolve(tvgId, channel.name)
            val programmes = lookupKeys.flatMap { key ->
                programmesByPlaylistEpgId[key].orEmpty()
            }.distinctBy { it.id }
            val firstProgramStart = programmes.firstOrNull()?.startTime
            Log.i(
                EPG_MATCH_DEBUG_TAG,
                "channel=${channel.name}, tvgId=${tvgId ?: "null"}, matchedId=${link.xmlTvChannelId ?: "NO MATCH"}, " +
                    "matchReason=${link.reason}, lookupKeys=$lookupKeys, programmeCount=${programmes.size}, " +
                    "now=$now, firstProgramStart=$firstProgramStart, windowStart=$windowStart"
            )
        }
    }

    override suspend fun allDistinctEpgIds(): List<String> = withContext(Dispatchers.IO) {
        channelDao.allDistinctEpgIds()
    }

    override fun epgDataRevision(): Flow<Long> = _epgDataRevision.asStateFlow()

    override suspend fun notifyEpgLinksUpdated() = withContext(Dispatchers.IO) {
        epgCache.clear()
        epgLinkResolver = null
        _epgDataRevision.update { it + 1 }
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
        val content = remoteTextFetcher.fetch(normalizedUrl)
        m3uParser.parseHeaderEpgUrl(content)?.let { headerEpgUrl ->
            val playlist = playlistDao.getById(playlistId) ?: return@let
            if (playlist.epgUrl.isNullOrBlank()) {
                playlistDao.update(playlist.copy(epgUrl = headerEpgUrl))
                Log.i(IPTV_REPO_LOG_TAG, "M3U header EPG URL: $headerEpgUrl")
            }
        }
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
        secureCredentialStore.saveM3uUrl(playlistId, normalizedUrl)
    }

    private fun scheduleEpgImportWork(startedAt: Long) {
        Log.i(
            EPG_FLOW_TAG,
            "scheduleEpgImportWork startedAt=$startedAt → priority validation, then EPG, then background scan"
        )
        channelScanGate.get().beginPostImportPriorityWorkflow()
    }

    private suspend fun insertM3uPlaylist(name: String, url: String, epgUrl: String?, refreshHours: Int): Long =
        withPlaylistImport("m3u_insert") {
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
            playlistId
        }

    override suspend fun updateM3uPlaylist(
        playlistId: Long,
        name: String,
        url: String,
        epgUrl: String?,
        refreshHours: Int
    ) = withContext(Dispatchers.IO) {
        withPlaylistImport("m3u_update") {
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
        val epgFromProvider = liveChannels.count { it.epgResolutionSource == "xtream" }
        val epgFromStreamId = liveChannels.count { it.epgResolutionSource == "xtream:stream_id" }
        Log.i(
            EPG_FLOW_TAG,
            "Xtream import epgId stats: epg_channel_id=$epgFromProvider stream_id_fallback=$epgFromStreamId " +
                "total=${liveChannels.size}"
        )

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
        clearVodCatalogForPlaylist(playlistId)

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
    ): Long = withPlaylistImport("xtream_insert") {
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
            vodStreamDao.clearByPlaylist(playlistId)
            vodCategoryDao.clearByPlaylist(playlistId)
            seriesShowDao.clearByPlaylist(playlistId)
            vodCatalogDiskCache.clear(playlistId)
            bumpVodCatalogRevision()
            throw e
        }
        scheduleEpgImportWork(startedAt)
        playlistId
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
        withPlaylistImport("xtream_update") {
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
    }

    private suspend fun insertStalkerPlaylist(
        name: String,
        portalUrl: String,
        macAddress: String,
        refreshHours: Int
    ): Long = withPlaylistImport("stalker_insert") {
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
        playlistId
    }

    override suspend fun addPlaylistFromLocal(name: String, content: String, epgUrl: String?, refreshHours: Int) =
        withContext(Dispatchers.IO) {
            withPlaylistImport("local_m3u") {
                val startedAt = System.currentTimeMillis()
                val resolvedEpgUrl = epgUrl?.takeIf { it.isNotBlank() } ?: m3uParser.parseHeaderEpgUrl(content)
                val playlistId = playlistDao.insert(
                    PlaylistEntity(
                        name = name,
                        url = "local://$name",
                        epgUrl = resolvedEpgUrl,
                        refreshIntervalHours = refreshHours,
                        isLocalFile = true,
                        type = PlaylistType.M3U.name
                    )
                )
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
        vodStreamDao.clearByPlaylist(playlistId)
        vodCategoryDao.clearByPlaylist(playlistId)
        seriesShowDao.clearByPlaylist(playlistId)
        vodCatalogDiskCache.clear(playlistId)
        clearSeriesSeasonsForPlaylist(playlistId)
        bumpVodCatalogRevision()
    }

    override suspend fun refreshEpgNow(): EpgRefreshReport = epgRefreshMutex.withLock {
        withContext(epgDispatchers.io) {
        epgDownloadTracker.setInProgress(true)
        try {
        Log.i(EPG_FLOW_TAG, "refreshEpgNow started on ${Thread.currentThread().name}")
        epgCache.clear()
        epgLinkResolver = null
        val now = System.currentTimeMillis()
        val threshold = now - EPG_PURGE_GRACE_MS
        programDao.purgeOlderThan(threshold)
        Log.i(EPG_FLOW_TAG, "EPG purge: removed programmes with endTime before $threshold")
        val playlists = playlistDao.all()
        Log.i(EPG_FLOW_TAG, "refreshEpgNow: ${playlists.size} playlist(s) in DB")
        val attempts = mutableListOf<EpgFetchAttempt>()
        playlists.forEach { playlist ->
            val epgUrl = playlist.epgUrl
            val resolvedEpgUrl = when {
                !epgUrl.isNullOrBlank() -> epgUrl
                playlist.type == PlaylistType.XTREAM.name -> {
                    val server = playlist.xtreamServerUrl ?: playlist.url
                    val user = playlist.xtreamUsername
                    if (user.isNullOrBlank()) {
                        val skip = EpgFetchAttempt(
                            playlistName = playlist.name,
                            playlistId = playlist.id,
                            endpointKind = "xmltv.php",
                            skippedReason = "missing Xtream username"
                        )
                        attempts += skip
                        Log.w(EPG_FLOW_TAG, "Skipping EPG for ${playlist.name}: missing Xtream username")
                        return@forEach
                    }
                    val pass = resolveXtreamPassword(playlist)
                    if (pass == null) {
                        val skip = EpgFetchAttempt(
                            playlistName = playlist.name,
                            playlistId = playlist.id,
                            endpointKind = "xmltv.php",
                            skippedReason = "missing Xtream password"
                        )
                        attempts += skip
                        Log.w(EPG_FLOW_TAG, "Skipping EPG for ${playlist.name}: missing Xtream password")
                        return@forEach
                    }
                    buildXtreamXmlTvUrl(server, user, pass)
                }
                else -> {
                    val skip = EpgFetchAttempt(
                        playlistName = playlist.name,
                        playlistId = playlist.id,
                        endpointKind = null,
                        skippedReason = "no EPG URL and not Xtream (${playlist.type})"
                    )
                    attempts += skip
                    Log.w(
                        EPG_FLOW_TAG,
                        "Skipping EPG for ${playlist.name}: no epgUrl and type=${playlist.type}"
                    )
                    return@forEach
                }
            }
            val endpointKind = when {
                playlist.type == PlaylistType.XTREAM.name && epgUrl.isNullOrBlank() -> "xmltv.php"
                else -> "playlist EPG URL"
            }
            Log.i(
                EPG_FLOW_TAG,
                "Fetching EPG for ${playlist.name} (id=${playlist.id}) via $endpointKind: $resolvedEpgUrl"
            )
            var attempt = EpgFetchAttempt(
                playlistName = playlist.name,
                playlistId = playlist.id,
                endpointKind = endpointKind,
                url = resolvedEpgUrl
            )
            try {
                when (val fetchOutcome = remoteTextFetcher.fetchEpgXmlTv(
                    rawUrl = resolvedEpgUrl,
                    parser = xmlTvParser,
                    playlistId = playlist.id,
                    playlistName = playlist.name
                )) {
                    is EpgXmlTvFetchOutcome.HttpError -> {
                        attempt = attempt.copy(
                            httpCode = fetchOutcome.httpCode,
                            error = "HTTP ${fetchOutcome.httpCode}"
                        )
                    }
                    is EpgXmlTvFetchOutcome.Success -> {
                        val fetchResult = fetchOutcome.result
                        Log.i(
                            EPG_FLOW_TAG,
                            "EPG disk-backed fetch for ${playlist.name}: http=${fetchResult.httpCode}, " +
                                "cachedBytes=${fetchResult.rawBytes}, " +
                                "channels=${fetchResult.parsed.channelsById.size}, " +
                                "programmes=${fetchResult.parsed.programs.size}"
                        )
                        attempt = attempt.copy(
                            httpCode = fetchResult.httpCode,
                            bytesReceived = fetchResult.rawBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        val parsed = fetchResult.parsed
                        attempt = attempt.copy(
                            channelsParsed = parsed.channelsById.size,
                            programmesParsed = parsed.programs.size
                        )
                        EpgFlowLogger.dbWriteStarted(playlist.id, playlist.name)
                        val sourceKey = "xmltv:${playlist.id}"
                        var channelsStored = 0
                        val sourceChannels = if (parsed.channelsById.isNotEmpty()) {
                            parsed.channelsById.map { (epgId, displayName) ->
                                EpgSourceChannelEntity(
                                    epgId = epgId,
                                    displayName = displayName,
                                    normalizedName = channelNameNormalizer.normalize(displayName),
                                    source = sourceKey,
                                    logoUrl = null,
                                    cachedAt = now
                                )
                            }
                        } else {
                            emptyList()
                        }
                        if (parsed.channelsById.isEmpty()) {
                            Log.w(EPG_FLOW_TAG, "No XMLTV <channel> entries parsed for ${playlist.name}")
                        }
                        var programmesStored = 0
                        if (sourceChannels.isNotEmpty() || parsed.programs.isNotEmpty()) {
                            database.importEpgForPlaylist(
                                playlistId = playlist.id,
                                sourceKey = sourceKey,
                                sourceChannels = sourceChannels,
                                programs = parsed.programs,
                                playlist = playlist,
                                refreshedAt = now
                            )
                            channelsStored = sourceChannels.size
                            programmesStored = parsed.programs.size
                            logImportedProgrammeWindowSample(parsed.programs, now)
                            if (channelsStored > 0) {
                                EpgFlowLogger.channelsImported(playlist.id, playlist.name, channelsStored, sourceKey)
                            }
                            if (programmesStored > 0) {
                                EpgFlowLogger.programsImported(playlist.id, playlist.name, programmesStored)
                            }
                        } else {
                            Log.w(EPG_FLOW_TAG, "No programmes parsed from $resolvedEpgUrl for ${playlist.name}")
                        }
                        EpgFlowLogger.dbWriteCompleted(playlist.id, playlist.name)
                        attempt = attempt.copy(
                            channelsStored = channelsStored,
                            programmesStored = programmesStored
                        )
                    }
                }
            } catch (error: Exception) {
                EpgFlowLogger.importFailed(playlist.id, playlist.name, resolvedEpgUrl, error)
                attempt = attempt.copy(error = error.message ?: error.javaClass.simpleName)
            }
            attempts += attempt
        }
        rebuildEpgLinkResolver()
        epgJobCoordinator.scheduleResolverAfterImport(createdAfter = 0L)
        val programChannelCount = programDao.distinctChannelEpgIds().size
        Log.i(EPG_FLOW_TAG, "EPG link resolver rebuilt; $programChannelCount distinct programme channel ids in DB")
        val sampleChannels = mapChannelEntities(
            channelDao.channelsPage(
                groupName = null,
                search = "",
                onlyFavorites = false,
                profileId = activeProfileId,
                favoriteGroupId = -1L,
                limit = 10,
                offset = 0
            )
        )
        if (sampleChannels.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            val samplePrograms = programsWindowForChannels(
                channels = sampleChannels,
                start = nowMs - 90 * 60 * 1000,
                end = nowMs + 4 * 60 * 60 * 1000
            )
            val matched = samplePrograms.map { it.channelEpgId }.distinct().size
            Log.i(
                EPG_FLOW_TAG,
                "Post-refresh sample: ${sampleChannels.size} channels → $matched with programmes " +
                    "(${samplePrograms.size} programme rows)"
            )
        }
        _epgDataRevision.update { it + 1 }
        EpgFlowLogger.revisionBumped(_epgDataRevision.value)
        val report = EpgRefreshReport(playlistsTotal = playlists.size, attempts = attempts)
        Log.i(
            EPG_FLOW_TAG,
            "refreshEpgNow finished; playlists=${report.playlistsTotal} " +
                "fetches=${report.urlsAttempted} bytes=${report.totalBytesReceived} " +
                "channelsStored=${report.totalChannelsStored} programmesStored=${report.totalProgrammesStored} " +
                "failures=${report.failures.size} epgDataRevision=${_epgDataRevision.value}"
        )
        report
        } finally {
            epgDownloadTracker.setInProgress(false)
        }
        }
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

    override suspend fun ensureVodCatalogLoaded(trigger: VodRefreshTrigger) {
        warmLocalUiCache()
        refreshVodSeriesCatalog(trigger = trigger, force = false)
    }

    override suspend fun warmLocalUiCache() {
        withContext(Dispatchers.IO) {
            ensureDefaultProfile()
            loadSettings()
            ensureVodDiskCacheLoaded()
            publishVodProgressFromDb(trigger = VodRefreshTrigger.REPOSITORY_INIT)
            val channelCount = channelDao.countTotal()
            if (channelCount > 0) {
                channelDao.channelsPage(
                    groupName = null,
                    search = "",
                    onlyFavorites = false,
                    profileId = activeProfileId,
                    favoriteGroupId = -1L,
                    limit = CHANNEL_PAGE_SIZE,
                    offset = 0
                )
            }
            Log.i(
                IPTV_REPO_LOG_TAG,
                "warmLocalUiCache complete profileId=$activeProfileId channels=$channelCount"
            )
        }
    }

    override suspend fun refreshVodSeriesCatalog(
        trigger: VodRefreshTrigger,
        force: Boolean
    ) = withContext(Dispatchers.IO) {
        val callAtMs = System.currentTimeMillis()
        val dbMovies = vodStreamDao.countTotal()
        val dbSeries = seriesShowDao.countTotal()
        Log.i(
            VOD_FLOW_TAG,
            "refreshVodSeriesCatalog requested trigger=$trigger force=$force at=$callAtMs " +
                "dbMovies=$dbMovies dbSeries=$dbSeries " +
                "lastRefreshAgeMs=${if (lastVodRefreshCompletedAtMs > 0L) callAtMs - lastVodRefreshCompletedAtMs else -1L}"
        )

        ensureVodDiskCacheLoaded()

        if (playlistImportCoordinator.isImportActive() && !force) {
            playlistImportCoordinator.deferVodRefresh("import_active trigger=$trigger")
            Log.i(
                VOD_FLOW_TAG,
                "Deferring VOD refresh until import completes trigger=$trigger"
            )
            return@withContext
        }

        activeVodRefresh?.takeIf { it.isActive }?.let { inFlight ->
            Log.i(VOD_FLOW_TAG, "Coalescing duplicate refresh trigger=$trigger with in-flight request")
            inFlight.await()
            return@withContext
        }

        if (!force && isVodCatalogFresh()) {
            Log.i(
                VOD_FLOW_TAG,
                "Skipping network refresh trigger=$trigger — cache fresh within TTL " +
                    "(movies=$dbMovies, series=$dbSeries)"
            )
            publishVodProgressFromDb(trigger = trigger)
            return@withContext
        }

        val completion = CompletableDeferred<Unit>()
        activeVodRefresh = completion
        try {
            vodRefreshMutex.withLock {
                if (!force && isVodCatalogFresh()) {
                    Log.i(VOD_FLOW_TAG, "Skipping network refresh inside lock trigger=$trigger — cache became fresh")
                    publishVodProgressFromDb(trigger = trigger)
                    return@withLock
                }
                executeVodSeriesNetworkRefresh(trigger = trigger, force = force)
            }
            completion.complete(Unit)
        } catch (error: Throwable) {
            completion.completeExceptionally(error)
            throw error
        } finally {
            if (activeVodRefresh === completion) {
                activeVodRefresh = null
            }
        }
    }

    private suspend fun ensureVodDiskCacheLoaded() {
        if (vodDiskCacheLoaded) return
        vodDiskLoadMutex.withLock {
            if (vodDiskCacheLoaded) return
            val dbMovies = vodStreamDao.countTotal()
            val dbSeries = seriesShowDao.countTotal()
            vodDiskCacheLoaded = true
            if (dbMovies > 0 || dbSeries > 0) {
                if (lastVodRefreshCompletedAtMs <= 0L) {
                    lastVodRefreshCompletedAtMs = System.currentTimeMillis()
                }
                Log.i(
                    VOD_FLOW_TAG,
                    "VOD catalog loaded from DB movies=$dbMovies series=$dbSeries"
                )
                publishVodProgressFromDb(trigger = VodRefreshTrigger.REPOSITORY_INIT)
            }
        }
    }

    private suspend fun isVodCatalogFresh(): Boolean {
        if (lastVodRefreshCompletedAtMs <= 0L) return false
        val ageMs = System.currentTimeMillis() - lastVodRefreshCompletedAtMs
        if (ageMs >= VOD_CACHE_TTL_MS) return false
        return vodStreamDao.countTotal() > 0 || seriesShowDao.countTotal() > 0
    }

    private suspend fun publishVodProgressFromDb(trigger: VodRefreshTrigger) {
        val moviesCount = vodStreamDao.countTotal()
        val seriesCount = seriesShowDao.countTotal()
        val progress = VodCatalogProgress(
            moviesLoaded = moviesCount,
            moviesTotal = moviesCount,
            seriesLoaded = seriesCount,
            seriesTotal = seriesCount,
            isLoading = false,
            moviesPhaseFinished = true,
            seriesPhaseFinished = true
        )
        vodCatalogLoading.value = false
        vodCatalogProgress.value = progress
        vodCatalogStatus.value = VodCatalogStatus(
            progress = progress,
            moviesParsedCount = moviesCount,
            seriesParsedCount = seriesCount,
            hasXtreamPlaylist = true
        )
        Log.i(
            VOD_FLOW_TAG,
            "Serving DB-backed catalog trigger=$trigger movies=$moviesCount series=$seriesCount " +
                "cacheAgeMs=${System.currentTimeMillis() - lastVodRefreshCompletedAtMs}"
        )
    }

    private suspend fun executeVodSeriesNetworkRefresh(trigger: VodRefreshTrigger, force: Boolean) {
        try {
            loadSettings()
            val playlists = playlistDao.all().filter { it.type == PlaylistType.XTREAM.name }
            if (playlists.isEmpty()) {
                Log.w(VOD_FLOW_TAG, "No Xtream playlists configured — VOD catalog unavailable trigger=$trigger")
                val progress = VodCatalogProgress(
                    moviesPhaseFinished = true,
                    seriesPhaseFinished = true
                )
                vodCatalogLoading.value = false
                vodCatalogProgress.value = progress
                vodCatalogStatus.value = VodCatalogStatus(
                    progress = progress,
                    hasXtreamPlaylist = false
                )
                return
            }

            vodCatalogLoading.value = true
            var moviesLoaded = 0
            var moviesTotal = 0
            var seriesLoaded = 0
            var seriesTotal = 0
            var moviesError: String? = null
            var seriesError: String? = null
            var moviesRawLength = 0
            var moviesParsedCount = 0
            var seriesRawLength = 0
            var seriesParsedCount = 0

            fun publishProgress(
                isLoading: Boolean,
                moviesPhaseFinished: Boolean = vodCatalogProgress.value.moviesPhaseFinished,
                seriesPhaseFinished: Boolean = vodCatalogProgress.value.seriesPhaseFinished
            ) {
                val progress = VodCatalogProgress(
                    moviesLoaded = moviesLoaded,
                    moviesTotal = moviesTotal,
                    seriesLoaded = seriesLoaded,
                    seriesTotal = seriesTotal,
                    isLoading = isLoading,
                    moviesPhaseFinished = moviesPhaseFinished,
                    seriesPhaseFinished = seriesPhaseFinished
                )
                vodCatalogProgress.value = progress
                vodCatalogStatus.value = VodCatalogStatus(
                    progress = progress,
                    moviesError = moviesError,
                    seriesError = seriesError,
                    moviesRawLength = moviesRawLength,
                    moviesParsedCount = moviesParsedCount,
                    seriesRawLength = seriesRawLength,
                    seriesParsedCount = seriesParsedCount,
                    hasXtreamPlaylist = true
                )
            }

            publishProgress(isLoading = true)

            playlists.forEach { playlist ->
                val result = refreshVodCatalogForPlaylist(
                    playlist = playlist,
                    trigger = trigger,
                    onBatchInserted = { parsedSoFar, total ->
                        moviesLoaded = parsedSoFar
                        moviesTotal = total
                        moviesParsedCount = parsedSoFar
                        publishProgress(isLoading = true)
                    }
                )
                moviesTotal += result.arrayLength
                moviesLoaded = result.parsedCount
                moviesRawLength += result.rawLength
                moviesParsedCount += result.parsedCount
                result.error?.let { moviesError = it }
                result.skippedReason?.let { moviesError = it }
            }
            publishProgress(isLoading = true, moviesPhaseFinished = true)
            Log.i(
                VOD_FLOW_TAG,
                "Movies phase complete trigger=$trigger parsed=$moviesParsedCount rawBytes=$moviesRawLength " +
                    "dbCount=${vodStreamDao.countTotal()}"
            )

            playlists.forEach { playlist ->
                val result = refreshSeriesCatalogForPlaylist(
                    playlist = playlist,
                    trigger = trigger,
                    onBatchInserted = { parsedSoFar, total ->
                        seriesLoaded = parsedSoFar
                        seriesTotal = total
                        seriesParsedCount = parsedSoFar
                        publishProgress(isLoading = true, moviesPhaseFinished = true)
                    }
                )
                seriesTotal += result.arrayLength
                seriesLoaded = result.parsedCount
                seriesRawLength += result.rawLength
                seriesParsedCount += result.parsedCount
                result.error?.let { seriesError = it }
                result.skippedReason?.let { seriesError = it }
            }
            publishProgress(
                isLoading = false,
                moviesPhaseFinished = true,
                seriesPhaseFinished = true
            )
            lastVodRefreshCompletedAtMs = System.currentTimeMillis()
            bumpVodCatalogRevision()
            Log.i(
                VOD_FLOW_TAG,
                "VOD pipeline complete trigger=$trigger force=$force movies=$moviesParsedCount " +
                    "series=$seriesParsedCount dbMovies=${vodStreamDao.countTotal()}"
            )
        } finally {
            vodCatalogLoading.value = false
        }
    }

    private fun resolvePlaylistServerUrl(playlist: PlaylistEntity): String {
        val stored = playlist.xtreamServerUrl?.takeIf { it.isNotBlank() } ?: playlist.url
        return stored.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing server URL for ${playlist.name}")
    }

    private suspend fun resolveVodFetchCredentials(
        playlist: PlaylistEntity
    ): Triple<String, String, String>? {
        val user = playlist.xtreamUsername?.takeIf { it.isNotBlank() } ?: return null
        val pass = resolveXtreamPassword(playlist)?.takeIf { it.isNotBlank() } ?: return null
        val storedServer = runCatching { resolvePlaylistServerUrl(playlist) }.getOrElse {
            Log.w(VOD_FLOW_TAG, "VOD playlist ${playlist.id}: ${it.message}")
            return null
        }
        return runCatching {
            val authResult = remoteTextFetcher.fetchDetailed(
                buildXtreamApiUrl(storedServer, user, pass)
            )
            Log.i(
                VOD_FLOW_TAG,
                "VOD auth preflight playlist=${playlist.id} http=${authResult.httpCode} " +
                    "bytes=${authResult.rawBytes}"
            )
            if (!xtreamParser.isAuthSuccessful(authResult.body)) {
                Log.w(VOD_FLOW_TAG, "VOD auth preflight rejected for playlist ${playlist.id}")
                return@runCatching Triple(storedServer, user, pass)
            }
            val auth = xtreamParser.parseAuth(authResult.body)
            val resolved = resolveXtreamServerUrl(storedServer, auth)
            if (resolved != storedServer) {
                Log.i(
                    VOD_FLOW_TAG,
                    "VOD resolved server playlist=${playlist.id}: $storedServer -> $resolved"
                )
            }
            Triple(resolved, user, pass)
        }.getOrElse { error ->
            Log.w(
                VOD_FLOW_TAG,
                "VOD auth preflight failed playlist=${playlist.id}: ${error.message} — using stored URL"
            )
            Triple(storedServer, user, pass)
        }
    }

    private suspend fun refreshVodCatalogForPlaylist(
        playlist: PlaylistEntity,
        trigger: VodRefreshTrigger,
        onBatchInserted: (parsedSoFar: Int, arrayLength: Int) -> Unit = { _, _ -> }
    ): VodPlaylistRefreshResult {
        val credentials = resolveVodFetchCredentials(playlist)
        if (credentials == null) {
            val reason = when {
                playlist.xtreamUsername.isNullOrBlank() ->
                    "Missing Xtream username for ${playlist.name}"
                else -> "Missing Xtream password for ${playlist.name}"
            }
            Log.w(VOD_FLOW_TAG, "Skipping VOD playlist ${playlist.id}: $reason")
            return VodPlaylistRefreshResult(skippedReason = reason)
        }
        val (server, user, pass) = credentials
        val vodUrl = buildXtreamApiUrl(server, user, pass, action = "get_vod_streams")
        Log.i(
            VOD_FLOW_TAG,
            "Starting get_vod_streams playlist=${playlist.id} trigger=$trigger at=${System.currentTimeMillis()}"
        )
        return runCatching {
            val fetchResult = remoteTextFetcher.fetchDetailed(vodUrl)
            val vodRaw = fetchResult.body
            Log.i(
                VOD_FLOW_TAG,
                "VOD fetch playlist=${playlist.id} action=get_vod_streams trigger=$trigger " +
                    "http=${fetchResult.httpCode} rawBytes=${fetchResult.rawBytes} " +
                    "decodedChars=${vodRaw.length} urlHost=${runCatching { URI(server).host }.getOrNull()}"
            )
            if (vodRaw.length <= 500) {
                Log.d(VOD_FLOW_TAG, "VOD raw preview playlist=${playlist.id}: ${vodRaw.take(400)}")
            } else {
                Log.d(
                    VOD_FLOW_TAG,
                    "VOD raw preview playlist=${playlist.id}: ${vodRaw.take(200)}…${vodRaw.takeLast(80)}"
                )
            }

            val arrayLength = xtreamParser.parseVodArrayLength(vodRaw)
            Log.i(VOD_FLOW_TAG, "VOD parse arrayLength=$arrayLength playlist=${playlist.id}")
            val diagnosis = xtreamParser.diagnoseVodResponse(vodRaw)
            val priorCount = vodStreamDao.countByPlaylist(playlist.id)
            var parsedCount = 0

            if (arrayLength > 0) {
                database.replaceVodStreamsForPlaylist(playlist.id) {
                    xtreamParser.parseVodBatched(
                        raw = vodRaw,
                        username = user,
                        password = pass,
                        serverUrl = server,
                        playlistId = playlist.id,
                        batchSize = CHANNEL_INSERT_CHUNK
                    ) { batch ->
                        if (batch.isEmpty()) return@parseVodBatched
                        if (parsedCount == 0) {
                            batch.take(5).forEachIndexed { index, item ->
                                Log.d(
                                    VOD_FLOW_TAG,
                                    "VOD parsed[$index] playlist=${playlist.id} id=${item.streamId} " +
                                        "title=${item.title.take(48)} category=${item.categoryId}"
                                )
                            }
                        }
                        vodStreamDao.insertAll(batch.map { it.toEntity() })
                        parsedCount += batch.size
                        onBatchInserted(parsedCount, arrayLength)
                    }
                }
                if (parsedCount > 0) {
                    bumpVodCatalogRevision()
                }
            } else if (diagnosis != null) {
                Log.w(
                    VOD_FLOW_TAG,
                    "VOD unusable response playlist=${playlist.id} trigger=$trigger diagnosis=$diagnosis — " +
                        "keeping prior DB rows ($priorCount items)"
                )
            } else if (vodRaw.isBlank()) {
                Log.w(VOD_FLOW_TAG, "VOD empty HTTP body playlist=${playlist.id}")
                if (priorCount == 0) {
                    vodStreamDao.clearByPlaylist(playlist.id)
                    bumpVodCatalogRevision()
                }
            } else if (priorCount == 0) {
                vodStreamDao.clearByPlaylist(playlist.id)
                bumpVodCatalogRevision()
            }

            runCatching {
                Log.i(
                    VOD_FLOW_TAG,
                    "Starting get_vod_categories playlist=${playlist.id} trigger=$trigger " +
                        "at=${System.currentTimeMillis()}"
                )
                val vodCategoriesRaw = remoteTextFetcher.fetch(
                    buildXtreamApiUrl(server, user, pass, action = "get_vod_categories")
                )
                val categories = xtreamParser.parseVodCategories(vodCategoriesRaw, playlist.id)
                Log.i(
                    VOD_FLOW_TAG,
                    "VOD categories playlist=${playlist.id} trigger=$trigger count=${categories.size}"
                )
                if (categories.isNotEmpty()) {
                    vodCategoryDao.clearByPlaylist(playlist.id)
                    vodCategoryDao.insertAll(categories.map { it.toEntity() })
                    bumpVodCatalogRevision()
                }
            }.onFailure { categoryError ->
                Log.w(
                    VOD_FLOW_TAG,
                    "VOD categories fetch failed playlist=${playlist.id} trigger=$trigger — keeping prior categories",
                    categoryError
                )
            }

            Log.i(
                VOD_FLOW_TAG,
                "VOD commit playlist=${playlist.id} finalCount=$parsedCount arrayLength=$arrayLength"
            )
            VodPlaylistRefreshResult(
                rawLength = fetchResult.rawBytes,
                parsedCount = parsedCount,
                arrayLength = arrayLength,
                error = when {
                    parsedCount > 0 -> null
                    arrayLength > 0 ->
                        "Parsed 0 of $arrayLength movie entries for ${playlist.name}"
                    diagnosis != null -> diagnosis
                    vodRaw.isBlank() ->
                        "Provider returned an empty response for ${playlist.name}. Check your connection and server URL."
                    else -> null
                }
            )
        }.getOrElse { error ->
            val message = error.message ?: error.javaClass.simpleName
            Log.w(
                VOD_FLOW_TAG,
                "VOD catalog refresh failed for playlist ${playlist.id} (${playlist.name}): $message",
                error
            )
            VodPlaylistRefreshResult(error = message)
        }
    }

    private suspend fun refreshSeriesCatalogForPlaylist(
        playlist: PlaylistEntity,
        trigger: VodRefreshTrigger,
        onBatchInserted: (parsedSoFar: Int, arrayLength: Int) -> Unit = { _, _ -> }
    ): VodPlaylistRefreshResult {
        val credentials = resolveVodFetchCredentials(playlist)
        if (credentials == null) {
            val reason = when {
                playlist.xtreamUsername.isNullOrBlank() ->
                    "Missing Xtream username for ${playlist.name}"
                else -> "Missing Xtream password for ${playlist.name}"
            }
            Log.w(VOD_FLOW_TAG, "Skipping series playlist ${playlist.id}: $reason")
            return VodPlaylistRefreshResult(skippedReason = reason)
        }
        val (server, user, pass) = credentials
        return runCatching {
            val seriesUrl = buildXtreamApiUrl(server, user, pass, action = "get_series")
            Log.i(
                VOD_FLOW_TAG,
                "Starting get_series playlist=${playlist.id} trigger=$trigger at=${System.currentTimeMillis()}"
            )
            val fetchResult = remoteTextFetcher.fetchDetailed(seriesUrl)
            val seriesRaw = fetchResult.body
            Log.i(
                VOD_FLOW_TAG,
                "Series fetch playlist=${playlist.id} action=get_series trigger=$trigger " +
                    "http=${fetchResult.httpCode} rawBytes=${fetchResult.rawBytes} decodedChars=${seriesRaw.length}"
            )
            if (seriesRaw.length < 500) {
                Log.d(VOD_FLOW_TAG, "Series raw preview playlist=${playlist.id}: ${seriesRaw.take(300)}")
            }

            val arrayLength = xtreamParser.parseSeriesArrayLength(seriesRaw)
            Log.i(VOD_FLOW_TAG, "Series parse arrayLength=$arrayLength playlist=${playlist.id}")

            val priorCount = seriesShowDao.countByPlaylist(playlist.id)
            var parsedCount = 0

            if (arrayLength > 0) {
                clearSeriesSeasonsForPlaylist(playlist.id)
                database.replaceSeriesShowsForPlaylist(playlist.id) {
                    xtreamParser.parseSeriesBatched(
                        raw = seriesRaw,
                        playlistId = playlist.id,
                        batchSize = CHANNEL_INSERT_CHUNK
                    ) { batch ->
                        if (batch.isEmpty()) return@parseSeriesBatched
                        if (parsedCount == 0) {
                            batch.take(5).forEachIndexed { index, show ->
                                Log.d(
                                    VOD_FLOW_TAG,
                                    "Series parsed[$index] playlist=${playlist.id} id=${show.id} " +
                                        "name=${show.name.take(48)} category=${show.categoryId}"
                                )
                            }
                        }
                        seriesShowDao.insertAll(batch.map { it.toEntity() })
                        parsedCount += batch.size
                        onBatchInserted(parsedCount, arrayLength)
                    }
                }
                if (parsedCount > 0) {
                    bumpVodCatalogRevision()
                }
            } else {
                if (priorCount == 0) {
                    Log.w(
                        VOD_FLOW_TAG,
                        "Series empty response playlist=${playlist.id} trigger=$trigger — no prior DB rows"
                    )
                    seriesShowDao.clearByPlaylist(playlist.id)
                    bumpVodCatalogRevision()
                } else {
                    Log.w(
                        VOD_FLOW_TAG,
                        "Series empty response playlist=${playlist.id} trigger=$trigger — " +
                            "preserving $priorCount DB rows"
                    )
                }
            }

            Log.i(
                VOD_FLOW_TAG,
                "Series commit playlist=${playlist.id} finalCount=$parsedCount arrayLength=$arrayLength"
            )
            VodPlaylistRefreshResult(
                rawLength = fetchResult.rawBytes,
                parsedCount = parsedCount,
                arrayLength = arrayLength,
                error = if (arrayLength > 0 && parsedCount == 0) {
                    "Parsed 0 of $arrayLength series entries for ${playlist.name}"
                } else {
                    null
                }
            )
        }.getOrElse { error ->
            val message = error.message ?: error.javaClass.simpleName
            Log.w(
                VOD_FLOW_TAG,
                "Series catalog refresh failed for playlist ${playlist.id} (${playlist.name}): $message",
                error
            )
            VodPlaylistRefreshResult(error = message)
        }
    }

    override fun vodCatalogLoading(): Flow<Boolean> = vodCatalogLoading

    override fun vodCatalogProgress(): Flow<VodCatalogProgress> = vodCatalogProgress

    override fun vodCatalogStatus(): Flow<VodCatalogStatus> = vodCatalogStatus

    override fun vodCatalogRevision(): Flow<Long> = _vodCatalogRevision

    override fun vodStreamCount(): Flow<Int> = vodStreamDao.observeTotalCount()

    override fun seriesShowCount(): Flow<Int> = seriesShowDao.observeTotalCount()

    override fun vodCategories(): Flow<List<VodCategory>> =
        vodCategoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun vodPage(
        categoryId: String?,
        search: String,
        limit: Int,
        offset: Int
    ): List<VodItem> = withContext(Dispatchers.IO) {
        vodStreamDao.vodPage(categoryId, search.trim(), limit, offset).map { it.toDomain() }
    }

    override fun vodMoviesPaging(categoryId: String?, search: String): Flow<PagingData<VodItem>> {
        val trimmedSearch = search.trim()
        return Pager(
            config = PagingConfig(
                pageSize = VOD_PAGING_PAGE_SIZE,
                prefetchDistance = VOD_PAGING_PREFETCH,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { vodStreamDao.vodPagingSource(categoryId, trimmedSearch) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun vodFilteredCount(categoryId: String?, search: String): Int =
        withContext(Dispatchers.IO) {
            vodStreamDao.countFiltered(categoryId, search.trim())
        }

    override suspend fun findVodStream(playlistId: Long, streamId: Long): VodItem? =
        withContext(Dispatchers.IO) {
            vodStreamDao.findByStreamId(playlistId, streamId)?.toDomain()
        }

    override suspend fun vodRecent(limit: Int): List<VodItem> = withContext(Dispatchers.IO) {
        vodStreamDao.recent(limit).map { it.toDomain() }
    }

    override suspend fun vodSampleForRecommendations(sampleSize: Int): List<VodItem> =
        withContext(Dispatchers.IO) {
            val total = vodStreamDao.countTotal()
            if (total == 0) return@withContext emptyList()
            val target = sampleSize.coerceAtMost(total)
            val stride = (total / target).coerceAtLeast(1)
            buildList {
                var offset = 0
                while (size < target && offset < total) {
                    addAll(
                        vodStreamDao.samplePage(limit = 1, offset = offset).map { it.toDomain() }
                    )
                    offset += stride
                }
            }.take(target)
        }

    override suspend fun loadMovieBrowseRows(itemsPerRow: Int, maxRows: Int): List<VodBrowseRow> =
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<VodBrowseRow>()
            vodStreamDao.recent(itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                rows += VodBrowseRow("recent", "Recently Added", movies = it.map { e -> e.toDomain() })
            }
            vodStreamDao.topRated(itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                rows += VodBrowseRow("top_imdb", "Top IMDB", movies = it.map { e -> e.toDomain() })
            }
            vodStreamDao.fourK(itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                rows += VodBrowseRow("4k", "4K Movies", movies = it.map { e -> e.toDomain() })
            }
            vodCategoryDao.all().distinctBy { it.categoryId }
                .sortedBy { it.name.lowercase() }
                .forEach { category ->
                    val items = vodStreamDao.byCategory(category.categoryId, itemsPerRow)
                    if (items.isNotEmpty()) {
                        rows += VodBrowseRow(
                            "cat_${category.categoryId}",
                            category.name,
                            movies = items.map { it.toDomain() }
                        )
                    }
                }
            rows.filter { !it.isEmpty }.distinctBy { it.id }.take(maxRows)
        }

    override suspend fun seriesPage(
        category: String,
        search: String,
        limit: Int,
        offset: Int
    ): List<SeriesShow> = withContext(Dispatchers.IO) {
        seriesShowDao.seriesPage(category, search.trim(), limit, offset).map { it.toDomain() }
    }

    override suspend fun seriesFilteredCount(category: String, search: String): Int =
        withContext(Dispatchers.IO) {
            seriesShowDao.countFiltered(category, search.trim())
        }

    override suspend fun findSeriesShow(seriesId: Long): SeriesShow? = withContext(Dispatchers.IO) {
        seriesShowDao.findBySeriesIdGlobal(seriesId)?.toDomain()
    }

    override suspend fun loadSeriesBrowseRows(itemsPerRow: Int, maxRows: Int): List<VodBrowseRow> =
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<VodBrowseRow>()
            seriesShowDao.recent(itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                rows += VodBrowseRow("all", "All Series", series = it.map { e -> e.toDomain() })
            }
            seriesShowDao.fourK(itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                rows += VodBrowseRow("4k", "4K Series", series = it.map { e -> e.toDomain() })
            }
            val categoryLabels = seriesShowDao.distinctCategoryIds()
            categoryLabels.forEach { label ->
                val items = seriesShowDao.byCategory(label, itemsPerRow)
                if (items.isNotEmpty()) {
                    rows += VodBrowseRow("cat_$label", label, series = items.map { it.toDomain() })
                }
            }
            rows.filter { !it.isEmpty }.distinctBy { it.id }.take(maxRows)
        }

    override suspend fun searchVod(query: String, limit: Int): List<VodItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        vodStreamDao.search(query.trim(), limit).map { it.toDomain() }
    }

    override suspend fun searchSeriesShows(query: String, limit: Int): List<SeriesShow> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            seriesShowDao.search(query.trim(), limit).map { it.toDomain() }
        }

    override suspend fun distinctSeriesCategories(): List<String> = withContext(Dispatchers.IO) {
        seriesShowDao.distinctCategoryIds()
    }

    override suspend fun seriesSeasons(seriesId: Long): List<SeriesSeason> {
        val show = seriesShowDao.findBySeriesIdGlobal(seriesId) ?: return emptyList()
        val playlistId = show.playlistId
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

    override suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        ensureDefaultProfile()
        val db = profileSettingsDao.get(activeProfileId) ?: return@withContext cachedSettings.also {
            appHttpClient.applySettings(cachedSettings)
        }
        return@withContext AppSettings(
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
            connectionTimeoutSeconds = db.connectionTimeoutSeconds.let { stored ->
                // Migration 12_13 defaulted to 10s — too short for large playlist/VOD downloads.
                if (stored <= 10) DEFAULT_CONNECTION_TIMEOUT_SECONDS else stored
            },
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

    override suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        ensureDefaultProfile()
        cachedSettings = settings
        appHttpClient.applySettings(settings)
        val existing = profileSettingsDao.get(activeProfileId)
        val guideGroupsEncoded = resolveGuideGroupsForSave(settings, existing)
        val guideConfigured = resolveGuideConfiguredForSave(settings, existing)
        profileSettingsDao.upsert(
            ProfileSettingsEntity(
                profileId = activeProfileId,
                preferredAudioLanguage = settings.preferredAudioLanguage,
                epgRowHeight = settings.epgRowHeight.name,
                streamRetries = settings.streamRetries,
                previewEnabled = settings.miniPlayerAudioEnabled,
                gameLockEnabled = existing?.gameLockEnabled ?: false,
                lastSleepTimer = settings.sleepTimerMinutes,
                recordingStoragePath = existing?.recordingStoragePath,
                lastSeenVersion = existing?.lastSeenVersion,
                sleepTimerMinutes = settings.sleepTimerMinutes,
                hideAdultContent = settings.hideAdultContent,
                sleepTimerAutoEnabled = settings.sleepTimerAutoEnabled,
                autoScanEnabled = settings.autoScanEnabled,
                scanIntervalMinutes = settings.scanIntervalMinutes,
                concurrentChecks = settings.concurrentChecks,
                scanOnMetered = settings.scanOnMetered,
                lastFullScanAt = existing?.lastFullScanAt,
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
                guideChannelGroups = guideGroupsEncoded,
                guideFiltersConfigured = guideConfigured
            )
        )
        cachedSettings = cachedSettings.copy(
            guideChannelGroups = GuideChannelFilter.decode(guideGroupsEncoded),
            guideFiltersConfigured = guideConfigured
        )
    }

    override suspend fun saveGuideChannelFilter(groups: Set<String>, configured: Boolean) = withContext(Dispatchers.IO) {
        ensureDefaultProfile()
        val existing = profileSettingsDao.get(activeProfileId) ?: ProfileSettingsEntity(profileId = activeProfileId)
        val encoded = GuideChannelFilter.encode(groups)
        profileSettingsDao.upsert(
            existing.copy(
                guideChannelGroups = encoded,
                guideFiltersConfigured = configured
            )
        )
        cachedSettings = cachedSettings.copy(
            guideChannelGroups = groups,
            guideFiltersConfigured = configured
        )
    }

    /** Avoid wiping a persisted guide filter when unrelated settings are saved from stale UI state. */
    private fun resolveGuideGroupsForSave(
        settings: AppSettings,
        existing: ProfileSettingsEntity?
    ): String = when {
        settings.guideChannelGroups.isNotEmpty() ->
            GuideChannelFilter.encode(settings.guideChannelGroups)
        settings.guideFiltersConfigured ->
            GuideChannelFilter.encode(settings.guideChannelGroups)
        !existing?.guideChannelGroups.isNullOrBlank() ->
            existing.guideChannelGroups
        else ->
            GuideChannelFilter.encode(settings.guideChannelGroups)
    }

    private fun resolveGuideConfiguredForSave(
        settings: AppSettings,
        existing: ProfileSettingsEntity?
    ): Boolean = when {
        settings.guideFiltersConfigured -> true
        settings.guideChannelGroups.isNotEmpty() -> true
        existing?.guideFiltersConfigured == true -> true
        else -> false
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
        vodStreamDao.clearAll()
        vodCategoryDao.clearAll()
        seriesShowDao.clearAll()
        vodCatalogDiskCache.clearAll()
        seriesSeasonsCache.clear()
        _vodCatalogRevision.value = 0L
        activeProfileId = 0L
        cachedSettings = AppSettings()
        guestSessionPreferences.clearGuestSession()
        saveSettings(AppSettings())
    }
}
