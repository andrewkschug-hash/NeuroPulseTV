package com.grid.tv.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.FavoriteDao
import com.grid.tv.data.db.dao.FavoriteGroupDao
import com.grid.tv.data.db.dao.PlaylistDao
import com.grid.tv.data.db.dao.PlaylistFavoriteGroupDao
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
import com.grid.tv.data.db.entity.PlaylistFavoriteGroupEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import com.grid.tv.data.db.entity.ProfileSettingsEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.UserProfileEntity
import com.grid.tv.data.cache.VodCatalogDiskCache
import com.grid.tv.data.cache.VodCatalogCacheManifestStore
import com.grid.tv.data.cache.sha256FileHex
import com.grid.tv.data.cache.vodPlaylistCacheVersionKey
import com.grid.tv.data.io.DiskIoSerialExecutor
import com.grid.tv.data.db.dao.SeriesCategoryDao
import com.grid.tv.data.db.dao.SeriesShowDao
import com.grid.tv.data.db.dao.VodCategoryDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.withSearchTitle
import com.grid.tv.data.db.AppDatabase
import com.grid.tv.data.db.mapper.toDomain
import com.grid.tv.data.db.mapper.toEntity
import com.grid.tv.data.db.entity.SeriesCategoryEntity
import com.grid.tv.data.db.entity.VodCategoryEntity
import com.grid.tv.data.db.mapper.toSeriesCategoryEntity
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
import com.grid.tv.domain.session.PlaylistContext
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
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.feature.startup.StartupDependencyProbe
import com.grid.tv.feature.startup.StartupProfiler
import com.grid.tv.feature.startup.StartupTierPolicy
import com.grid.tv.feature.startup.StartupTiming
import com.grid.tv.feature.startup.StartupTrace
import com.grid.tv.util.TvImageSizing
import com.grid.tv.feature.startup.PersistedCatalogCounts
import com.grid.tv.feature.startup.CachedCatalogCounts
import com.grid.tv.feature.startup.StartupCatalogCountsStore
import com.grid.tv.feature.startup.StartupGroupMetadataStore
import com.grid.tv.feature.guide.GroupsTrace
import com.grid.tv.feature.guide.SmartGroupService
import com.grid.tv.util.DEFAULT_CONNECTION_TIMEOUT_SECONDS
import com.grid.tv.util.JsonParseMetrics
import com.grid.tv.util.GUEST_PROFILE_AVATAR_COLOR
import com.grid.tv.util.MAX_HOUSEHOLD_PROFILES
import com.grid.tv.util.sanitizeProfileAvatarColorHex
import com.grid.tv.util.runVodPipelineCatching
import com.grid.tv.util.PerformanceTelemetryTracker
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
import com.grid.tv.domain.model.ChannelGroupNavigationMode
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
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.StreamHealth
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryGuards
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.categoryBrowseRowId
import com.grid.tv.domain.model.categoryKey
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.SeriesCatalogHydrationState
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.data.db.mapper.toSeriesDetail
import com.grid.tv.data.db.mapper.toEpisodeEntities
import com.grid.tv.data.paging.VodUnifiedSearchPagingSource
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodSearchEntry
import com.grid.tv.domain.model.WatchHistory
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.grid.tv.feature.epg.EpgBlockCache
import com.grid.tv.feature.epg.EpgProgramTextDecoder
import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.util.cache.BoundedMemoryCache
import com.grid.tv.util.cache.CacheSizeEstimators
import com.grid.tv.feature.epg.EpgCoroutineDispatchers
import com.grid.tv.feature.epg.EpgFlowLogger
import com.grid.tv.feature.epg.EpgJobCoordinator
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.parental.ProfileAccessGuard
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.data.db.entity.FavoriteGroupEntity
import com.grid.tv.feature.backup.GridBackupManager
import com.grid.tv.feature.health.StreamHealthEngine
import com.grid.tv.feature.recommendation.RecommendationEngine
import com.grid.tv.feature.recommendation.WatchStat
import com.grid.tv.feature.tivimate.TiviMateImporter
import com.grid.tv.feature.scanner.ChannelScanGate
import com.grid.tv.feature.scanner.EpgDownloadTracker
import com.grid.tv.feature.search.SearchTitleNormalizer
import com.grid.tv.feature.vod.SeriesEpisodeTitleNormalizer
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.grid.tv.data.paging.ChannelBrowserPagingSource
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import java.net.URLEncoder
import java.net.URI
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val playlistDao: PlaylistDao,
    private val playlistFavoriteGroupDao: PlaylistFavoriteGroupDao,
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
    private val seriesCategoryDao: SeriesCategoryDao,
    private val vodCatalogDiskCache: VodCatalogDiskCache,
    private val vodCatalogCacheManifestStore: VodCatalogCacheManifestStore,
    private val catalogHydrationGuard: com.grid.tv.data.catalog.CatalogHydrationGuard,
    private val epgDownloadTracker: EpgDownloadTracker,
    private val epgDispatchers: EpgCoroutineDispatchers,
    private val appCacheRegistry: AppCacheRegistry,
    private val startupCatalogCountsStore: StartupCatalogCountsStore,
    private val startupGroupMetadataStore: StartupGroupMetadataStore,
    private val smartGroupService: SmartGroupService,
    private val seriesCatalogHydrationStore: com.grid.tv.feature.vod.SeriesCatalogHydrationStore,
    private val startupSafety: com.grid.tv.feature.startup.StartupSafety,
    private val startupDiskQueue: com.grid.tv.feature.startup.StartupDiskQueue,
    private val diskIoSerialExecutor: DiskIoSerialExecutor,
    private val playlistContext: PlaylistContext
) : IptvRepository {

    @Volatile
    private var repositoryBootstrapComplete = false

    init {
        StartupDependencyProbe.traceInjectedInit("IptvRepositoryImpl.init") {
            StartupTiming.log("IptvRepositoryImpl — @Inject constructor resolved, entering init blocks")
            StartupTiming.trace("IptvRepositoryImpl.registerVodLoadFlusher") {
                startupSafety.registerVodLoadFlusher { trigger -> loadVodStreamed(trigger) }
            }
        }
    }

    companion object {
        private const val CONNECT_ERROR = "Login or URL invalid"
        private const val IPTV_REPO_LOG_TAG = "IptvRepository"
        private const val EPG_MATCH_DEBUG_TAG = "EPG Match Debug"
        private const val EPG_FLOW_TAG = "EpgFlow"
        private const val VOD_FLOW_TAG = "VodCatalogPipeline"
        private const val CATEGORY_KEY_LOG_TAG = "CATEGORY_KEY"
        private const val CHANNEL_INSERT_CHUNK = 400
        const val CHANNEL_PAGE_SIZE = 200
        const val VOD_PAGING_PAGE_SIZE = 60
        const val VOD_PAGING_PREFETCH = 20
        const val CHANNEL_BROWSER_PAGE_SIZE = 48
        const val CHANNEL_BROWSER_PREFETCH = 12
        private const val SPORTS_FILTER_EMPTY_SENTINEL = "__none__"
        private const val GUEST_SCRATCH_PROFILE_NAME = "Guest"
        /** Serve in-memory/disk cache without network for this long unless [force] refresh. */
        private const val VOD_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        private const val INGEST_REVISION_EVERY_BATCHES = 4
        /** Skip cold-start VOD network ingest when the DB already holds at least this many rows. */
        private const val STARTUP_VOD_SKIP_MIN_ROWS = 500
        private const val VIEWPORT_EPG_LOOKBACK_MS = 30L * 60L * 1000L
        private const val VIEWPORT_EPG_LOOKAHEAD_MS = 4L * 60L * 60L * 1000L
        private const val VIEWPORT_EPG_COOLDOWN_MS = 90L * 1000L
        private const val VIEWPORT_EPG_ERROR_COOLDOWN_MS = 5L * 60L * 1000L
        private const val VIEWPORT_EPG_SHORT_LIMIT = 16
        private const val VIEWPORT_EPG_MAX_CHANNELS_PER_BATCH = 8
        private const val VIEWPORT_EPG_TRACKER_MAX_ENTRIES = 512
        private const val SERIES_DETAIL_CACHE_MAX_ENTRIES = 24
        private const val SERIES_DETAIL_CACHE_MAX_BYTES = 8L * 1024L * 1024L
        /** Room SQLite bind-arg limit for IN (...) clauses — chunk larger page lookups. */
        private const val ROOM_IN_CHUNK = 500
        /** Cap in-memory EPG source channel catalog — larger tables use programme ids only. */
        private const val MAX_RESOLVER_SOURCE_CHANNELS = 5_000
        /** Only purge programmes that ended more than a week ago — never today's grid cache. */
        private const val EPG_PURGE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L
    }

    private val vodSyncGenerationCounter = AtomicLong(System.currentTimeMillis())

    private fun nextVodSyncGeneration(): Long = vodSyncGenerationCounter.incrementAndGet()

    private fun sqlSearchPrefix(query: String): String = SearchTitleNormalizer.toSqlPrefixPattern(query)
    private val recommendationEngine = RecommendationEngine()
    private val healthEngine = StreamHealthEngine()
    private val epgCache = EpgBlockCache(registry = appCacheRegistry)
    private val viewportEpgLastFetch = BoundedMemoryCache<Long, Long>(
        name = "viewport_epg_last_fetch",
        maxEntries = VIEWPORT_EPG_TRACKER_MAX_ENTRIES,
        maxBytes = 64L * 1024L,
        valueSizeEstimator = { 16 },
        registry = appCacheRegistry
    )
    private val viewportEpgFailureUntil = BoundedMemoryCache<Long, Long>(
        name = "viewport_epg_failure_until",
        maxEntries = VIEWPORT_EPG_TRACKER_MAX_ENTRIES,
        maxBytes = 64L * 1024L,
        valueSizeEstimator = { 16 },
        registry = appCacheRegistry
    )
    private val _epgDataRevision = MutableStateFlow(0L)
    @Volatile
    private var epgLinkResolversByPlaylist: MutableMap<Long, EpgChannelLinkResolver>? = null
    private val epgLinkResolverRebuildMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    private var cachedSettings = AppSettings()
    private var activeProfileId = 1L
    private val _vodCatalogRevision = MutableStateFlow(0L)
    private val vodCatalogLoading = MutableStateFlow(false)
    private val vodCatalogProgress = MutableStateFlow(VodCatalogProgress())
    private val vodCatalogStatus = MutableStateFlow(VodCatalogStatus())
    private val startupHeavyWorkMutex = Mutex()
    private val epgRefreshMutex = Mutex()
    private val vodDiskLoadMutex = Mutex()
    private val vodRepositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var vodDiskCacheLoaded = false
    @Volatile
    private var lastVodRefreshCompletedAtMs = 0L
    @Volatile
    private var activeVodRefresh: CompletableDeferred<Unit>? = null
    @Volatile
    private var deferredVodCatalogRefreshJob: Job? = null

    private enum class CatalogCountSource { PERSISTED, DATABASE }

    private data class CatalogCountsSnapshot(
        val movies: Int,
        val series: Int,
        val channels: Int,
        val updatedAtMs: Long,
        val source: CatalogCountSource
    )

    @Volatile
    private var sessionCatalogCounts: CatalogCountsSnapshot? = null

    @Volatile
    private var sessionDbCountsLoaded = false

    private val _vodStreamCountFlow = MutableStateFlow(0)
    private val _seriesShowCountFlow = MutableStateFlow(0)
    private val _channelCountFlow = MutableStateFlow(0)

    private fun ensureRepositoryBootstrap() {
        if (repositoryBootstrapComplete) return
        synchronized(this) {
            if (repositoryBootstrapComplete) return
            StartupTiming.trace("IptvRepositoryImpl.restorePersistedCatalogCounts") {
                restorePersistedCatalogCounts()
            }
            repositoryBootstrapComplete = true
            StartupTiming.log("IptvRepositoryImpl construction bootstrap complete")
        }
    }

    private fun restorePersistedCatalogCounts() {
        val persisted = StartupTiming.trace("IptvRepositoryImpl.startupCatalogCountsStore.read") {
            startupCatalogCountsStore.read()
        }
        if (!persisted.isValid) return
        val snapshot = persisted.toSnapshot(CatalogCountSource.PERSISTED)
        sessionCatalogCounts = snapshot
        _vodStreamCountFlow.value = snapshot.movies
        _seriesShowCountFlow.value = snapshot.series
        _channelCountFlow.value = snapshot.channels
        seedVodRefreshBaselineIfNeeded(persisted)
    }

    private fun seedVodRefreshBaselineIfNeeded(persisted: PersistedCatalogCounts = startupCatalogCountsStore.read()) {
        if (!persisted.isValid) {
            seedVodRefreshBaselineFromManifest()
            return
        }
        if (persisted.movies <= 0 && persisted.series <= 0) {
            seedVodRefreshBaselineFromManifest()
            return
        }
        if (lastVodRefreshCompletedAtMs > 0L) return
        lastVodRefreshCompletedAtMs = persisted.updatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        seedVodRefreshBaselineFromManifest()
    }

    private fun seedVodRefreshBaselineFromManifest() {
        val manifestSavedAt = vodCatalogCacheManifestStore.latestSavedAtMs()
        if (manifestSavedAt > lastVodRefreshCompletedAtMs) {
            lastVodRefreshCompletedAtMs = manifestSavedAt
        }
    }

    private suspend fun isVodPlaylistDiskCacheFresh(playlist: PlaylistEntity): Boolean {
        val manifest = vodCatalogCacheManifestStore.read(playlist.id) ?: return false
        if (manifest.playlistVersionKey != vodPlaylistCacheVersionKey(playlist)) return false
        if (!manifest.isFresh(ttlMs = VOD_CACHE_TTL_MS)) return false
        val roomMovies = vodStreamDao.countByPlaylist(playlist.id)
        val roomSeries = seriesShowDao.countByPlaylist(playlist.id)
        val moviesOk = manifest.moviesCount <= 0 || roomMovies >= manifest.moviesCount
        val seriesOk = manifest.seriesCount <= 0 || roomSeries >= manifest.seriesCount
        return (roomMovies > 0 || roomSeries > 0) && moviesOk && seriesOk
    }

    private suspend fun canSkipVodNetworkRefreshFromDiskCache(): Boolean {
        loadSettings()
        val playlists = playlistDao.all().filter { it.type == PlaylistType.XTREAM.name }
        if (playlists.isEmpty()) return false
        return playlists.all { playlist -> isVodPlaylistDiskCacheFresh(playlist) }
    }

    private suspend fun shouldSkipStartupVodNetworkRefresh(trigger: VodRefreshTrigger): Boolean {
        if (trigger != VodRefreshTrigger.REPOSITORY_INIT) return false
        val persisted = startupCatalogCountsStore.read()
        val movies = persisted.movies.takeIf { it > 0 } ?: cachedMoviesCount()
        val series = persisted.series.takeIf { it > 0 } ?: cachedSeriesCount()
        if (movies < STARTUP_VOD_SKIP_MIN_ROWS && series < STARTUP_VOD_SKIP_MIN_ROWS) return false
        seedVodRefreshBaselineIfNeeded(persisted)
        Log.i(
            VOD_FLOW_TAG,
            "Skipping startup VOD network ingest — on-disk catalog movies=$movies series=$series"
        )
        return true
    }

    private suspend fun awaitVodIngestAllowed() {
        while (catalogHydrationGuard.shouldDeferHeavyCatalogIo()) {
            delay(StartupTierPolicy.vodIngestPlaybackWaitMs())
        }
    }

    private suspend fun throttleVodIngestBatch(batchIndex: Int) {
        if (batchIndex <= 0) return
        if (batchIndex % StartupTierPolicy.vodIngestYieldEveryNBatches() != 0) return
        delay(StartupTierPolicy.vodIngestBatchGapMs())
        yield()
    }

    private fun ingestRevisionCadence(): Int =
        if (LowEndDeviceMode.current().active) {
            INGEST_REVISION_EVERY_BATCHES * 4
        } else {
            INGEST_REVISION_EVERY_BATCHES
        }

    private fun PersistedCatalogCounts.toSnapshot(source: CatalogCountSource): CatalogCountsSnapshot =
        CatalogCountsSnapshot(
            movies = movies,
            series = series,
            channels = channels,
            updatedAtMs = updatedAtMs,
            source = source
        )

    private fun cachedMoviesCount(): Int = sessionCatalogCounts?.movies ?: _vodStreamCountFlow.value

    private fun cachedSeriesCount(): Int = sessionCatalogCounts?.series ?: _seriesShowCountFlow.value

    private fun cachedChannelsCount(): Int = sessionCatalogCounts?.channels ?: _channelCountFlow.value

    private suspend fun queryChannelCountFromDb(): Int =
        withContext(diskIoSerialExecutor.dispatcher) {
            channelDao.countTotal()
        }

    private suspend fun queryCatalogCountsFromDbChunked(): CatalogCountsSnapshot {
        StartupProfiler.mark("phase2_count_query_start")
        val moviesStart = SystemClock.elapsedRealtime()
        val movies = withContext(diskIoSerialExecutor.dispatcher) {
            vodStreamDao.countTotal()
        }
        val moviesMs = SystemClock.elapsedRealtime() - moviesStart
        delay(StartupTierPolicy.phase2CountChunkDelayMs())
        yield()

        val seriesStart = SystemClock.elapsedRealtime()
        val series = withContext(diskIoSerialExecutor.dispatcher) {
            seriesShowDao.countTotal()
        }
        val seriesMs = SystemClock.elapsedRealtime() - seriesStart
        delay(StartupTierPolicy.phase2CountChunkDelayMs())
        yield()

        val channelsStart = SystemClock.elapsedRealtime()
        val channels = withContext(diskIoSerialExecutor.dispatcher) {
            channelDao.countTotal()
        }
        val channelsMs = SystemClock.elapsedRealtime() - channelsStart

        val totalMs = moviesMs + seriesMs + channelsMs
        StartupProfiler.mark(
            "phase2_count_query_end",
            "total=${totalMs}ms movies=${moviesMs}ms($movies) " +
                "series=${seriesMs}ms($series) channels=${channelsMs}ms($channels)"
        )
        Log.i(
            IPTV_REPO_LOG_TAG,
            "countTotal(chunked): ${totalMs}ms total (movies=${moviesMs}ms/$movies " +
                "series=${seriesMs}ms/$series channels=${channelsMs}ms/$channels)"
        )
        return CatalogCountsSnapshot(
            movies = movies,
            series = series,
            channels = channels,
            updatedAtMs = System.currentTimeMillis(),
            source = CatalogCountSource.DATABASE
        )
    }

    private suspend fun queryCatalogCountsFromDb(): CatalogCountsSnapshot {
        val moviesStart = SystemClock.elapsedRealtime()
        val movies = vodStreamDao.countTotal()
        val moviesMs = SystemClock.elapsedRealtime() - moviesStart
        val seriesStart = SystemClock.elapsedRealtime()
        val series = seriesShowDao.countTotal()
        val seriesMs = SystemClock.elapsedRealtime() - seriesStart
        val channelsStart = SystemClock.elapsedRealtime()
        val channels = channelDao.countTotal()
        val channelsMs = SystemClock.elapsedRealtime() - channelsStart
        val totalMs = moviesMs + seriesMs + channelsMs
        StartupProfiler.mark(
            "catalog_count_queries",
            "total=${totalMs}ms movies=${moviesMs}ms($movies) " +
                "series=${seriesMs}ms($series) channels=${channelsMs}ms($channels)"
        )
        Log.i(
            IPTV_REPO_LOG_TAG,
            "countTotal: ${totalMs}ms total (movies=${moviesMs}ms/$movies " +
                "series=${seriesMs}ms/$series channels=${channelsMs}ms/$channels)"
        )
        return CatalogCountsSnapshot(
            movies = movies,
            series = series,
            channels = channels,
            updatedAtMs = System.currentTimeMillis(),
            source = CatalogCountSource.DATABASE
        )
    }

    private suspend fun refreshCatalogCountsFromDb(
        trigger: VodRefreshTrigger,
        force: Boolean = false,
        publishUi: Boolean = true,
        persist: Boolean = true
    ): CatalogCountsSnapshot {
        if (startupSafety.shouldDeferDbCountQueries()) {
            sessionCatalogCounts?.let { return it }
            return CatalogCountsSnapshot(
                movies = cachedMoviesCount(),
                series = cachedSeriesCount(),
                channels = cachedChannelsCount(),
                updatedAtMs = System.currentTimeMillis(),
                source = CatalogCountSource.PERSISTED
            )
        }
        if (!force && sessionDbCountsLoaded) {
            sessionCatalogCounts?.let { return it }
        }
        val counts = queryCatalogCountsFromDb()
        sessionDbCountsLoaded = true
        applyCatalogCounts(
            counts = counts,
            trigger = trigger,
            persist = persist,
            publishUi = publishUi
        )
        return counts
    }

    private fun applyCatalogCounts(
        counts: CatalogCountsSnapshot,
        trigger: VodRefreshTrigger,
        persist: Boolean,
        publishUi: Boolean
    ) {
        val previous = sessionCatalogCounts
        val moviesChanged = previous?.movies != counts.movies
        val seriesChanged = previous?.series != counts.series
        sessionCatalogCounts = counts
        _vodStreamCountFlow.value = counts.movies
        _seriesShowCountFlow.value = counts.series
        _channelCountFlow.value = counts.channels
        if (persist) {
            startupCatalogCountsStore.write(
                PersistedCatalogCounts(
                    movies = counts.movies,
                    series = counts.series,
                    channels = counts.channels,
                    updatedAtMs = counts.updatedAtMs
                )
            )
        }
        if (publishUi) {
            publishVodCatalogCounts(
                trigger = trigger,
                moviesCount = counts.movies,
                seriesCount = counts.series
            )
        }
        if (moviesChanged || seriesChanged) {
            bumpVodCatalogRevision()
        }
    }

    private fun invalidateCatalogCountCache() {
        sessionCatalogCounts = null
        sessionDbCountsLoaded = false
        invalidateGuideBootstrapChannelCache()
    }

    private data class GuideBootstrapChannelCache(
        val groups: Set<String>,
        val favoritesOnly: Boolean,
        val favoriteGroupId: Long?,
        val hideAdultContent: Boolean,
        val limit: Int,
        val channels: List<Channel>,
    )

    @Volatile
    private var guideBootstrapChannelCache: GuideBootstrapChannelCache? = null

    private fun invalidateGuideBootstrapChannelCache() {
        guideBootstrapChannelCache = null
    }
    private val seriesSeasonsCache = BoundedMemoryCache<Pair<Long, Long>, SeriesDetail>(
        name = "series_seasons",
        maxEntries = SERIES_DETAIL_CACHE_MAX_ENTRIES,
        maxBytes = SERIES_DETAIL_CACHE_MAX_BYTES,
        valueSizeEstimator = CacheSizeEstimators::seriesDetail,
        registry = appCacheRegistry
    )

    @Volatile
    private var cachedCategoryLookup: Map<String, String>? = null

    @Volatile
    private var cachedCategoryLookupRevision: Long = -1L

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
            delay(StartupTierPolicy.phase3DelayMs())
            invalidateCatalogCountCache()
            refreshCatalogCountsFromDb(
                trigger = VodRefreshTrigger.REPOSITORY_INIT,
                force = true,
                publishUi = true,
                persist = true
            )
            val shouldRefresh = playlistImportCoordinator.consumeDeferredVodRefresh() || !isVodCatalogFresh()
            if (!shouldRefresh) return@launch
            try {
                StartupProfiler.mark("vod_refresh_start", "post_import")
                refreshVodSeriesCatalog(trigger = VodRefreshTrigger.REPOSITORY_INIT, force = false)
                StartupProfiler.mark("vod_refresh_complete", "post_import")
            } catch (error: Throwable) {
                Log.e(
                    VOD_FLOW_TAG,
                    "Deferred VOD refresh failed after live import: ${error.message}",
                    error
                )
                val progress = vodCatalogProgress.value.copy(isLoading = false)
                vodCatalogProgress.value = progress
                vodCatalogStatus.value = vodCatalogStatus.value.copy(
                    progress = progress,
                    moviesError = error.message ?: "Movie catalog refresh failed",
                    seriesError = error.message ?: "Series catalog refresh failed"
                )
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
        seriesSeasonsCache.removeIf { it.first == playlistId }
    }

    private fun bumpVodCatalogRevision() {
        invalidateCategoryLookupCache()
        _vodCatalogRevision.update { it + 1L }
    }

    private fun publishMoviesIngestBatch(parsedSoFar: Int, batchIndex: Int) {
        _vodStreamCountFlow.value = parsedSoFar
        val cadence = ingestRevisionCadence()
        if (batchIndex == 0 || batchIndex % cadence == 0) {
            bumpVodCatalogRevision()
        }
    }

    private fun publishSeriesIngestBatch(parsedSoFar: Int, batchIndex: Int) {
        _seriesShowCountFlow.value = parsedSoFar
        val cadence = ingestRevisionCadence()
        if (batchIndex == 0 || batchIndex % cadence == 0) {
            bumpVodCatalogRevision()
        }
    }

    private fun isSuccessfulHttp(httpCode: Int): Boolean = httpCode in 200..299

    private fun deleteCatalogCacheFile(file: File) {
        if (!file.exists()) return
        if (!file.delete()) {
            Log.w(VOD_FLOW_TAG, "Failed to delete catalog cache file ${file.absolutePath}")
        }
    }

    private suspend fun preserveCatalogLog(
        playlistId: Long,
        contentType: String,
        reason: String
    ) {
        val movies = vodStreamDao.countByPlaylist(playlistId)
        val series = seriesShowDao.countByPlaylist(playlistId)
        Log.w(
            VOD_FLOW_TAG,
            "Preserving $contentType catalog playlistId=$playlistId movies=$movies series=$series — $reason"
        )
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
        unique.map { it.withSearchTitle() }.chunked(CHANNEL_INSERT_CHUNK).forEach { channelDao.insertAll(it) }
        return unique.size
    }

    private suspend fun finalizeChannelImport(playlistId: Long) {
        smartGroupService.rebuildCacheForPlaylist(playlistId)
        persistGroupMetadataSnapshot(playlistId)
    }

    private suspend fun expandGuideFilterGroups(groups: Set<String>): Set<String> =
        smartGroupService.resolveFilterGroups(groups)

    private suspend fun mapChannelEntities(
        entities: List<com.grid.tv.data.db.entity.ChannelEntity>,
        bootstrapFast: Boolean = false,
    ): List<Channel> {
        if (entities.isEmpty()) return emptyList()
        val playlistNames = playlistNameMap()
        if (bootstrapFast) {
            return entities.map { entity ->
                channelFromEntity(entity, playlistNames[entity.playlistId])
            }
        }
        val playlists = playlistDao.all().associateBy { it.id }
        val channelIds = entities.map { it.id }
        val health = healthForChannelIds(channelIds)
        val favIds = favoriteIdsForChannelIds(channelIds)
        val resolved = entities.map { entity ->
            resolveXtreamPlaybackEntity(entity, playlists[entity.playlistId])
        }
        return mapChannelsWithPlaylists(resolved, playlistNames, health, favIds)
    }

    private suspend fun healthForChannelIds(channelIds: List<Long>): Map<Long, StreamHealthEntity> {
        if (channelIds.isEmpty()) return emptyMap()
        return channelIds.chunked(ROOM_IN_CHUNK).flatMap { chunk ->
            streamHealthDao.getForChannelIds(chunk)
        }.associateBy { it.channelId }
    }

    private suspend fun favoriteIdsForChannelIds(channelIds: List<Long>): Set<Long> {
        if (channelIds.isEmpty()) return emptySet()
        return channelIds.chunked(ROOM_IN_CHUNK).flatMap { chunk ->
            profileFavoriteDao.favoriteIdsAmong(activeProfileId, chunk)
        }.toSet()
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
    }.flowOn(Dispatchers.IO)

    override suspend fun hasActiveConnection(): Boolean = playlistDao.all().isNotEmpty()

    private fun resolveXtreamPassword(playlist: PlaylistEntity): String? =
        secureCredentialStore.getXtreamPassword(playlist.id) ?: playlist.xtreamPassword

    override fun groups(): Flow<List<String>> =
        observeGroupMetadata().map { it.groups }.flowOn(Dispatchers.IO)

    override fun groupChannelCounts(): Flow<Map<String, Int>> =
        observeGroupMetadata().map { it.counts }.flowOn(Dispatchers.IO)

    override fun observeGroupMetadata(): Flow<com.grid.tv.feature.guide.GuideGroupMetadata> =
        playlistImportCoordinator.importActive.flatMapLatest { importing ->
            if (importing) {
                emptyFlow()
            } else {
                channelDao.observeGroupChannelCounts().map(::groupMetadataFromRows)
            }
        }
            .onEach { metadata ->
                if (metadata.groups.isNotEmpty() && !playlistImportCoordinator.isImportActive()) {
                    startupGroupMetadataStore.write(metadata)
                }
            }
            .flowOn(Dispatchers.IO)

    private fun groupMetadataFromRows(
        rows: List<com.grid.tv.data.db.model.GroupChannelCountRow>
    ): com.grid.tv.feature.guide.GuideGroupMetadata {
        val startNs = System.nanoTime()
        val sortedRows = rows.sortedWith(
            kotlin.comparisons.compareBy<com.grid.tv.data.db.model.GroupChannelCountRow>(
                { it.groupName.trim().lowercase() },
                { it.groupName.trim() },
                { it.playlistId }
            )
        )
        val groups = ArrayList<String>(sortedRows.size)
        val counts = LinkedHashMap<String, Int>(sortedRows.size)
        sortedRows.forEach { row ->
            val key = com.grid.tv.domain.model.ChannelGroupIdentity.groupKey(
                row.playlistId,
                row.groupName
            )
            groups += key
            counts[key] = row.channelCount
        }
        val metadata = com.grid.tv.feature.guide.GuideGroupMetadata(groups = groups, counts = counts)
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        GroupsTrace.logMetadataLoaded(
            source = "room_group_by",
            groupCount = groups.size,
            durationMs = durationMs,
            cached = false
        )
        return metadata
    }

    private suspend fun persistGroupMetadataSnapshot(playlistId: Long) {
        val rows = channelDao.groupChannelCountsForPlaylist(playlistId)
        if (rows.isEmpty()) return
        val metadata = groupMetadataFromRows(rows)
        startupGroupMetadataStore.write(metadata)
    }

    override fun observeFavoriteChannelGroups(playlistId: Long): Flow<List<String>> =
        playlistFavoriteGroupDao.observeGroupKeys(playlistId).flowOn(Dispatchers.IO)

    override fun observeIsFavoriteChannelGroup(playlistId: Long, groupKey: String): Flow<Boolean> =
        playlistFavoriteGroupDao.observeIsFavorite(playlistId, groupKey).flowOn(Dispatchers.IO)

    override suspend fun toggleFavoriteChannelGroup(playlistId: Long, groupKey: String) =
        withContext(Dispatchers.IO) {
            if (playlistFavoriteGroupDao.isFavorite(playlistId, groupKey)) {
                playlistFavoriteGroupDao.delete(playlistId, groupKey)
            } else {
                val sortOrder = playlistFavoriteGroupDao.maxSortOrder(playlistId) + 1
                playlistFavoriteGroupDao.insert(
                    PlaylistFavoriteGroupEntity(
                        playlistId = playlistId,
                        groupKey = groupKey,
                        sortOrder = sortOrder
                    )
                )
            }
        }

    override suspend fun getFavoriteChannelGroups(playlistId: Long): List<String> =
        withContext(Dispatchers.IO) {
            playlistFavoriteGroupDao.observeGroupKeys(playlistId).first()
        }

    override fun channels(
        group: String?,
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?
    ): Flow<List<Channel>> {
        val groupFilter = favoriteGroupId ?: -1L
        val parsed = com.grid.tv.domain.model.ChannelGroupIdentity.parseFilter(group)
        return combine(
            channelDao.observeChannels(
                filterPlaylistId = parsed.playlistId,
                filterGroupName = parsed.groupName,
                searchPrefix = sqlSearchPrefix(search),
                onlyFavorites = favoritesOnly,
                profileId = activeProfileId,
                favoriteGroupId = groupFilter
            ),
            playlistDao.observeAll(),
            profileFavoriteDao.observeForProfile(activeProfileId)
        ) { rows, playlists, favs -> Triple(rows, playlists, favs) }
            .flatMapLatest { (rows, playlists, favs) ->
                val channelIds = rows.map { it.id }
                val healthFlow = if (channelIds.isEmpty()) {
                    flowOf(emptyList<StreamHealthEntity>())
                } else {
                    streamHealthDao.observeForChannelIds(channelIds)
                }
                healthFlow.map { healthRows ->
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
            .flowOn(Dispatchers.IO)
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
        val resolvedGroups = expandGuideFilterGroups(groups)
        val rows = if (resolvedGroups.isEmpty()) {
            channelDao.channelsPage(
                filterPlaylistId = -1L,
                filterGroupName = null,
                searchPrefix = sqlSearchPrefix(search),
                onlyFavorites = favoritesOnly,
                profileId = activeProfileId,
                favoriteGroupId = groupFilter,
                limit = limit,
                offset = offset
            )
        } else {
            channelDao.channelsPageInGroups(
                groupKeys = resolvedGroups.toList(),
                searchPrefix = sqlSearchPrefix(search),
                onlyFavorites = favoritesOnly,
                profileId = activeProfileId,
                favoriteGroupId = groupFilter,
                limit = limit,
                offset = offset
            )
        }
        mapChannelEntities(rows)
    }

    override suspend fun takeGuideBootstrapChannelPage(
        groups: Set<String>,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?,
        hideAdultContent: Boolean,
        limit: Int,
    ): List<Channel>? = withContext(Dispatchers.IO) {
        val cached = guideBootstrapChannelCache ?: return@withContext null
        val matches = cached.groups == groups &&
            cached.favoritesOnly == favoritesOnly &&
            cached.favoriteGroupId == favoriteGroupId &&
            cached.hideAdultContent == hideAdultContent &&
            cached.limit == limit
        if (!matches) return@withContext null
        guideBootstrapChannelCache = null
        StartupProfiler.mark(
            "guide_bootstrap_channels_cache_hit",
            "count=${cached.channels.size} groups=${groups.size}",
        )
        cached.channels
    }

    private suspend fun warmGuideBootstrapChannelPage(settings: AppSettings) {
        if (getCachedCatalogCounts().channels <= 0) return
        val groups = GuideChannelFilter(settings.guideChannelGroups).selectedGroups
        val resolvedGroups = expandGuideFilterGroups(groups)
        val limit = StartupTierPolicy.guideInitialChannelPageSize()
        val hideAdult = settings.hideAdultContent
        val rows = if (resolvedGroups.isEmpty()) {
            channelDao.channelsPage(
                filterPlaylistId = -1L,
                filterGroupName = null,
                searchPrefix = "",
                onlyFavorites = false,
                profileId = activeProfileId,
                favoriteGroupId = -1L,
                limit = limit,
                offset = 0,
            )
        } else {
            channelDao.channelsPageInGroups(
                groupKeys = resolvedGroups.toList(),
                searchPrefix = "",
                onlyFavorites = false,
                profileId = activeProfileId,
                favoriteGroupId = -1L,
                limit = limit,
                offset = 0,
            )
        }
        var channels = mapChannelEntities(rows, bootstrapFast = true)
        if (hideAdult) {
            channels = channels.filter { !ProfileAccessGuard.isAdultGroup(it.group) }
        }
        if (channels.isEmpty()) return
        guideBootstrapChannelCache = GuideBootstrapChannelCache(
            groups = groups,
            favoritesOnly = false,
            favoriteGroupId = null,
            hideAdultContent = hideAdult,
            limit = limit,
            channels = channels,
        )
        StartupProfiler.mark(
            "guide_bootstrap_channels_warmed",
            "count=${channels.size} groups=${groups.size}",
        )
    }

    override fun channelsPaging(
        group: String?,
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?,
        sportsEpgIds: Set<String>?
    ): Flow<PagingData<Channel>> {
        val trimmedSearch = search.trim()
        val searchPrefix = sqlSearchPrefix(trimmedSearch)
        val groupFilter = favoriteGroupId ?: -1L
        val (matchSports, sportsIds) = sportsFilterParams(sportsEpgIds)
        val parsed = com.grid.tv.domain.model.ChannelGroupIdentity.parseFilter(group)
        return Pager(
            config = PagingConfig(
                pageSize = CHANNEL_BROWSER_PAGE_SIZE,
                prefetchDistance = CHANNEL_BROWSER_PREFETCH,
                enablePlaceholders = false,
                initialLoadSize = CHANNEL_BROWSER_PAGE_SIZE
            ),
            pagingSourceFactory = {
                ChannelBrowserPagingSource(
                    channelDao = channelDao,
                    filterPlaylistId = parsed.playlistId,
                    filterGroupName = parsed.groupName,
                    searchPrefix = searchPrefix,
                    onlyFavorites = favoritesOnly,
                    profileId = activeProfileId,
                    favoriteGroupId = groupFilter,
                    matchSports = matchSports,
                    sportsEpgIds = sportsIds,
                    mapPage = { entities -> mapChannelEntities(entities) }
                )
            }
        ).flow
    }

    override suspend fun channelsFilteredCount(
        group: String?,
        search: String,
        favoritesOnly: Boolean,
        favoriteGroupId: Long?,
        sportsEpgIds: Set<String>?
    ): Int = withContext(Dispatchers.IO) {
        val groupFilter = favoriteGroupId ?: -1L
        val (matchSports, sportsIds) = sportsFilterParams(sportsEpgIds)
        val parsed = com.grid.tv.domain.model.ChannelGroupIdentity.parseFilter(group)
        channelDao.countChannelsFiltered(
            filterPlaylistId = parsed.playlistId,
            filterGroupName = parsed.groupName,
            searchPrefix = sqlSearchPrefix(search),
            onlyFavorites = favoritesOnly,
            profileId = activeProfileId,
            favoriteGroupId = groupFilter,
            matchSports = matchSports,
            sportsEpgIds = sportsIds
        )
    }

    private fun sportsFilterParams(sportsEpgIds: Set<String>?): Pair<Boolean, List<String>> {
        if (sportsEpgIds == null) return false to listOf("")
        if (sportsEpgIds.isEmpty()) return true to listOf(SPORTS_FILTER_EMPTY_SENTINEL)
        return true to sportsEpgIds.toList().sorted()
    }

    override fun hasChannels(): Flow<Boolean> =
        _channelCountFlow.map { it > 0 }

    override suspend fun searchChannels(query: String, limit: Int): List<Channel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        mapChannelEntities(
            channelDao.channelsPage(
                filterPlaylistId = -1L,
                filterGroupName = null,
                searchPrefix = sqlSearchPrefix(trimmed),
                onlyFavorites = false,
                profileId = activeProfileId,
                favoriteGroupId = -1L,
                limit = limit,
                offset = 0
            )
        )
    }

    override fun programs(playlistId: Long, epgIds: List<String>, fromTime: Long): Flow<List<Program>> =
        programDao.observeGrid(playlistId, epgIds, fromTime).map { rows ->
            rows.map(::programFromEntity)
        }.flowOn(Dispatchers.IO)

    override fun searchPrograms(query: String): Flow<List<Program>> =
        programDao.observeSearch(query).map { rows ->
            rows.map(::programFromEntity)
        }.flowOn(Dispatchers.IO)

    override fun recordings(): Flow<List<String>> = recordingDao.observeAll().map { rows ->
        rows.map { "${it.programTitle} (${it.status})" }
    }.flowOn(Dispatchers.IO)

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
        }.flowOn(Dispatchers.IO)
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
        }.flowOn(Dispatchers.IO)

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
        }.flowOn(Dispatchers.IO)

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
        }.flowOn(Dispatchers.IO)

    override fun liveSportsNow(): Flow<List<Program>> =
        programDao.observeSports(System.currentTimeMillis()).map { rows ->
            rows.map {
                Program(it.id, it.channelEpgId, it.title, it.description, it.startTime, it.endTime, ProgramGenre.SPORTS, it.catchupUrl)
            }
        }.flowOn(Dispatchers.IO)

    override fun moviesStartingSoon(now: Long): Flow<List<Program>> {
        val windowEnd = now + 30 * 60 * 1000
        return programDao.observeMoviesStartingSoon(now, windowEnd).map { rows ->
            rows.map {
                Program(it.id, it.channelEpgId, it.title, it.description, it.startTime, it.endTime, ProgramGenre.MOVIES, it.catchupUrl)
            }
        }.flowOn(Dispatchers.IO)
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
            channels
                .groupBy { it.playlistId }
                .flatMap { (playlistId, playlistChannels) ->
                    programsWindowForPlaylistChannels(playlistId, playlistChannels, start, end)
                }
                .distinctBy { it.id }
        }

    private suspend fun programsWindowForPlaylistChannels(
        playlistId: Long,
        channels: List<Channel>,
        start: Long,
        end: Long
    ): List<Program> {
        if (channels.isEmpty()) return emptyList()

        val resolver = ensureEpgLinkResolver(playlistId)
        val xmlTvToPlaylist = linkedMapOf<String, MutableList<String>>()
        val xmlTvIdsToQuery = buildProgrammeQueryIds(channels, resolver, xmlTvToPlaylist)

        if (xmlTvIdsToQuery.isEmpty()) {
            val noEpgId = channels.count { it.epgId.isNullOrBlank() }
            Log.w(
                EPG_FLOW_TAG,
                "programsWindowForChannels playlist=$playlistId: no XMLTV ids " +
                    "(${channels.size} channels, $noEpgId without epgId)"
            )
            logEpgMatchDebug(channels, resolver, emptyMap(), start)
            return emptyList()
        }

        val key = "${_epgDataRevision.value}-p$playlistId-${start / 1000}-${end / 1000}-" +
            "ch${channels.size}-${xmlTvIdsToQuery.hashCode()}"
        val cached = epgCache.get(key)
        val rawPrograms = if (cached != null) {
            cached
        } else {
            val loaded = loadProgramsForXmlTvIds(playlistId, xmlTvIdsToQuery.toList(), start, end)
            epgCache.put(key, loaded)
            loaded
        }

        val remapped = remapProgramsToPlaylistKeys(rawPrograms, xmlTvToPlaylist, playlistId)

        if (Log.isLoggable(EPG_MATCH_DEBUG_TAG, Log.DEBUG)) {
            val programmesByPlaylistEpgId = remapped.groupBy { it.channelEpgId }
            logEpgMatchDebug(channels, resolver, programmesByPlaylistEpgId, start)
        }
        return remapped
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
        val groups = channels.groupBy { it.playlistId }
        val perPlaylist = groups.map { (playlistId, groupChannels) ->
            observeProgramsWindowForPlaylist(playlistId, groupChannels, windowStart, windowEnd)
        }
        if (perPlaylist.size == 1) {
            perPlaylist[0].collect { emit(it) }
            return@flow
        }
        combine(perPlaylist) { chunks ->
            chunks.flatMap { it.asIterable() }.distinctBy { it.id }
        }.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private fun observeProgramsWindowForPlaylist(
        playlistId: Long,
        channels: List<Channel>,
        windowStart: Long,
        windowEnd: Long
    ): Flow<List<Program>> = flow {
        if (channels.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val resolver = ensureEpgLinkResolver(playlistId)
        val xmlTvToPlaylist = linkedMapOf<String, MutableList<String>>()
        val queryIds = buildProgrammeQueryIds(channels, resolver, xmlTvToPlaylist)
        if (queryIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        programDao.observeWindow(playlistId, queryIds.toList(), windowStart, windowEnd).collect { rows ->
            val programs = rows.map(::programFromEntity)
            emit(remapProgramsToPlaylistKeys(programs, xmlTvToPlaylist, playlistId))
        }
    }

    override suspend fun fetchCurrentEpgForChannels(channelIds: List<String>): Int = withContext(epgDispatchers.io) {
        if (channelIds.isEmpty()) return@withContext 0
        if (!startupSafety.allowNetwork("viewport_epg")) return@withContext 0
        if (catalogHydrationGuard.viewportEpgSuspended) {
            Log.d(EPG_FLOW_TAG, "Viewport EPG skipped — live playback active")
            return@withContext 0
        }
        if (epgDownloadTracker.isInProgress()) {
            Log.d(EPG_FLOW_TAG, "Viewport EPG skipped — bulk EPG import in progress")
            return@withContext 0
        }
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
            val failureUntil = viewportEpgFailureUntil.get(channel.id) ?: 0L
            if (failureUntil > now) return@filter false
            val lastFetch = viewportEpgLastFetch.get(channel.id) ?: 0L
            now - lastFetch >= VIEWPORT_EPG_COOLDOWN_MS
        }
        if (eligible.isEmpty()) {
            Log.d(EPG_FLOW_TAG, "Viewport EPG fetch skipped — all ${ids.size} channel(s) still in cooldown")
            return@withContext 0
        }

        val batch = eligible.take(VIEWPORT_EPG_MAX_CHANNELS_PER_BATCH)
        Log.i(
            EPG_FLOW_TAG,
            "Viewport EPG fast-track for ${batch.size}/${eligible.size} eligible channel(s), " +
                "window [$windowStart, $windowEnd]"
        )

        val fetched = mutableListOf<com.grid.tv.data.db.entity.ProgramEntity>()
        var httpFailures = 0
        for (channel in batch) {
            if (epgDownloadTracker.isInProgress()) break
            when (val result = fetchViewportEpgForChannel(
                channel = channel,
                playlist = playlistsById[channel.playlistId],
                windowStart = windowStart,
                windowEnd = windowEnd
            )) {
                is ViewportEpgFetchResult.Programs -> {
                    viewportEpgFailureUntil.remove(channel.id)
                    viewportEpgLastFetch.put(channel.id, now)
                    fetched += result.items
                }
                ViewportEpgFetchResult.HttpUnavailable -> {
                    httpFailures++
                    viewportEpgFailureUntil.put(channel.id, now + VIEWPORT_EPG_ERROR_COOLDOWN_MS)
                    viewportEpgLastFetch.put(channel.id, now)
                }
                ViewportEpgFetchResult.Skipped -> Unit
            }
        }

        if (httpFailures > 0) {
            Log.w(
                EPG_FLOW_TAG,
                "Viewport EPG: $httpFailures channel(s) returned HTTP errors — backing off for " +
                    "${VIEWPORT_EPG_ERROR_COOLDOWN_MS / 60_000L} min"
            )
        }

        if (fetched.isEmpty()) return@withContext 0

        programDao.insertAll(fetched)
        epgCache.clear()
        _epgDataRevision.update { it + 1 }

        Log.i(
            EPG_FLOW_TAG,
            "Viewport EPG upserted ${fetched.size} programme(s) for ${batch.size} channel(s)"
        )
        fetched.size
    }

    private sealed interface ViewportEpgFetchResult {
        data class Programs(val items: List<com.grid.tv.data.db.entity.ProgramEntity>) : ViewportEpgFetchResult
        data object HttpUnavailable : ViewportEpgFetchResult
        data object Skipped : ViewportEpgFetchResult
    }

    private suspend fun fetchViewportEpgForChannel(
        channel: Channel,
        playlist: PlaylistEntity?,
        windowStart: Long,
        windowEnd: Long
    ): ViewportEpgFetchResult {
        if (playlist == null || playlist.type != PlaylistType.XTREAM.name) return ViewportEpgFetchResult.Skipped
        val streamId = resolveXtreamStreamId(channel) ?: return ViewportEpgFetchResult.Skipped
        val server = playlist.xtreamServerUrl ?: return ViewportEpgFetchResult.Skipped
        val user = playlist.xtreamUsername ?: return ViewportEpgFetchResult.Skipped
        val pass = resolveXtreamPassword(playlist) ?: return ViewportEpgFetchResult.Skipped
        val channelEpgId = channel.epgId?.trim()?.takeIf { it.isNotEmpty() } ?: streamId
        val url = buildXtreamApiUrl(
            serverUrl = server,
            username = user,
            password = pass,
            action = "get_short_epg",
            extra = "stream_id=$streamId&limit=$VIEWPORT_EPG_SHORT_LIMIT"
        )
        val raw = remoteTextFetcher.tryFetchShortEpg(url)
            ?: return ViewportEpgFetchResult.HttpUnavailable
        return runCatching {
            val programs = JsonParseMetrics.onIoThread(
                label = "short_epg channel=${channel.name}",
                itemCount = -1
            ) {
                xtreamParser.parseShortEpg(
                    raw = raw,
                    channelEpgId = channelEpgId,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    playlistId = channel.playlistId
                )
            }
            ViewportEpgFetchResult.Programs(programs)
        }.onFailure { error ->
            Log.w(
                EPG_FLOW_TAG,
                "Viewport EPG parse failed for channel=${channel.name} streamId=$streamId: ${error.message}"
            )
        }.getOrDefault(ViewportEpgFetchResult.Skipped)
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
        xmlTvToPlaylist: Map<String, List<String>>,
        playlistId: Long
    ): List<Program> =
        rawPrograms.flatMap { program ->
            val playlistEpgIds = xmlTvToPlaylist[program.channelEpgId]
                ?: xmlTvToPlaylist.entries.firstOrNull { (xmlTvId, _) ->
                    xmlTvId.equals(program.channelEpgId, ignoreCase = true) ||
                        EpgIdNormalizer.normalize(xmlTvId) == EpgIdNormalizer.normalize(program.channelEpgId)
                }?.value
                ?: listOf(program.channelEpgId)
            playlistEpgIds.map { playlistEpgId ->
                program.copy(channelEpgId = playlistEpgId, playlistId = playlistId)
            }
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
            title = EpgProgramTextDecoder.decode(row.title),
            description = EpgProgramTextDecoder.decode(row.description),
            startTime = row.startTime,
            endTime = row.endTime,
            genre = runCatching { ProgramGenre.valueOf(row.genre) }.getOrDefault(ProgramGenre.GENERAL),
            catchupUrl = row.catchupUrl,
            playlistId = row.playlistId
        )

    private suspend fun loadProgramsForXmlTvIds(
        playlistId: Long,
        xmlTvIds: List<String>,
        start: Long,
        end: Long
    ): List<Program> {
        val entities = xmlTvIds.chunked(400).flatMap { chunk ->
            programDao.loadWindow(playlistId, chunk, start, end)
        }
        val foundLower = entities.map { it.channelEpgId.lowercase() }.toSet()
        val missingLower = xmlTvIds.map { it.lowercase() }.filter { it !in foundLower }.distinct()
        val caseInsensitive = if (missingLower.isEmpty()) {
            emptyList()
        } else {
            missingLower.chunked(400).flatMap { chunk ->
                programDao.loadWindowIgnoreCase(playlistId, chunk, start, end)
            }
        }
        return (entities + caseInsensitive)
            .distinctBy { it.id }
            .map(::programFromEntity)
    }

    private suspend fun ensureEpgLinkResolver(playlistId: Long): EpgChannelLinkResolver {
        epgLinkResolversByPlaylist?.get(playlistId)?.let { return it }
        val mutex = epgLinkResolverRebuildMutexes.getOrPut(playlistId) { Mutex() }
        return mutex.withLock {
            epgLinkResolversByPlaylist?.get(playlistId)?.let { return it }
            rebuildEpgLinkResolver(playlistId)
        }
    }

    private suspend fun rebuildEpgLinkResolver(playlistId: Long): EpgChannelLinkResolver =
        withContext(Dispatchers.IO) {
        StartupTrace.traceSuspend("rebuildEpgLinkResolver playlistId=$playlistId") {
        val sourceKey = "xmltv:$playlistId"
        val refs = linkedMapOf<String, XmlTvChannelRef>()
        val sourceChannels = epgSourceChannelDao.bySource(sourceKey)
        if (sourceChannels.size <= MAX_RESOLVER_SOURCE_CHANNELS) {
            sourceChannels.forEach { source ->
                refs[source.epgId] = XmlTvChannelRef(source.epgId, source.displayName)
            }
        } else {
            Log.w(
                EPG_FLOW_TAG,
                "EPG resolver: skipping ${sourceChannels.size} xmltv source channels for playlistId=$playlistId " +
                    "(cap=$MAX_RESOLVER_SOURCE_CHANNELS) — using programme channel ids only"
            )
        }
        programDao.distinctChannelEpgIdsForPlaylist(playlistId).forEach { epgId ->
            if (refs.size >= MAX_RESOLVER_SOURCE_CHANNELS) return@forEach
            refs.putIfAbsent(epgId, XmlTvChannelRef(epgId, epgId))
        }
        val learnedMappings = epgLearnedMappingDao.recent(5_000).associate { mapping ->
            mapping.normalizedOriginalName to mapping.epgId
        }
        EpgChannelLinkResolver(
            xmlTvChannels = refs.values.toList(),
            learnedMappings = learnedMappings,
            normalizer = channelNameNormalizer
        ).also { resolver ->
            val map = epgLinkResolversByPlaylist
                ?: mutableMapOf<Long, EpgChannelLinkResolver>().also { epgLinkResolversByPlaylist = it }
            map[playlistId] = resolver
            Log.i(
                EPG_FLOW_TAG,
                "EPG link resolver built playlistId=$playlistId sourceChannels=${sourceChannels.size} " +
                    "resolverChannels=${refs.size} cap=$MAX_RESOLVER_SOURCE_CHANNELS"
            )
        }
        }
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
        epgLinkResolversByPlaylist = null
        _epgDataRevision.update { it + 1 }
    }

    override fun profiles(): Flow<List<UserProfile>> = profileDao.observeProfiles().map { rows ->
        rows
            .filter { it.name != "Default" && it.name != GUEST_SCRATCH_PROFILE_NAME }
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
    }.flowOn(Dispatchers.IO)

    override suspend fun activeProfile(): UserProfile? {
        ensureDefaultProfile()
        return profileDao.getProfile(activeProfileId)?.let {
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

    override suspend fun prepareProfilePicker() {
        profileDao.deleteDefaultProfiles()
        ensureDefaultProfile()
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
                avatarColor = sanitizeProfileAvatarColorHex(avatarColor),
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

    override fun isGuestSession(): Boolean = guestSessionPreferences.isGuestSession()

    override suspend fun enterGuestSession() {
        ensureDefaultProfile()
        if (profileDao.getProfile(activeProfileId) == null) {
            val id = profileDao.upsertProfile(
                UserProfileEntity(
                    name = GUEST_SCRATCH_PROFILE_NAME,
                    avatarColor = sanitizeProfileAvatarColorHex(GUEST_PROFILE_AVATAR_COLOR)
                )
            )
            activeProfileId = id
            profileDao.setActive(ActiveProfileEntity(profileId = id))
        }
        if (profileSettingsDao.get(activeProfileId) == null) {
            profileSettingsDao.upsert(ProfileSettingsEntity(profileId = activeProfileId))
        }
        favoritesRepository.ensureDefaultGroups(activeProfileId)
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
        profileDao.upsertProfile(profile.copy(avatarColor = sanitizeProfileAvatarColorHex(avatarColor)))
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

    override fun activePlaylistId(): Flow<Long> = playlistContext.activePlaylistId

    override suspend fun setActivePlaylist(playlistId: Long) {
        playlistContext.setActive(playlistId)
    }

    override fun healthBest(limit: Int): Flow<List<StreamHealth>> = streamHealthDao.best(limit).map { rows ->
        rows.map { StreamHealth(it.channelId, it.reliabilityScore, it.averageLoadTimeMs, it.bufferEventsPerSession, it.lastSuccessfulLoad) }
    }.flowOn(Dispatchers.IO)

    override fun healthWorst(limit: Int): Flow<List<StreamHealth>> = streamHealthDao.worst(limit).map { rows ->
        rows.map { StreamHealth(it.channelId, it.reliabilityScore, it.averageLoadTimeMs, it.bufferEventsPerSession, it.lastSuccessfulLoad) }
    }.flowOn(Dispatchers.IO)

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
        finalizeChannelImport(playlistId)
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
            playlistContext.setActive(playlistId)
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
        channelDao.clearByPlaylist(playlistId)
        val seenUrls = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        var inserted = 0
        val liveStats = xtreamParser.parseLiveChannelsBatched(
            playlistId = playlistId,
            raw = liveRaw,
            username = username,
            password = password,
            serverUrl = normalizedServer,
            categories = categories,
        ) { batch ->
            inserted += insertParsedChannelBatches(playlistId, seenUrls, seenNames, batch)
        }
        Log.i(IPTV_REPO_LOG_TAG, "Parsed ${liveStats.parsedCount} live channels (${categories.size} categories)")
        val epgFromProvider = liveStats.epgFromProvider
        val epgFromStreamId = liveStats.epgFromStreamId
        Log.i(
            EPG_FLOW_TAG,
            "Xtream import epgId stats: epg_channel_id=$epgFromProvider stream_id_fallback=$epgFromStreamId " +
                "total=${liveStats.parsedCount}"
        )

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
        finalizeChannelImport(playlistId)
        // VOD catalog is refreshed asynchronously after import; never clear it here.

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
            seriesCategoryDao.clearByPlaylist(playlistId)
            seriesShowDao.clearByPlaylist(playlistId)
            database.vodCatalogEpisodeDao().deleteForPlaylist(playlistId)
            vodCatalogDiskCache.clear(playlistId)
            vodCatalogCacheManifestStore.clear(playlistId)
            bumpVodCatalogRevision()
            throw e
        }
        scheduleEpgImportWork(startedAt)
        playlistContext.setActive(playlistId)
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
        finalizeChannelImport(playlistId)
        scheduleEpgImportWork(startedAt)
        playlistContext.setActive(playlistId)
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
                finalizeChannelImport(playlistId)
                scheduleEpgImportWork(startedAt)
                playlistContext.setActive(playlistId)
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
        playlistFavoriteGroupDao.deleteAllForPlaylist(playlistId)
        playlistDao.delete(playlistId)
        vodStreamDao.clearByPlaylist(playlistId)
        vodCategoryDao.clearByPlaylist(playlistId)
        seriesCategoryDao.clearByPlaylist(playlistId)
        seriesShowDao.clearByPlaylist(playlistId)
        database.vodCatalogEpisodeDao().deleteForPlaylist(playlistId)
        vodCatalogDiskCache.clear(playlistId)
        vodCatalogCacheManifestStore.clear(playlistId)
        clearSeriesSeasonsForPlaylist(playlistId)
        bumpVodCatalogRevision()
    }

    override suspend fun refreshEpgNow(): EpgRefreshReport = epgRefreshMutex.withLock {
        StartupTrace.traceSuspend("refreshEpgNow") {
        if (!startupSafety.isInputSafe()) {
            Log.i(EPG_FLOW_TAG, "refreshEpgNow deferred — input safe not reached")
            return@traceSuspend EpgRefreshReport(playlistsTotal = 0, attempts = emptyList())
        }
        withContext(epgDispatchers.io) {
        epgDownloadTracker.setInProgress(true)
        try {
        Log.i(EPG_FLOW_TAG, "refreshEpgNow started on ${Thread.currentThread().name}")
        epgCache.clear()
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
                val epgStarted = SystemClock.elapsedRealtime()
                var programmesStored = 0
                var samplePrograms = emptyList<com.grid.tv.data.db.entity.ProgramEntity>()
                when (val fetchOutcome = remoteTextFetcher.fetchEpgXmlTv(
                    rawUrl = resolvedEpgUrl,
                    parser = xmlTvParser,
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    onProgramBatch = { batch ->
                        val scoped = batch.map { program ->
                            if (program.playlistId == playlist.id) program
                            else program.copy(playlistId = playlist.id)
                        }
                        programDao.insertAll(scoped)
                        if (samplePrograms.isEmpty()) samplePrograms = scoped.take(5)
                        programmesStored += scoped.size
                    },
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
                                "programmes=${fetchResult.parsed.programCount}"
                        )
                        attempt = attempt.copy(
                            httpCode = fetchResult.httpCode,
                            bytesReceived = fetchResult.rawBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        val parsed = fetchResult.parsed
                        attempt = attempt.copy(
                            channelsParsed = parsed.channelsById.size,
                            programmesParsed = parsed.programCount
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
                        if (sourceChannels.isNotEmpty() || programmesStored > 0) {
                            database.importEpgChannelsForPlaylist(
                                sourceKey = sourceKey,
                                sourceChannels = sourceChannels,
                                playlist = playlist,
                                refreshedAt = now,
                            )
                            channelsStored = sourceChannels.size
                            if (samplePrograms.isNotEmpty()) {
                                logImportedProgrammeWindowSample(samplePrograms, now)
                            }
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
                        PerformanceTelemetryTracker.epgRefresh(
                            playlistId = playlist.id,
                            elapsedMs = SystemClock.elapsedRealtime() - epgStarted,
                            programCount = programmesStored,
                        )
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
        if (reportWillImportData(attempts)) {
            epgLinkResolversByPlaylist = null
            epgJobCoordinator.scheduleResolverAfterImport(createdAfter = 0L)
            val programChannelCount = programDao.distinctChannelEpgIds().size
            Log.i(EPG_FLOW_TAG, "EPG link resolver rebuilt; $programChannelCount distinct programme channel ids in DB")
            val sampleChannels = mapChannelEntities(
                channelDao.channelsPage(
                    filterPlaylistId = -1L,
                    filterGroupName = null,
                    searchPrefix = "",
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
        } else {
            Log.i(EPG_FLOW_TAG, "EPG refresh imported no data — skipping link resolver rebuild")
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
    }

    private fun reportWillImportData(attempts: List<EpgFetchAttempt>): Boolean =
        attempts.any { it.programmesStored > 0 || it.channelsStored > 0 }

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
        loadVodStreamed(trigger)
    }

    override fun loadVodStreamed(trigger: VodRefreshTrigger) {
        if (LowEndDeviceMode.isEnabled() && trigger == VodRefreshTrigger.REPOSITORY_INIT) {
            Log.i(
                VOD_FLOW_TAG,
                "Skipping startup VOD load on low-end device trigger=$trigger — loads on VOD hub entry"
            )
            return
        }
        com.grid.tv.util.VodCatalogLogger.vodLoadStart(trigger.name)
        if (startupSafety.shouldDeferHeavyRepositoryWork(trigger)) {
            startupSafety.enqueueDeferredVodLoad(trigger)
            Log.i(
                VOD_FLOW_TAG,
                "loadVodStreamed deferred trigger=$trigger phase=${startupSafety.currentPhase()}"
            )
            return
        }
        loadVodStreamedInternal(trigger)
    }

    private fun loadVodStreamedInternal(trigger: VodRefreshTrigger) {
        vodRepositoryScope.launch {
            if (trigger != VodRefreshTrigger.MANUAL_RETRY) {
                startupSafety.awaitInputSafe()
            }
            startupDiskQueue.run("vod_local_warm_$trigger") {
                hydrateVodUiFromRoom(trigger)
                StartupProfiler.mark("vod_local_warm_start", trigger.name)
                if (!vodDiskCacheLoaded) {
                    warmLocalUiCacheMinimal()
                } else {
                    ensureVodDiskCacheLoaded()
                }
                StartupProfiler.mark("vod_local_warm_complete", trigger.name)
            }
            scheduleDeferredVodCatalogRefresh(
                trigger,
                immediate = trigger == VodRefreshTrigger.VOD_HUB_MOUNT ||
                    trigger == VodRefreshTrigger.MANUAL_RETRY
            )
        }
    }

    override fun startDeferredVodMaintenance(trigger: VodRefreshTrigger) {
        if (LowEndDeviceMode.isEnabled() && trigger == VodRefreshTrigger.REPOSITORY_INIT) {
            Log.i(
                VOD_FLOW_TAG,
                "Skipping startup VOD maintenance on low-end device trigger=$trigger — loads on VOD hub entry"
            )
            return
        }
        scheduleDeferredVodCatalogRefresh(trigger, immediate = false)
    }

    /** Publishes cached Room counts when allowed — never blocks startup Phase 1. */
    private suspend fun hydrateVodUiFromRoom(trigger: VodRefreshTrigger) {
        StartupProfiler.mark("vod_instant_hydrate_start", trigger.name)
        if (startupSafety.shouldDeferDbCountQueries()) {
            StartupProfiler.mark(
                "vod_instant_hydrate_skipped",
                "phase=${startupSafety.currentPhase()} trigger=$trigger"
            )
            return
        }
        if (sessionDbCountsLoaded) {
            StartupProfiler.mark(
                "vod_instant_hydrate_complete",
                "movies=${cachedMoviesCount()} series=${cachedSeriesCount()} cached=true"
            )
            return
        }
        val persisted = startupCatalogCountsStore.read()
        if (persisted.isValid) {
            sessionDbCountsLoaded = true
            applyCatalogCounts(
                counts = persisted.toSnapshot(CatalogCountSource.PERSISTED),
                trigger = trigger,
                persist = false,
                publishUi = true
            )
            StartupProfiler.mark(
                "vod_instant_hydrate_complete",
                "movies=${persisted.movies} series=${persisted.series} persisted=true"
            )
            return
        }
        refreshCatalogCountsFromDb(
            trigger = trigger,
            force = true,
            publishUi = true,
            persist = false
        )
        StartupProfiler.mark(
            "vod_instant_hydrate_complete",
            "movies=${cachedMoviesCount()} series=${cachedSeriesCount()}"
        )
    }

    override fun scheduleDeferredVodCatalogRefresh(trigger: VodRefreshTrigger) {
        scheduleDeferredVodCatalogRefresh(trigger, immediate = false)
    }

    private fun scheduleDeferredVodCatalogRefresh(trigger: VodRefreshTrigger, immediate: Boolean) {
        deferredVodCatalogRefreshJob?.cancel()
        deferredVodCatalogRefreshJob = vodRepositoryScope.launch {
            if (trigger != VodRefreshTrigger.MANUAL_RETRY) {
                startupSafety.awaitInputSafe()
            }
            val dbEmpty = startupDiskQueue.run("vod_refresh_db_empty_check") {
                cachedMoviesCount() <= 0 && cachedSeriesCount() <= 0
            }
            val persisted = startupCatalogCountsStore.read()
            val dbPopulated = persisted.movies >= STARTUP_VOD_SKIP_MIN_ROWS ||
                persisted.series >= STARTUP_VOD_SKIP_MIN_ROWS ||
                cachedMoviesCount() >= STARTUP_VOD_SKIP_MIN_ROWS ||
                cachedSeriesCount() >= STARTUP_VOD_SKIP_MIN_ROWS
            val delayMs = when {
                immediate -> 0L
                dbPopulated && trigger == VodRefreshTrigger.REPOSITORY_INIT ->
                    StartupTierPolicy.populatedCatalogVodRefreshDelayMs()
                dbEmpty && (
                    trigger == VodRefreshTrigger.VOD_HUB_MOUNT ||
                        trigger == VodRefreshTrigger.MANUAL_RETRY
                    ) -> StartupTierPolicy.emptyCatalogVodRefreshDelayMs()
                else -> StartupTierPolicy.deferredVodRefreshDelayMs(trigger)
            }
            StartupProfiler.mark("vod_refresh_scheduled", "delay=${delayMs}ms trigger=$trigger dbEmpty=$dbEmpty")
            delay(delayMs)
            if (playlistImportCoordinator.isImportActive()) {
                playlistImportCoordinator.deferVodRefresh("deferred_startup trigger=$trigger")
                publishVodProgressFromDb(trigger = trigger)
                return@launch
            }
            if (trigger != VodRefreshTrigger.MANUAL_RETRY && isVodCatalogFresh()) {
                Log.i(VOD_FLOW_TAG, "Skipping deferred VOD refresh — catalog still fresh trigger=$trigger")
                publishVodProgressFromDb(trigger = trigger)
                return@launch
            }
            if (shouldSkipStartupVodNetworkRefresh(trigger)) {
                refreshSeriesCategoriesIfMissing(trigger)
                refreshVodCategoriesIfMissing(trigger)
                repairStoredCategoryNames()
                publishVodProgressFromDb(trigger)
                StartupProfiler.mark("vod_refresh_skipped", trigger.name)
                return@launch
            }
            try {
                StartupProfiler.mark("vod_refresh_start", trigger.name)
                refreshSeriesCategoriesIfMissing(trigger)
                refreshVodCategoriesIfMissing(trigger)
                repairStoredCategoryNames()
                refreshVodSeriesCatalog(trigger = trigger, force = trigger == VodRefreshTrigger.MANUAL_RETRY)
                StartupProfiler.mark("vod_refresh_complete", trigger.name)
            } catch (error: Throwable) {
                Log.e(
                    VOD_FLOW_TAG,
                    "Deferred VOD refresh failed trigger=$trigger: ${error.message}",
                    error
                )
            }
        }
    }

    override suspend fun warmLocalUiCacheMinimal() {
        ensureRepositoryBootstrap()
        StartupProfiler.mark("repository_warm_minimal_start")
        ensureDefaultProfile()
        loadSettings()

        val persisted = startupCatalogCountsStore.read()
        if (persisted.isValid) {
            sessionDbCountsLoaded = true
            applyCatalogCounts(
                counts = persisted.toSnapshot(CatalogCountSource.PERSISTED),
                trigger = VodRefreshTrigger.REPOSITORY_INIT,
                persist = false,
                publishUi = true
            )
            StartupProfiler.mark(
                "catalog_counts_instant",
                "movies=${persisted.movies} series=${persisted.series} channels=${persisted.channels}"
            )
        }
        vodDiskCacheLoaded = true
        if (persisted.isValid && persisted.channels > 0) {
            runCatching { warmGuideBootstrapChannelPage(cachedSettings) }
                .onFailure {
                    Log.w(IPTV_REPO_LOG_TAG, "guide bootstrap channel warm failed: ${it.message}")
                }
        }
        StartupProfiler.mark("repository_warm_minimal_complete")
    }

    override fun getCachedCatalogCounts(): CachedCatalogCounts {
        sessionCatalogCounts?.let {
            return CachedCatalogCounts(
                movies = it.movies,
                series = it.series,
                channels = it.channels,
                isValid = true
            )
        }
        val persisted = startupCatalogCountsStore.read()
        return CachedCatalogCounts(
            movies = persisted.movies,
            series = persisted.series,
            channels = persisted.channels,
            isValid = persisted.isValid
        )
    }

    override fun applyCachedCatalogCountsAtStartup() {
        ensureRepositoryBootstrap()
        val cached = getCachedCatalogCounts()
        StartupProfiler.mark(
            "phase2_cache_used",
            "movies=${cached.movies} series=${cached.series} channels=${cached.channels} " +
                "valid=${cached.isValid}"
        )
        if (!cached.isValid) return
        sessionDbCountsLoaded = true
        applyCatalogCounts(
            counts = CatalogCountsSnapshot(
                movies = cached.movies,
                series = cached.series,
                channels = cached.channels,
                updatedAtMs = System.currentTimeMillis(),
                source = CatalogCountSource.PERSISTED
            ),
            trigger = VodRefreshTrigger.REPOSITORY_INIT,
            persist = false,
            publishUi = true
        )
    }

    override suspend fun updateCountsInBackground() {
        ensureDefaultProfile()
        loadSettings()

        val persisted = startupCatalogCountsStore.read()
        val previous = sessionCatalogCounts
        val counts = if (persisted.isValid) {
            StartupProfiler.mark(
                "phase2_count_query_skipped",
                "movies=${persisted.movies} series=${persisted.series} channels=${persisted.channels}"
            )
            Log.i(
                IPTV_REPO_LOG_TAG,
                "updateCountsInBackground: using persisted catalog counts " +
                    "(movies=${persisted.movies} series=${persisted.series} channels=${persisted.channels})"
            )
            persisted.toSnapshot(CatalogCountSource.PERSISTED)
        } else {
            StartupProfiler.mark("phase2_count_query_start", "mode=channels_only")
            val channelsStart = SystemClock.elapsedRealtime()
            val channels = queryChannelCountFromDb()
            val channelsMs = SystemClock.elapsedRealtime() - channelsStart
            StartupProfiler.mark(
                "phase2_count_query_end",
                "mode=channels_only total=${channelsMs}ms channels=${channelsMs}ms($channels)"
            )
            Log.i(
                IPTV_REPO_LOG_TAG,
                "updateCountsInBackground: first-run channel count only=${channels}ms/$channels " +
                    "(movie/series totals refresh after VOD sync)"
            )
            CatalogCountsSnapshot(
                movies = 0,
                series = 0,
                channels = channels,
                updatedAtMs = System.currentTimeMillis(),
                source = CatalogCountSource.DATABASE
            )
        }
        sessionDbCountsLoaded = persisted.isValid
        val publishUi = previous == null ||
            previous.movies != counts.movies ||
            previous.series != counts.series ||
            previous.channels != counts.channels
        applyCatalogCounts(
            counts = counts,
            trigger = VodRefreshTrigger.REPOSITORY_INIT,
            persist = !persisted.isValid,
            publishUi = publishUi
        )

        if (counts.channels > 0 && !startupSafety.shouldDeferChannelPageWarm()) {
            yield()
            delay(50)
            if (guideBootstrapChannelCache == null) {
                runCatching { warmGuideBootstrapChannelPage(cachedSettings) }
                    .onFailure {
                        Log.w(IPTV_REPO_LOG_TAG, "guide bootstrap channel warm failed: ${it.message}")
                    }
            }
        }
        Log.i(
            IPTV_REPO_LOG_TAG,
            "updateCountsInBackground complete profileId=$activeProfileId " +
                "channels=${counts.channels} movies=${counts.movies} series=${counts.series} " +
                "source=${counts.source}"
        )
        appCacheRegistry.logInventory("warm_local_ui_cache")
    }

    override suspend fun warmLocalUiCache() {
        StartupTrace.traceSuspend("warmLocalUiCache") {
            warmLocalUiCacheMinimal()
        }
    }

    override fun ensureSeriesCatalogForTab(trigger: VodRefreshTrigger) {
        vodRepositoryScope.launch {
            try {
                refreshSeriesCategoriesIfMissing(trigger)
                val playlistId = playlistContext.activePlaylistId.value
                if (playlistId <= 0L) return@launch
                val playlistSeriesCount = seriesShowDao.countByPlaylist(playlistId)
                val hydrationState = seriesCatalogHydrationStore.resolveState(
                    playlistId = playlistId,
                    seriesCountOnDisk = playlistSeriesCount,
                )
                val manualRetry = trigger == VodRefreshTrigger.MANUAL_RETRY
                val userSelectedSeriesTab = trigger == VodRefreshTrigger.SERIES_VIEW_MODEL
                if (!manualRetry &&
                    !userSelectedSeriesTab &&
                    hydrationState != SeriesCatalogHydrationState.NEVER_FETCHED
                ) {
                    Log.i(
                        VOD_FLOW_TAG,
                        "Skipping series tab auto-fetch hydration=$hydrationState playlist=$playlistId"
                    )
                    return@launch
                }
                if (playlistSeriesCount > 0) {
                    publishVodProgressFromDb(trigger)
                    return@launch
                }
                if (playlistSeriesCount <= 0) {
                    refreshVodSeriesCatalog(
                        trigger = trigger,
                        force = manualRetry || userSelectedSeriesTab,
                    )
                }
            } catch (error: Throwable) {
                Log.e(
                    VOD_FLOW_TAG,
                    "ensureSeriesCatalogForTab failed trigger=$trigger: ${error.message}",
                    error
                )
            }
        }
    }

    override suspend fun refreshVodSeriesCatalog(
        trigger: VodRefreshTrigger,
        force: Boolean
    ) = withContext(Dispatchers.IO) {
        val callAtMs = System.currentTimeMillis()
        val dbMovies = cachedMoviesCount()
        val dbSeries = cachedSeriesCount()
        if (!force && isSystemLowOnMemory()) {
            Log.w(VOD_FLOW_TAG, "Skipping VOD refresh — system low on memory trigger=$trigger")
            publishVodProgressFromDb(trigger = trigger)
            return@withContext
        }
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
            publishVodProgressFromDb(trigger = trigger)
            return@withContext
        }

        activeVodRefresh?.takeIf { it.isActive }?.let { inFlight ->
            Log.i(VOD_FLOW_TAG, "Coalescing duplicate refresh trigger=$trigger with in-flight request")
            inFlight.await()
            return@withContext
        }

        if (!force && isVodCatalogFresh()) {
            refreshSeriesCategoriesIfMissing(trigger)
            refreshVodCategoriesIfMissing(trigger)
            Log.i(
                VOD_FLOW_TAG,
                "Skipping network refresh trigger=$trigger — cache fresh within TTL " +
                    "(movies=$dbMovies, series=$dbSeries)"
            )
            publishVodProgressFromDb(trigger = trigger)
            return@withContext
        }

        if (!force && shouldSkipStartupVodNetworkRefresh(trigger)) {
            refreshSeriesCategoriesIfMissing(trigger)
            refreshVodCategoriesIfMissing(trigger)
            repairStoredCategoryNames()
            publishVodProgressFromDb(trigger = trigger)
            return@withContext
        }

        val completion = CompletableDeferred<Unit>()
        activeVodRefresh = completion
        try {
            startupHeavyWorkMutex.withLock {
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
            val hydrated = tryHydrateVodCatalogFromDiskCache(VodRefreshTrigger.REPOSITORY_INIT)
            val dbMovies = cachedMoviesCount()
            val dbSeries = cachedSeriesCount()
            vodDiskCacheLoaded = true
            if (hydrated) {
                Log.i(
                    VOD_FLOW_TAG,
                    "VOD catalog hydrated from disk cache movies=$dbMovies series=$dbSeries"
                )
            } else if (dbMovies > 0 || dbSeries > 0) {
                Log.i(
                    VOD_FLOW_TAG,
                    "VOD catalog available in DB movies=$dbMovies series=$dbSeries (lazy paging — no bulk load)"
                )
            } else if (!sessionDbCountsLoaded) {
                if (startupSafety.shouldDeferDbCountQueries()) return
                val counts = refreshCatalogCountsFromDb(
                    trigger = VodRefreshTrigger.REPOSITORY_INIT,
                    force = true,
                    publishUi = false,
                    persist = true
                )
                if (counts.movies > 0 || counts.series > 0) {
                    Log.i(
                        VOD_FLOW_TAG,
                        "VOD catalog available in DB movies=${counts.movies} series=${counts.series} " +
                            "(lazy paging — no bulk load)"
                    )
                }
            }
            seedVodRefreshBaselineFromManifest()
        }
    }

    private suspend fun tryHydrateVodCatalogFromDiskCache(trigger: VodRefreshTrigger): Boolean {
        loadSettings()
        val playlists = playlistDao.all().filter { it.type == PlaylistType.XTREAM.name }
        if (playlists.isEmpty()) return false

        var hydratedAny = false
        for (playlist in playlists) {
            val manifest = vodCatalogCacheManifestStore.read(playlist.id) ?: continue
            if (manifest.playlistVersionKey != vodPlaylistCacheVersionKey(playlist)) continue

            val roomMovies = vodStreamDao.countByPlaylist(playlist.id)
            val roomSeries = seriesShowDao.countByPlaylist(playlist.id)

            if (roomMovies <= 0 && manifest.moviesCount > 0) {
                hydratedAny = hydrateMoviesFromDiskCache(playlist, trigger) || hydratedAny
            }
            if (roomSeries <= 0 && manifest.seriesCount > 0) {
                hydratedAny = hydrateSeriesFromDiskCache(playlist, trigger) || hydratedAny
            }
        }

        if (!hydratedAny) return false

        lastVodRefreshCompletedAtMs = vodCatalogCacheManifestStore.latestSavedAtMs()
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        refreshCatalogCountsFromDb(
            trigger = trigger,
            force = true,
            publishUi = true,
            persist = true,
        )
        publishVodProgressFromDb(trigger)
        bumpVodCatalogRevision()
        return true
    }

    private suspend fun hydrateMoviesFromDiskCache(
        playlist: PlaylistEntity,
        trigger: VodRefreshTrigger,
    ): Boolean {
        val snapshot = vodCatalogDiskCache.load(playlist.id)
        val movies = snapshot?.movies.orEmpty()
        if (movies.isEmpty()) {
            val rawFile = vodCatalogDiskCache.rawCatalogFile(playlist.id, "movies")
            if (!rawFile.exists()) return false
            return ingestMoviesFromLocalCacheFile(playlist, rawFile, trigger)
        }
        val syncGeneration = nextVodSyncGeneration()
        var batchIndex = 0
        vodCatalogDiskCache.hydrateMoviesBatches(movies) { batch ->
            startupDiskQueue.run("vod_disk_hydrate_movies_$batchIndex") {
                vodStreamDao.insertAll(batch.map { it.toEntity(syncGeneration) })
            }
            batchIndex++
        }
        return true
    }

    private suspend fun hydrateSeriesFromDiskCache(
        playlist: PlaylistEntity,
        trigger: VodRefreshTrigger,
    ): Boolean {
        val snapshot = vodCatalogDiskCache.load(playlist.id)
        val series = snapshot?.series.orEmpty()
        if (series.isEmpty()) {
            val rawFile = vodCatalogDiskCache.rawCatalogFile(playlist.id, "series")
            if (!rawFile.exists()) return false
            return ingestSeriesFromLocalCacheFile(playlist, rawFile, trigger)
        }
        val syncGeneration = nextVodSyncGeneration()
        var batchIndex = 0
        vodCatalogDiskCache.hydrateSeriesBatches(series) { batch ->
            startupDiskQueue.run("vod_disk_hydrate_series_$batchIndex") {
                seriesShowDao.insertAll(batch.map { it.toEntity(syncGeneration) })
            }
            batchIndex++
        }
        seriesCatalogHydrationStore.setState(
            playlist.id,
            com.grid.tv.domain.model.SeriesCatalogHydrationState.POPULATED,
        )
        return true
    }

    private suspend fun ingestMoviesFromLocalCacheFile(
        playlist: PlaylistEntity,
        catalogFile: File,
        trigger: VodRefreshTrigger,
    ): Boolean {
        val credentials = resolveVodFetchCredentials(playlist) ?: return false
        val (server, user, pass) = credentials
        val syncGeneration = nextVodSyncGeneration()
        var parsedCount = 0
        val ingestBatchSize = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.DEFAULT_BATCH_SIZE
        val streamResult = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.parseVodCatalogStream(
            input = catalogFile.inputStream(),
            username = user,
            password = pass,
            serverUrl = server,
            playlistId = playlist.id,
            batchSize = ingestBatchSize,
            parser = xtreamParser,
        ) { batch, batchIndex, _ ->
            if (batch.isEmpty()) return@parseVodCatalogStream
            startupDiskQueue.run("vod_disk_hydrate_movies_$batchIndex") {
                vodStreamDao.insertAll(batch.map { it.toEntity(syncGeneration) })
            }
            parsedCount += batch.size
        }
        if (parsedCount <= 0) return false
        vodStreamDao.deleteStaleByPlaylist(playlist.id, syncGeneration)
        refreshCatalogCountsFromDb(trigger = trigger, force = true, publishUi = true, persist = true)
        return true
    }

    private suspend fun ingestSeriesFromLocalCacheFile(
        playlist: PlaylistEntity,
        catalogFile: File,
        trigger: VodRefreshTrigger,
    ): Boolean {
        val syncGeneration = nextVodSyncGeneration()
        var parsedCount = 0
        val ingestBatchSize = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.DEFAULT_BATCH_SIZE
        clearSeriesSeasonsForPlaylist(playlist.id)
        val streamResult = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.parseSeriesCatalogStream(
            input = catalogFile.inputStream(),
            playlistId = playlist.id,
            batchSize = ingestBatchSize,
            parser = xtreamParser,
        ) { batch, batchIndex, _ ->
            if (batch.isEmpty()) return@parseSeriesCatalogStream
            startupDiskQueue.run("vod_disk_hydrate_series_$batchIndex") {
                seriesShowDao.insertAll(batch.map { it.toEntity(syncGeneration) })
            }
            parsedCount += batch.size
        }
        parsedCount = streamResult.parsedCount
        if (parsedCount <= 0) return false
        seriesShowDao.deleteStaleByPlaylist(playlist.id, syncGeneration)
        seriesCatalogHydrationStore.setState(
            playlist.id,
            com.grid.tv.domain.model.SeriesCatalogHydrationState.POPULATED,
        )
        refreshCatalogCountsFromDb(trigger = trigger, force = true, publishUi = true, persist = true)
        return true
    }

    private fun scheduleParsedVodSnapshotExport(playlistId: Long, savedAtMs: Long) {
        vodRepositoryScope.launch {
            try {
                exportVodSnapshotsToDiskCache(playlistId, savedAtMs)
            } catch (error: Throwable) {
                Log.w(
                    VOD_FLOW_TAG,
                    "VOD parsed snapshot export failed playlist=$playlistId: ${error.message}",
                    error,
                )
            }
        }
    }

    private suspend fun exportVodSnapshotsToDiskCache(playlistId: Long, savedAtMs: Long) {
        startupDiskQueue.run("vod_snapshot_export_$playlistId") {
            val movies = buildList {
                var offset = 0
                while (true) {
                    val page = vodStreamDao.pageByPlaylist(
                        playlistId,
                        VodCatalogDiskCache.HYDRATE_BATCH_SIZE,
                        offset,
                    )
                    if (page.isEmpty()) break
                    addAll(page.map { it.toDomain() })
                    offset += page.size
                }
            }
            if (movies.isNotEmpty()) {
                vodCatalogDiskCache.saveMovies(playlistId, movies, savedAtMs)
            }

            val series = buildList {
                var offset = 0
                while (true) {
                    val page = seriesShowDao.pageByPlaylist(
                        playlistId,
                        VodCatalogDiskCache.HYDRATE_BATCH_SIZE,
                        offset,
                    )
                    if (page.isEmpty()) break
                    addAll(page.map { it.toDomain() })
                    offset += page.size
                }
            }
            if (series.isNotEmpty()) {
                vodCatalogDiskCache.saveSeries(playlistId, series, savedAtMs)
            }
        }
    }

    private suspend fun persistVodDiskCacheAfterMoviesIngest(
        playlist: PlaylistEntity,
        catalogFile: File,
        parsedCount: Int,
    ) {
        val versionKey = vodPlaylistCacheVersionKey(playlist)
        val savedAtMs = System.currentTimeMillis()
        startupDiskQueue.run("vod_cache_write_movies_${playlist.id}") {
            val fingerprint = vodCatalogDiskCache.persistRawCatalog(playlist.id, "movies", catalogFile)
            vodCatalogCacheManifestStore.merge(
                playlistId = playlist.id,
                playlistVersionKey = versionKey,
                savedAtMs = savedAtMs,
                moviesCount = parsedCount,
                moviesContentFingerprint = fingerprint,
            )
        }
        scheduleParsedVodSnapshotExport(playlist.id, savedAtMs)
    }

    private suspend fun persistVodDiskCacheAfterSeriesIngest(
        playlist: PlaylistEntity,
        catalogFile: File,
        parsedCount: Int,
    ) {
        val versionKey = vodPlaylistCacheVersionKey(playlist)
        val savedAtMs = System.currentTimeMillis()
        startupDiskQueue.run("vod_cache_write_series_${playlist.id}") {
            val fingerprint = vodCatalogDiskCache.persistRawCatalog(playlist.id, "series", catalogFile)
            vodCatalogCacheManifestStore.merge(
                playlistId = playlist.id,
                playlistVersionKey = versionKey,
                savedAtMs = savedAtMs,
                seriesCount = parsedCount,
                seriesContentFingerprint = fingerprint,
            )
        }
        scheduleParsedVodSnapshotExport(playlist.id, savedAtMs)
    }

    private suspend fun isVodCatalogFresh(): Boolean {
        if (canSkipVodNetworkRefreshFromDiskCache()) {
            seedVodRefreshBaselineFromManifest()
            return true
        }
        if (lastVodRefreshCompletedAtMs <= 0L) return false
        val ageMs = System.currentTimeMillis() - lastVodRefreshCompletedAtMs
        if (ageMs >= VOD_CACHE_TTL_MS) return false
        val movies = cachedMoviesCount()
        val series = cachedSeriesCount()
        if (movies <= 0 && series <= 0) return false
        // Movies on disk but series never ingested — not a complete catalog.
        if (movies > 0 && series <= 0) {
            val status = vodCatalogStatus.value
            if (status.seriesRawLength == 0 && status.seriesError.isNullOrBlank()) {
                return false
            }
        }
        return true
    }

    private fun isSystemLowOnMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory().coerceAtLeast(1L)
        return used.toDouble() / max.toDouble() >= 0.85
    }

    private suspend fun needsSeriesCategoriesRefresh(): Boolean {
        if (seriesCategoryDao.countTotal() > 0) return false
        return playlistDao.all().any { it.type == PlaylistType.XTREAM.name }
    }

    private suspend fun needsVodCategoriesRefresh(): Boolean =
        cachedMoviesCount() > 0 && vodCategoryDao.countTotal() == 0

    private suspend fun refreshSeriesCategoriesIfMissing(trigger: VodRefreshTrigger) {
        if (!needsSeriesCategoriesRefresh()) return
        loadSettings()
        playlistDao.all()
            .filter { it.type == PlaylistType.XTREAM.name }
            .forEach { playlist ->
                refreshSeriesCategoriesForPlaylist(playlist, trigger)
            }
    }

    private suspend fun refreshVodCategoriesIfMissing(trigger: VodRefreshTrigger) {
        if (!needsVodCategoriesRefresh()) return
        loadSettings()
        playlistDao.all()
            .filter { it.type == PlaylistType.XTREAM.name }
            .forEach { playlist ->
                val credentials = resolveVodFetchCredentials(playlist)
                if (credentials == null) {
                    backfillVodCategoriesFromStreams(playlist.id)
                    return@forEach
                }
                val (server, user, pass) = credentials
                refreshVodCategoriesForPlaylist(playlist, server, user, pass, trigger)
            }
    }

    private suspend fun refreshSeriesCategoriesForPlaylist(
        playlist: PlaylistEntity,
        trigger: VodRefreshTrigger
    ) {
        val credentials = resolveVodFetchCredentials(playlist)
        if (credentials == null) {
            backfillSeriesCategoriesFromShows(playlist.id)
            return
        }
        val (server, user, pass) = credentials
        runVodPipelineCatching("refreshSeriesCategoriesForPlaylist playlist=${playlist.id}") {
            Log.i(
                VOD_FLOW_TAG,
                "Starting get_series_categories playlist=${playlist.id} trigger=$trigger " +
                    "at=${System.currentTimeMillis()}"
            )
            val seriesCategoriesRaw = remoteTextFetcher.fetch(
                buildXtreamApiUrl(server, user, pass, action = "get_series_categories")
            )
            Log.d(
                VOD_FLOW_TAG,
                "get_series_categories raw sample playlist=${playlist.id}: " +
                    seriesCategoriesRaw.take(2000).replace('\n', ' ')
            )
            val categories = xtreamParser.parseSeriesCategories(seriesCategoriesRaw, playlist.id)
            categories.take(8).forEach { category ->
                Log.d(
                    VOD_FLOW_TAG,
                    "get_series_categories parsed playlist=${playlist.id} " +
                        "id=${category.id} name=${category.name}"
                )
            }
            Log.i(
                VOD_FLOW_TAG,
                "Series categories playlist=${playlist.id} trigger=$trigger count=${categories.size}"
            )
            if (categories.isNotEmpty()) {
                val resolved = resolveCategoriesForStorage(categories, playlist.id)
                resolved.take(8).forEach { category ->
                    Log.d(
                        VOD_FLOW_TAG,
                        "get_series_categories resolved playlist=${playlist.id} " +
                            "id=${category.id} name=${category.name}"
                    )
                }
                database.replaceSeriesCategoriesForPlaylist(
                    playlistId = playlist.id,
                    categories = resolved.map { it.toSeriesCategoryEntity() }
                )
                bumpVodCatalogRevision()
                repairStoredCategoryNames()
            } else {
                backfillSeriesCategoriesFromShows(playlist.id)
            }
        }.onFailure { categoryError ->
            Log.w(
                VOD_FLOW_TAG,
                "Series categories fetch failed playlist=${playlist.id} trigger=$trigger — " +
                    "falling back to show category ids",
                categoryError
            )
            backfillSeriesCategoriesFromShows(playlist.id)
        }
    }

    private suspend fun backfillSeriesCategoriesFromShows(playlistId: Long) {
        val pairs = seriesShowDao.distinctCategoryPairs().filter { it.playlistId == playlistId }
        if (pairs.isEmpty()) return
        val lookup = buildCategoryNameLookup()
        Log.d(
            VOD_FLOW_TAG,
            "backfillSeriesCategoriesFromShows playlist=$playlistId pairs=${pairs.size} " +
                "lookupSize=${lookup.size} lookupSample=${lookup.entries.take(12)}"
        )
        pairs.take(8).forEach { row ->
            Log.d(
                VOD_FLOW_TAG,
                "backfillSeriesCategoriesFromShows pair playlist=${row.playlistId} " +
                    "categoryId=${row.categoryId} lookupId=${lookup[row.categoryId]} " +
                    "lookupComposite=${lookup["${row.playlistId}_${row.categoryId}"]}"
            )
        }
        seriesCategoryDao.clearByPlaylist(playlistId)
        seriesCategoryDao.insertAll(
            pairs.map { row ->
                SeriesCategoryEntity(
                    playlistId = row.playlistId,
                    categoryId = row.categoryId,
                    name = VodCategoryNameResolver.resolveDisplayName(
                        categoryId = row.categoryId,
                        storedName = lookupCategoryLabel(lookup, row.playlistId, row.categoryId),
                        playlistId = row.playlistId,
                        lookupById = lookup
                    )
                )
            }
        )
        bumpVodCatalogRevision()
        repairStoredCategoryNames()
        Log.i(
            VOD_FLOW_TAG,
            "Backfilled ${pairs.size} series categories from show rows for playlist=$playlistId"
        )
    }

    private suspend fun backfillVodCategoriesFromStreams(playlistId: Long) {
        val pairs = vodStreamDao.distinctCategoryPairs().filter { it.playlistId == playlistId }
        if (pairs.isEmpty()) return
        val lookup = buildCategoryNameLookup()
        Log.d(
            VOD_FLOW_TAG,
            "backfillVodCategoriesFromStreams playlist=$playlistId pairs=${pairs.size} " +
                "lookupSize=${lookup.size} lookupSample=${lookup.entries.take(12)}"
        )
        pairs.take(8).forEach { row ->
            Log.d(
                VOD_FLOW_TAG,
                "backfillVodCategoriesFromStreams pair playlist=${row.playlistId} " +
                    "categoryId=${row.categoryId} lookupId=${lookup[row.categoryId]} " +
                    "lookupComposite=${lookup["${row.playlistId}_${row.categoryId}"]}"
            )
        }
        vodCategoryDao.clearByPlaylist(playlistId)
        vodCategoryDao.insertAll(
            pairs.map { row ->
                VodCategoryEntity(
                    playlistId = row.playlistId,
                    categoryId = row.categoryId,
                    name = VodCategoryNameResolver.resolveDisplayName(
                        categoryId = row.categoryId,
                        storedName = lookupCategoryLabel(lookup, row.playlistId, row.categoryId),
                        playlistId = row.playlistId,
                        lookupById = lookup
                    )
                )
            }
        )
        bumpVodCatalogRevision()
        repairStoredCategoryNames()
        Log.i(
            VOD_FLOW_TAG,
            "Backfilled ${pairs.size} movie categories from stream rows for playlist=$playlistId"
        )
    }

    private suspend fun buildStreamFallbackSeriesCategoriesRaw(): List<VodCategory> =
        VodCategoryGuards.sanitizeStreamBacked(
            seriesShowDao.distinctCategoryPairs().map { row ->
                VodCategory(
                    id = row.categoryId,
                    name = row.categoryId,
                    playlistId = row.playlistId
                )
            }
        )

    private suspend fun buildSeriesGenreHintCategoriesRaw(): List<VodCategory> =
        VodCategoryGuards.sanitizeStreamBacked(
            seriesShowDao.distinctCategoryGenreHints().mapNotNull { row ->
                val genre = row.genre.trim()
                if (genre.isBlank()) return@mapNotNull null
                VodCategory(
                    id = row.categoryId,
                    name = genre,
                    playlistId = row.playlistId
                )
            }
        )

    private suspend fun mergeAllSeriesCategorySources(stored: List<VodCategory>): List<VodCategory> =
        VodCategoryNameResolver.mergeSeriesCategorySources(
            stored,
            buildStreamFallbackSeriesCategoriesRaw(),
            buildSeriesGenreHintCategoriesRaw()
        )

    private suspend fun buildFallbackSeriesCategories(): List<VodCategory> {
        val lookup = buildCategoryNameLookup()
        return buildStreamFallbackSeriesCategoriesRaw().map { category ->
            category.copy(
                name = resolvedCategoryDisplayName(
                    categoryId = category.id,
                    storedName = lookupCategoryLabel(lookup, category.playlistId, category.id),
                    playlistId = category.playlistId,
                    lookup = lookup
                )
            )
        }
    }

    private suspend fun buildFallbackVodCategories(): List<VodCategory> {
        val lookup = buildCategoryNameLookup()
        return VodCategoryGuards.sanitizeStreamBacked(
            vodStreamDao.distinctCategoryPairs().map { row ->
                VodCategory(
                    id = row.categoryId,
                    name = resolvedCategoryDisplayName(
                        categoryId = row.categoryId,
                        storedName = lookupCategoryLabel(lookup, row.playlistId, row.categoryId),
                        playlistId = row.playlistId,
                        lookup = lookup
                    ),
                    playlistId = row.playlistId
                )
            }
        )
    }

    private fun invalidateCategoryLookupCache() {
        cachedCategoryLookup = null
        cachedCategoryLookupRevision = -1L
    }

    private fun lookupCategoryLabel(
        lookup: Map<String, String>,
        playlistId: Long,
        categoryId: String
    ): String = lookup[categoryKey(playlistId, categoryId)]
        ?: lookup[categoryId]
        ?: categoryId

    private suspend fun vodStreamsForCategory(playlistId: Long, categoryId: String, limit: Int) =
        vodStreamDao.byCategoryForPlaylist(playlistId, categoryId, limit)

    private suspend fun seriesShowsForCategory(playlistId: Long, categoryId: String, limit: Int) =
        seriesShowDao.byCategoryForPlaylist(playlistId, categoryId, limit)

    private fun Long?.isPlaylistScoped(): Boolean = this != null && this > 0L

    private fun scopedPlaylistId(explicit: Long?): Long? =
        playlistContext.resolveOrNull(explicit)

    private fun requireScopedPlaylistId(explicit: Long): Long? =
        playlistContext.resolve(explicit).takeIf { it > 0L }

    private fun logCategoryKey(playlistId: Long, categoryId: String) {
        Log.d(CATEGORY_KEY_LOG_TAG, "playlistId=$playlistId categoryId=$categoryId key=${categoryKey(playlistId, categoryId)}")
    }

    private suspend fun buildCategoryNameLookup(): Map<String, String> {
        val revision = _vodCatalogRevision.value
        cachedCategoryLookup?.takeIf { cachedCategoryLookupRevision == revision }?.let { return it }
        return withContext(Dispatchers.IO) {
            val lookup = buildCategoryNameLookupUncached()
            cachedCategoryLookup = lookup
            cachedCategoryLookupRevision = revision
            lookup
        }
    }

    /** Room-backed category names only — no disk JSON parse on navigation paths.
     *
     * Lookup merge order (later tables augment labels only; keys are always stream-backed ids):
     * 1. Vod API stored categories ([VodCategoryDao])
     * 2. Series API stored categories ([SeriesCategoryDao])
     * 3. Stream-derived genre hints ([distinctCategoryGenreHints] — never new category ids)
     */
    private suspend fun buildCategoryNameLookupUncached(): Map<String, String> {
        val tables = mutableListOf<Map<String, String>>()
        tables += VodCategoryNameResolver.buildLookupTable(
            vodCategoryDao.all().map { it.toDomain() }
        )
        tables += VodCategoryNameResolver.buildLookupTable(
            seriesCategoryDao.all().map { it.toDomain() }
        )
        val genreHints = linkedMapOf<String, String>()
        vodStreamDao.distinctCategoryGenreHints().forEach { row ->
            if (!VodCategoryGuards.isStreamBackedCategoryId(row.categoryId)) return@forEach
            val genre = row.genre.trim()
            if (genre.isBlank() || VodCategoryNameResolver.isUnresolvedName(row.categoryId, genre)) {
                return@forEach
            }
            val key = categoryKey(row.playlistId, row.categoryId)
            genreHints[key] = genre
            genreHints.putIfAbsent(row.categoryId, genre)
            logCategoryKey(row.playlistId, row.categoryId)
        }
        seriesShowDao.distinctCategoryGenreHints().forEach { row ->
            if (!VodCategoryGuards.isStreamBackedCategoryId(row.categoryId)) return@forEach
            val genre = row.genre.trim()
            if (genre.isBlank() || VodCategoryNameResolver.isUnresolvedName(row.categoryId, genre)) {
                return@forEach
            }
            val key = categoryKey(row.playlistId, row.categoryId)
            genreHints[key] = genre
            genreHints.putIfAbsent(row.categoryId, genre)
            logCategoryKey(row.playlistId, row.categoryId)
        }
        if (genreHints.isNotEmpty()) {
            tables += genreHints
        }
        return VodCategoryNameResolver.mergeLookupTables(*tables.toTypedArray())
    }

    private suspend fun resolveCategoriesForStorage(
        categories: List<VodCategory>,
        playlistId: Long
    ): List<VodCategory> {
        val lookup = buildCategoryNameLookup()
        return categories.map { category ->
            category.copy(
                name = resolvedCategoryDisplayName(
                    categoryId = category.id,
                    storedName = category.name,
                    playlistId = playlistId,
                    lookup = lookup
                )
            )
        }
    }

    private fun resolvedCategoryDisplayName(
        categoryId: String,
        storedName: String,
        playlistId: Long,
        lookup: Map<String, String>
    ): String = VodCategoryNameResolver.resolveDisplayName(
        categoryId = categoryId,
        storedName = storedName,
        playlistId = playlistId,
        lookupById = lookup
    )

    private suspend fun resolveCategoriesForDisplay(categories: List<VodCategory>): List<VodCategory> {
        if (categories.isEmpty()) return categories
        val streamBacked = VodCategoryGuards.sanitizeStreamBacked(categories)
        val lookup = buildCategoryNameLookup()
        return streamBacked.map { category ->
            VodCategoryNameResolver.withResolvedNames(category, lookup)
        }
    }

    private suspend fun repairStoredCategoryNames() {
        val lookup = buildCategoryNameLookup()
        Log.d(
            VOD_FLOW_TAG,
            "repairStoredCategoryNames lookupSize=${lookup.size} lookupSample=${lookup.entries.take(12)}"
        )
        val vodUpdates = vodCategoryDao.all().mapNotNull { entity ->
            val resolved = resolvedCategoryDisplayName(
                categoryId = entity.categoryId,
                storedName = entity.name,
                playlistId = entity.playlistId,
                lookup = lookup
            )
            if (resolved != entity.name) entity.copy(name = resolved) else null
        }
        if (vodUpdates.isNotEmpty()) {
            vodCategoryDao.insertAll(vodUpdates)
            bumpVodCatalogRevision()
        }

        val seriesUpdates = seriesCategoryDao.all().mapNotNull { entity ->
            val resolved = resolvedCategoryDisplayName(
                categoryId = entity.categoryId,
                storedName = entity.name,
                playlistId = entity.playlistId,
                lookup = lookup
            )
            if (resolved != entity.name) entity.copy(name = resolved) else null
        }
        if (seriesUpdates.isNotEmpty()) {
            seriesCategoryDao.insertAll(seriesUpdates)
            bumpVodCatalogRevision()
        }
        Log.d(
            VOD_FLOW_TAG,
            "repairStoredCategoryNames vodUpdates=${vodUpdates.size} seriesUpdates=${seriesUpdates.size}"
        )
        seriesUpdates.take(8).forEach { entity ->
            Log.d(
                VOD_FLOW_TAG,
                "repairStoredCategoryNames series playlist=${entity.playlistId} " +
                    "id=${entity.categoryId} name=${entity.name}"
            )
        }
    }

    private suspend fun publishVodProgressFromDb(trigger: VodRefreshTrigger) {
        val cached = sessionCatalogCounts
        if (sessionDbCountsLoaded && cached != null) {
            publishVodCatalogCounts(
                trigger = trigger,
                moviesCount = cached.movies,
                seriesCount = cached.series
            )
            return
        }
        refreshCatalogCountsFromDb(trigger = trigger, force = true, persist = true)
    }

    private fun publishVodCatalogCounts(
        trigger: VodRefreshTrigger,
        moviesCount: Int,
        seriesCount: Int
    ) {
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
            "VOD catalog counts trigger=$trigger movies=$moviesCount series=$seriesCount " +
                "(lazy paging — rows are not loaded into memory)"
        )
        com.grid.tv.util.VodCatalogLogger.vodLoadComplete(trigger.name, moviesCount, seriesCount)
        com.grid.tv.util.VodCatalogLogger.moviesReceived(moviesCount)
        com.grid.tv.util.VodCatalogLogger.seriesReceived(seriesCount)
    }

    private suspend fun executeVodSeriesNetworkRefresh(trigger: VodRefreshTrigger, force: Boolean) {
        val catalogStarted = SystemClock.elapsedRealtime()
        try {
            invalidateCatalogCountCache()
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

            coroutineScope {
                val progressLock = Any()
                var moviesPhaseDone = false
                var seriesPhaseDone = false

                fun publishParallelProgress(
                    isLoading: Boolean,
                    moviesPhaseFinished: Boolean = moviesPhaseDone,
                    seriesPhaseFinished: Boolean = seriesPhaseDone,
                ) {
                    synchronized(progressLock) {
                        publishProgress(
                            isLoading = isLoading,
                            moviesPhaseFinished = moviesPhaseFinished,
                            seriesPhaseFinished = seriesPhaseFinished,
                        )
                    }
                }

                val moviesDeferred = async {
                    playlists.forEach { playlist ->
                        val result = refreshVodCatalogForPlaylist(
                            playlist = playlist,
                            trigger = trigger,
                            onBatchInserted = { parsedSoFar, total ->
                                synchronized(progressLock) {
                                    moviesLoaded = parsedSoFar
                                    moviesTotal = total
                                    moviesParsedCount = parsedSoFar
                                }
                                publishParallelProgress(isLoading = true)
                            }
                        )
                        synchronized(progressLock) {
                            moviesTotal += result.arrayLength
                            moviesLoaded = result.parsedCount
                            moviesRawLength += result.rawLength
                            moviesParsedCount += result.parsedCount
                            result.error?.let { moviesError = it }
                            result.skippedReason?.let { moviesError = it }
                        }
                    }
                    synchronized(progressLock) {
                        moviesPhaseDone = true
                    }
                    publishParallelProgress(isLoading = true, moviesPhaseFinished = true)
                }

                val seriesDeferred = async {
                    playlists.forEach { playlist ->
                        val result = refreshSeriesCatalogForPlaylist(
                            playlist = playlist,
                            trigger = trigger,
                            onBatchInserted = { parsedSoFar, total ->
                                synchronized(progressLock) {
                                    seriesLoaded = parsedSoFar
                                    seriesTotal = total
                                    seriesParsedCount = parsedSoFar
                                }
                                publishParallelProgress(isLoading = true)
                            }
                        )
                        synchronized(progressLock) {
                            seriesTotal += result.arrayLength
                            seriesLoaded = result.parsedCount
                            seriesRawLength += result.rawLength
                            seriesParsedCount += result.parsedCount
                            result.error?.let { seriesError = it }
                            result.skippedReason?.let { seriesError = it }
                        }
                    }
                    synchronized(progressLock) {
                        seriesPhaseDone = true
                    }
                    publishParallelProgress(isLoading = true, seriesPhaseFinished = true)
                }

                moviesDeferred.await()
                seriesDeferred.await()
            }

            Log.i(
                VOD_FLOW_TAG,
                "Movies phase complete trigger=$trigger parsed=$moviesParsedCount rawBytes=$moviesRawLength " +
                    "dbCount=${cachedMoviesCount()}"
            )
            Log.i(
                VOD_FLOW_TAG,
                "Series phase complete trigger=$trigger parsed=$seriesParsedCount rawBytes=$seriesRawLength"
            )
            publishProgress(
                isLoading = false,
                moviesPhaseFinished = true,
                seriesPhaseFinished = true
            )
            if (moviesParsedCount > 0 || seriesParsedCount > 0) {
                lastVodRefreshCompletedAtMs = System.currentTimeMillis()
            }
            bumpVodCatalogRevision()
            refreshCatalogCountsFromDb(trigger = trigger, force = true)
            Log.i(
                VOD_FLOW_TAG,
                "VOD pipeline complete trigger=$trigger force=$force movies=$moviesParsedCount " +
                    "series=$seriesParsedCount dbMovies=${cachedMoviesCount()}"
            )
            PerformanceTelemetryTracker.vodCatalogLoad(
                playlistId = playlists.firstOrNull()?.id ?: 0L,
                elapsedMs = SystemClock.elapsedRealtime() - catalogStarted,
                itemCount = moviesParsedCount + seriesParsedCount,
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
        val hasSecurePassword = secureCredentialStore.getXtreamPassword(playlist.id)?.isNotBlank() == true
        val hasDbPassword = playlist.xtreamPassword?.isNotBlank() == true
        val user = playlist.xtreamUsername?.takeIf { it.isNotBlank() }
        val pass = resolveXtreamPassword(playlist)?.takeIf { it.isNotBlank() }
        com.grid.tv.util.VodCatalogLogger.providerConnected(
            playlistId = playlist.id,
            hasCredentials = user != null && pass != null,
            hasSecurePassword = hasSecurePassword,
            hasDbPassword = hasDbPassword
        )
        if (user == null || pass == null) {
            com.grid.tv.util.VodCatalogLogger.catalogStageFailure(
                stage = "credentials",
                reason = if (user == null) "missing_username" else "missing_password",
                dbMovies = cachedMoviesCount(),
                dbSeries = cachedSeriesCount()
            )
            return null
        }
        val storedServer = runCatching { resolvePlaylistServerUrl(playlist) }.getOrElse {
            Log.w(VOD_FLOW_TAG, "VOD playlist ${playlist.id}: ${it.message}")
            com.grid.tv.util.VodCatalogLogger.catalogStageFailure(
                stage = "credentials",
                reason = "missing_server_url",
                dbMovies = cachedMoviesCount(),
                dbSeries = cachedSeriesCount()
            )
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
        return StartupTrace.traceSuspend("refreshVodCatalogForPlaylist playlistId=${playlist.id}") {
        try {
            val fetchResult = remoteTextFetcher.fetchCatalogToTempFile(
                rawUrl = vodUrl,
                cacheKey = "vod_pl${playlist.id}"
            )
            val catalogFile = fetchResult.file
            Log.i(
                VOD_FLOW_TAG,
                "VOD fetch playlist=${playlist.id} action=get_vod_streams trigger=$trigger " +
                    "http=${fetchResult.httpCode} rawBytes=${fetchResult.rawBytes} " +
                    "urlHost=${runCatching { URI(server).host }.getOrNull()}"
            )
            if (fetchResult.headPreview.length <= 500) {
                Log.d(VOD_FLOW_TAG, "VOD raw preview playlist=${playlist.id}: ${fetchResult.headPreview.take(400)}")
            } else {
                Log.d(
                    VOD_FLOW_TAG,
                    "VOD raw preview playlist=${playlist.id}: ${fetchResult.headPreview.take(200)}…"
                )
            }

            val priorCount = vodStreamDao.countByPlaylist(playlist.id)
            if (!isSuccessfulHttp(fetchResult.httpCode)) {
                preserveCatalogLog(
                    playlistId = playlist.id,
                    contentType = "movie",
                    reason = "HTTP ${fetchResult.httpCode}"
                )
                catalogFile?.let { deleteCatalogCacheFile(it) }
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    error = "Provider returned HTTP ${fetchResult.httpCode} for ${playlist.name}"
                )
            }

            if (catalogFile == null || !catalogFile.exists()) {
                preserveCatalogLog(playlist.id, "movie", "empty HTTP body")
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    error = "Provider returned an empty response for ${playlist.name}."
                )
            }

            val contentFingerprint = sha256FileHex(catalogFile)
            val versionKey = vodPlaylistCacheVersionKey(playlist)
            val manifest = vodCatalogCacheManifestStore.read(playlist.id)
            if (manifest != null &&
                manifest.playlistVersionKey == versionKey &&
                manifest.moviesContentFingerprint == contentFingerprint &&
                priorCount >= manifest.moviesCount &&
                manifest.isFresh(ttlMs = VOD_CACHE_TTL_MS)
            ) {
                Log.i(
                    VOD_FLOW_TAG,
                    "Skipping movie batch ingest — disk cache fresh (fingerprint match) " +
                        "playlist=${playlist.id} count=$priorCount"
                )
                startupDiskQueue.run("vod_cache_touch_movies_${playlist.id}") {
                    vodCatalogDiskCache.persistRawCatalog(playlist.id, "movies", catalogFile)
                    vodCatalogCacheManifestStore.merge(
                        playlistId = playlist.id,
                        playlistVersionKey = versionKey,
                        savedAtMs = System.currentTimeMillis(),
                        moviesCount = priorCount,
                        moviesContentFingerprint = contentFingerprint,
                    )
                }
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    parsedCount = priorCount,
                    arrayLength = priorCount,
                )
            }

            try {
            val diagnosis = xtreamParser.diagnoseVodResponse(fetchResult.headPreview)
            if (diagnosis != null && !fetchResult.headPreview.trimStart().startsWith("[")) {
                preserveCatalogLog(playlist.id, "movie", diagnosis)
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    error = diagnosis
                )
            }

            com.grid.tv.util.PlaybackDiagnostics.logMemory("before_vod_stream_parse playlist=${playlist.id}")
            val parseStartNs = System.nanoTime()
            var parsedCount = 0
            var skippedCount = 0
            var firstBatchLogged = false
            val ingestBatchSize = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.DEFAULT_BATCH_SIZE
            val syncGeneration = nextVodSyncGeneration()

            val streamResult = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.parseVodCatalogStream(
                input = catalogFile.inputStream(),
                username = user,
                password = pass,
                serverUrl = server,
                playlistId = playlist.id,
                batchSize = ingestBatchSize,
                parser = xtreamParser
            ) { batch, batchIndex, parsedSoFar ->
                if (batch.isEmpty()) return@parseVodCatalogStream
                awaitVodIngestAllowed()
                if (!firstBatchLogged) {
                    firstBatchLogged = true
                    batch.take(5).forEachIndexed { index, item ->
                        Log.d(
                            VOD_FLOW_TAG,
                            "VOD parsed[$index] playlist=${playlist.id} id=${item.streamId} " +
                                "title=${item.title.take(48)} category=${item.categoryId}"
                        )
                    }
                }
                val batchStartNs = System.nanoTime()
                com.grid.tv.util.VodCatalogIngestLogger.logBatchStart(
                    phase = "movies",
                    playlistId = playlist.id,
                    batchIndex = batchIndex,
                    batchSize = batch.size,
                    parsedSoFar = parsedSoFar
                )
                startupDiskQueue.run("vod_ingest_movies_$batchIndex") {
                    vodStreamDao.insertAll(batch.map { it.toEntity(syncGeneration) })
                }
                val batchMs = (System.nanoTime() - batchStartNs) / 1_000_000L
                com.grid.tv.util.VodCatalogIngestLogger.logBatchComplete(
                    phase = "movies",
                    playlistId = playlist.id,
                    batchIndex = batchIndex,
                    batchSize = batch.size,
                    parsedSoFar = parsedSoFar,
                    elapsedMs = batchMs
                )
                publishMoviesIngestBatch(parsedSoFar, batchIndex)
                onBatchInserted(parsedSoFar, parsedSoFar)
                throttleVodIngestBatch(batchIndex)
            }
            parsedCount = streamResult.parsedCount
            skippedCount = streamResult.skippedCount

            val parseMs = (System.nanoTime() - parseStartNs) / 1_000_000L
            com.grid.tv.util.PerformanceAudit.logJsonParse("parseVodCatalogStream", parseMs, parsedCount)
            com.grid.tv.util.PlaybackDiagnostics.logMemory("after_vod_stream_parse playlist=${playlist.id}")
            com.grid.tv.util.VodCatalogIngestLogger.logIngestComplete(
                phase = "movies",
                playlistId = playlist.id,
                parsedCount = parsedCount,
                skippedCount = skippedCount,
                totalElapsedMs = parseMs
            )

            if (parsedCount <= 0) {
                preserveCatalogLog(
                    playlist.id,
                    "movie",
                    "parsed 0 catalog entries"
                )
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    arrayLength = 0,
                    error = "Parsed 0 movie entries for ${playlist.name}"
                )
            }

            refreshCatalogCountsFromDb(
                trigger = trigger,
                force = true,
                publishUi = true,
                persist = true
            )
            val pruned = vodStreamDao.deleteStaleByPlaylist(playlist.id, syncGeneration)
            bumpVodCatalogRevision()
            refreshVodCategoriesForPlaylist(playlist, server, user, pass, trigger)

            Log.i(
                VOD_FLOW_TAG,
                "VOD incremental sync playlist=${playlist.id} upserted=$parsedCount " +
                    "prunedStale=$pruned priorCount=$priorCount syncGen=$syncGeneration"
            )
            persistVodDiskCacheAfterMoviesIngest(playlist, catalogFile, parsedCount)
            VodPlaylistRefreshResult(
                rawLength = fetchResult.rawBytes.toInt(),
                parsedCount = parsedCount,
                arrayLength = parsedCount
            )
            } finally {
                deleteCatalogCacheFile(catalogFile)
            }
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            Log.e(
                VOD_FLOW_TAG,
                "VOD catalog refresh failed for playlist ${playlist.id} (${playlist.name}): $message",
                error
            )
            preserveCatalogLog(playlist.id, "movie", message)
            VodPlaylistRefreshResult(error = message)
        }
        }
    }

    private suspend fun refreshVodCategoriesForPlaylist(
        playlist: PlaylistEntity,
        server: String,
        user: String,
        pass: String,
        trigger: VodRefreshTrigger
    ) {
        try {
            Log.i(
                VOD_FLOW_TAG,
                "Starting get_vod_categories playlist=${playlist.id} trigger=$trigger " +
                    "at=${System.currentTimeMillis()}"
            )
            val fetchResult = remoteTextFetcher.fetchDetailed(
                buildXtreamApiUrl(server, user, pass, action = "get_vod_categories")
            )
            if (!isSuccessfulHttp(fetchResult.httpCode)) {
                Log.w(
                    VOD_FLOW_TAG,
                    "VOD categories HTTP ${fetchResult.httpCode} playlist=${playlist.id} — " +
                        "falling back to stream category ids"
                )
                backfillVodCategoriesFromStreams(playlist.id)
                return
            }
            val categories = xtreamParser.parseVodCategories(fetchResult.body, playlist.id)
            Log.i(
                VOD_FLOW_TAG,
                "VOD categories playlist=${playlist.id} trigger=$trigger count=${categories.size}"
            )
            if (categories.isNotEmpty()) {
                val resolved = resolveCategoriesForStorage(categories, playlist.id)
                database.replaceVodCategoriesForPlaylist(
                    playlistId = playlist.id,
                    categories = resolved.map { it.toEntity() }
                )
                bumpVodCatalogRevision()
                repairStoredCategoryNames()
            } else {
                backfillVodCategoriesFromStreams(playlist.id)
            }
        } catch (categoryError: Throwable) {
            Log.w(
                VOD_FLOW_TAG,
                "VOD categories fetch failed playlist=${playlist.id} trigger=$trigger — " +
                    "falling back to stream category ids",
                categoryError
            )
            backfillVodCategoriesFromStreams(playlist.id)
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
        return StartupTrace.traceSuspend("refreshSeriesCatalogForPlaylist playlistId=${playlist.id}") {
        try {
            val seriesUrl = buildXtreamApiUrl(server, user, pass, action = "get_series")
            Log.i(
                VOD_FLOW_TAG,
                "Starting get_series playlist=${playlist.id} trigger=$trigger at=${System.currentTimeMillis()}"
            )
            val fetchResult = remoteTextFetcher.fetchCatalogToTempFile(
                rawUrl = seriesUrl,
                cacheKey = "series_pl${playlist.id}"
            )
            val catalogFile = fetchResult.file
            Log.i(
                VOD_FLOW_TAG,
                "Series fetch playlist=${playlist.id} action=get_series trigger=$trigger " +
                    "http=${fetchResult.httpCode} rawBytes=${fetchResult.rawBytes}"
            )
            if (fetchResult.headPreview.length < 500) {
                Log.d(VOD_FLOW_TAG, "Series raw preview playlist=${playlist.id}: ${fetchResult.headPreview.take(300)}")
            }

            val priorCount = seriesShowDao.countByPlaylist(playlist.id)
            if (!isSuccessfulHttp(fetchResult.httpCode)) {
                preserveCatalogLog(playlist.id, "series", "HTTP ${fetchResult.httpCode}")
                catalogFile?.let { deleteCatalogCacheFile(it) }
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    error = "Provider returned HTTP ${fetchResult.httpCode} for ${playlist.name}"
                )
            }

            if (catalogFile == null || !catalogFile.exists()) {
                preserveCatalogLog(playlist.id, "series", "empty or invalid response")
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    error = "Provider returned an empty series response for ${playlist.name}."
                )
            }

            val contentFingerprint = sha256FileHex(catalogFile)
            val versionKey = vodPlaylistCacheVersionKey(playlist)
            val manifest = vodCatalogCacheManifestStore.read(playlist.id)
            if (manifest != null &&
                manifest.playlistVersionKey == versionKey &&
                manifest.seriesContentFingerprint == contentFingerprint &&
                priorCount >= manifest.seriesCount &&
                manifest.isFresh(ttlMs = VOD_CACHE_TTL_MS)
            ) {
                Log.i(
                    VOD_FLOW_TAG,
                    "Skipping series batch ingest — disk cache fresh (fingerprint match) " +
                        "playlist=${playlist.id} count=$priorCount"
                )
                startupDiskQueue.run("vod_cache_touch_series_${playlist.id}") {
                    vodCatalogDiskCache.persistRawCatalog(playlist.id, "series", catalogFile)
                    vodCatalogCacheManifestStore.merge(
                        playlistId = playlist.id,
                        playlistVersionKey = versionKey,
                        savedAtMs = System.currentTimeMillis(),
                        seriesCount = priorCount,
                        seriesContentFingerprint = contentFingerprint,
                    )
                }
                seriesCatalogHydrationStore.setState(
                    playlist.id,
                    com.grid.tv.domain.model.SeriesCatalogHydrationState.POPULATED,
                )
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    parsedCount = priorCount,
                    arrayLength = priorCount,
                )
            }

            try {
            com.grid.tv.util.PlaybackDiagnostics.logMemory("before_series_stream_parse playlist=${playlist.id}")
            val parseStartNs = System.nanoTime()
            var parsedCount = 0
            var skippedCount = 0
            var firstBatchLogged = false
            val ingestBatchSize = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.DEFAULT_BATCH_SIZE

            clearSeriesSeasonsForPlaylist(playlist.id)
            val syncGeneration = nextVodSyncGeneration()

            val streamResult = com.grid.tv.data.network.parser.XtreamCatalogStreamParser.parseSeriesCatalogStream(
                input = catalogFile.inputStream(),
                playlistId = playlist.id,
                batchSize = ingestBatchSize,
                parser = xtreamParser
            ) { batch, batchIndex, parsedSoFar ->
                if (batch.isEmpty()) return@parseSeriesCatalogStream
                awaitVodIngestAllowed()
                if (!firstBatchLogged) {
                    firstBatchLogged = true
                    batch.take(5).forEachIndexed { index, show ->
                        Log.d(
                            VOD_FLOW_TAG,
                            "Series parsed[$index] playlist=${playlist.id} id=${show.id} " +
                                "name=${show.name.take(48)} category=${show.categoryId}"
                        )
                    }
                }
                val batchStartNs = System.nanoTime()
                com.grid.tv.util.VodCatalogIngestLogger.logBatchStart(
                    phase = "series",
                    playlistId = playlist.id,
                    batchIndex = batchIndex,
                    batchSize = batch.size,
                    parsedSoFar = parsedSoFar
                )
                startupDiskQueue.run("vod_ingest_series_$batchIndex") {
                    seriesShowDao.insertAll(batch.map { it.toEntity(syncGeneration) })
                }
                val batchMs = (System.nanoTime() - batchStartNs) / 1_000_000L
                com.grid.tv.util.VodCatalogIngestLogger.logBatchComplete(
                    phase = "series",
                    playlistId = playlist.id,
                    batchIndex = batchIndex,
                    batchSize = batch.size,
                    parsedSoFar = parsedSoFar,
                    elapsedMs = batchMs
                )
                publishSeriesIngestBatch(parsedSoFar, batchIndex)
                onBatchInserted(parsedSoFar, parsedSoFar)
                throttleVodIngestBatch(batchIndex)
            }
            parsedCount = streamResult.parsedCount
            skippedCount = streamResult.skippedCount

            val parseMs = (System.nanoTime() - parseStartNs) / 1_000_000L
            com.grid.tv.util.PerformanceAudit.logJsonParse("parseSeriesCatalogStream", parseMs, parsedCount)
            com.grid.tv.util.PlaybackDiagnostics.logMemory("after_series_stream_parse playlist=${playlist.id}")
            com.grid.tv.util.VodCatalogIngestLogger.logIngestComplete(
                phase = "series",
                playlistId = playlist.id,
                parsedCount = parsedCount,
                skippedCount = skippedCount,
                totalElapsedMs = parseMs
            )

            if (parsedCount <= 0) {
                preserveCatalogLog(playlist.id, "series", "parsed 0 catalog entries")
                if (isSuccessfulHttp(fetchResult.httpCode)) {
                    seriesCatalogHydrationStore.setState(
                        playlist.id,
                        SeriesCatalogHydrationState.EMPTY,
                    )
                }
                return@traceSuspend VodPlaylistRefreshResult(
                    rawLength = fetchResult.rawBytes.toInt(),
                    arrayLength = 0,
                    error = "Parsed 0 series entries for ${playlist.name}"
                )
            }

            seriesCatalogHydrationStore.setState(
                playlist.id,
                SeriesCatalogHydrationState.POPULATED,
            )

            refreshCatalogCountsFromDb(
                trigger = trigger,
                force = true,
                publishUi = true,
                persist = true
            )
            val pruned = seriesShowDao.deleteStaleByPlaylist(playlist.id, syncGeneration)
            bumpVodCatalogRevision()
            refreshSeriesCategoriesForPlaylist(playlist, trigger)

            Log.i(
                VOD_FLOW_TAG,
                "Series incremental sync playlist=${playlist.id} upserted=$parsedCount " +
                    "prunedStale=$pruned priorCount=$priorCount syncGen=$syncGeneration"
            )
            persistVodDiskCacheAfterSeriesIngest(playlist, catalogFile, parsedCount)
            VodPlaylistRefreshResult(
                rawLength = fetchResult.rawBytes.toInt(),
                parsedCount = parsedCount,
                arrayLength = parsedCount
            )
            } finally {
                deleteCatalogCacheFile(catalogFile)
            }
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            Log.e(
                VOD_FLOW_TAG,
                "Series catalog refresh failed for playlist ${playlist.id} (${playlist.name}): $message",
                error
            )
            preserveCatalogLog(playlist.id, "series", message)
            VodPlaylistRefreshResult(error = message)
        }
        }
    }

    override fun vodCatalogLoading(): Flow<Boolean> = vodCatalogLoading

    override fun vodCatalogProgress(): Flow<VodCatalogProgress> = vodCatalogProgress

    override fun vodCatalogStatus(): Flow<VodCatalogStatus> = vodCatalogStatus

    override fun vodCatalogRevision(): Flow<Long> = _vodCatalogRevision

    override fun vodStreamCount(): Flow<Int> = _vodStreamCountFlow.asStateFlow()

    override fun seriesShowCount(): Flow<Int> = _seriesShowCountFlow.asStateFlow()

    override fun vodCategories(): Flow<List<VodCategory>> =
        combine(
            vodCategoryDao.observeAll(),
            _vodCatalogRevision,
            _vodStreamCountFlow
        ) { stored, _, _ -> stored }
            .flatMapLatest { stored ->
                flow {
                    val categories = if (stored.isNotEmpty()) {
                        stored.map { it.toDomain() }
                    } else {
                        buildFallbackVodCategories()
                    }
                    emit(resolveCategoriesForDisplay(categories))
                }
            }
            .flowOn(Dispatchers.IO)

    override fun seriesCategories(): Flow<List<VodCategory>> =
        combine(
            seriesCategoryDao.observeAll(),
            _vodCatalogRevision,
            _seriesShowCountFlow
        ) { stored, _, _ -> stored }
            .flatMapLatest { stored ->
                flow {
                    val merged = mergeAllSeriesCategorySources(stored.map { it.toDomain() })
                    emit(resolveCategoriesForDisplay(merged))
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun vodPage(
        categoryId: String?,
        search: String,
        limit: Int,
        offset: Int
    ): List<VodItem> = withContext(Dispatchers.IO) {
        vodStreamDao.vodPage(categoryId, sqlSearchPrefix(search), limit, offset).map { it.toDomain() }
    }

    override fun vodMoviesPaging(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?
    ): Flow<PagingData<VodItem>> {
        val searchPrefix = sqlSearchPrefix(search.trim())
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        return Pager(
            config = PagingConfig(
                pageSize = VOD_PAGING_PAGE_SIZE,
                initialLoadSize = StartupTierPolicy.vodPagingInitialLoadSize(VOD_PAGING_PAGE_SIZE),
                prefetchDistance = TvImageSizing.vodPagingPrefetchDistance(),
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (resolvedPlaylistId.isPlaylistScoped()) {
                    vodStreamDao.vodPagingSourceByIdsForPlaylist(
                        resolvedPlaylistId!!,
                        matchAll,
                        ids,
                        searchPrefix
                    )
                } else {
                    vodStreamDao.vodPagingSourceByIds(matchAll, ids, searchPrefix)
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override fun vodUnifiedSearchPaging(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?
    ): Flow<PagingData<VodSearchEntry>> {
        val searchPrefix = sqlSearchPrefix(search.trim())
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        val playlistScoped = resolvedPlaylistId.isPlaylistScoped()
        return Pager(
            config = PagingConfig(
                pageSize = VOD_PAGING_PAGE_SIZE,
                initialLoadSize = StartupTierPolicy.vodPagingInitialLoadSize(VOD_PAGING_PAGE_SIZE),
                prefetchDistance = TvImageSizing.vodPagingPrefetchDistance(),
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                VodUnifiedSearchPagingSource(
                    vodSearchDao = database.vodSearchDao(),
                    vodStreamDao = vodStreamDao,
                    seriesShowDao = seriesShowDao,
                    searchPrefix = searchPrefix,
                    playlistScoped = playlistScoped,
                    playlistId = resolvedPlaylistId ?: 0L,
                    matchAll = matchAll,
                    categoryIds = ids
                )
            }
        ).flow
    }

    override fun seriesShowsPaging(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?
    ): Flow<PagingData<SeriesShow>> {
        val searchPrefix = sqlSearchPrefix(search.trim())
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        return Pager(
            config = PagingConfig(
                pageSize = VOD_PAGING_PAGE_SIZE,
                initialLoadSize = StartupTierPolicy.vodPagingInitialLoadSize(VOD_PAGING_PAGE_SIZE),
                prefetchDistance = TvImageSizing.vodPagingPrefetchDistance(),
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (resolvedPlaylistId.isPlaylistScoped()) {
                    seriesShowDao.seriesPagingSourceByIdsForPlaylist(
                        resolvedPlaylistId!!,
                        matchAll,
                        ids,
                        searchPrefix
                    )
                } else {
                    seriesShowDao.seriesPagingSourceByIds(matchAll, ids, searchPrefix)
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun vodFilteredCount(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?
    ): Int = withContext(Dispatchers.IO) {
        val searchPrefix = sqlSearchPrefix(search.trim())
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        if (resolvedPlaylistId.isPlaylistScoped()) {
            vodStreamDao.countFilteredByIdsForPlaylist(resolvedPlaylistId!!, matchAll, ids, searchPrefix)
        } else {
            vodStreamDao.countFilteredByIds(matchAll, ids, searchPrefix)
        }
    }

    override suspend fun findVodStream(playlistId: Long, streamId: Long): VodItem? =
        withContext(Dispatchers.IO) {
            val resolved = requireScopedPlaylistId(playlistId) ?: return@withContext null
            vodStreamDao.findByStreamId(resolved, streamId)?.toDomain()
        }

    override suspend fun loadVodDetail(playlistId: Long, streamId: Long): VodItem? = withContext(Dispatchers.IO) {
        val resolved = requireScopedPlaylistId(playlistId) ?: return@withContext null
        val base = vodStreamDao.findByStreamId(resolved, streamId)?.toDomain()
        Log.d(
            VOD_FLOW_TAG,
            "METADATA_TRACE movie click playlist=$resolved streamId=$streamId base=" +
                "title=${base?.title?.take(80)} plot=${!base?.plot.isNullOrBlank()} genre=${!base?.genre.isNullOrBlank()} " +
                "duration=${!base?.duration.isNullOrBlank()}"
        )

        val playlist = playlistDao.getById(resolved) ?: return@withContext base
        if (playlist.type != PlaylistType.XTREAM.name) {
            Log.d(VOD_FLOW_TAG, "METADATA_TRACE movie detail skipped non-xtream playlist=$resolved")
            return@withContext base
        }
        val server = playlist.xtreamServerUrl ?: return@withContext base
        val user = playlist.xtreamUsername ?: return@withContext base
        val pass = resolveXtreamPassword(playlist) ?: return@withContext base
        val url = buildXtreamApiUrl(server, user, pass, action = "get_vod_info", extra = "vod_id=$streamId")
        Log.i(VOD_FLOW_TAG, "METADATA_TRACE movie request url=$url")

        val fetch = remoteTextFetcher.fetchDetailed(url)
        Log.i(
            VOD_FLOW_TAG,
            "METADATA_TRACE movie response http=${fetch.httpCode} bytes=${fetch.rawBytes} streamId=$streamId"
        )
        Log.d(VOD_FLOW_TAG, "METADATA_TRACE movie body sample=${fetch.body.take(600).replace('\n', ' ')}")
        if (!isSuccessfulHttp(fetch.httpCode)) {
            return@withContext base
        }

        val parsed = runCatching {
            xtreamParser.parseVodInfo(
                raw = fetch.body,
                username = user,
                password = pass,
                serverUrl = server,
                playlistId = resolved,
                fallbackStreamId = streamId,
            )
        }.onFailure { error ->
            Log.w(VOD_FLOW_TAG, "METADATA_TRACE movie parse failed streamId=$streamId: ${error.message}", error)
        }.getOrNull()

        Log.d(
            VOD_FLOW_TAG,
            "METADATA_TRACE movie parsed streamId=$streamId title=${parsed?.title?.take(80)} " +
                "plot=${!parsed?.plot.isNullOrBlank()} genre=${!parsed?.genre.isNullOrBlank()} " +
                "duration=${!parsed?.duration.isNullOrBlank()}"
        )

        val merged = mergeVodDetail(base, parsed)
        Log.d(
            VOD_FLOW_TAG,
            "METADATA_TRACE movie emitted streamId=$streamId title=${merged?.title?.take(80)} " +
                "plot=${!merged?.plot.isNullOrBlank()} genre=${!merged?.genre.isNullOrBlank()} " +
                "duration=${!merged?.duration.isNullOrBlank()}"
        )
        return@withContext merged
    }

    private fun mergeVodDetail(base: VodItem?, detail: VodItem?): VodItem? {
        if (base == null) return detail
        if (detail == null) return base
        return base.copy(
            title = detail.title.takeIf { it.isNotBlank() } ?: base.title,
            streamUrl = detail.streamUrl.takeIf { it.isNotBlank() } ?: base.streamUrl,
            posterUrl = detail.posterUrl?.takeIf { it.isNotBlank() } ?: base.posterUrl,
            plot = detail.plot?.takeIf { it.isNotBlank() } ?: base.plot,
            cast = detail.cast?.takeIf { it.isNotBlank() } ?: base.cast,
            director = detail.director?.takeIf { it.isNotBlank() } ?: base.director,
            genre = detail.genre?.takeIf { it.isNotBlank() } ?: base.genre,
            rating = detail.rating?.takeIf { it.isNotBlank() } ?: base.rating,
            duration = detail.duration?.takeIf { it.isNotBlank() } ?: base.duration,
            categoryId = detail.categoryId?.takeIf { it.isNotBlank() } ?: base.categoryId,
            addedEpochSec = detail.addedEpochSec ?: base.addedEpochSec
        )
    }

    override suspend fun vodRecent(playlistId: Long, limit: Int): List<VodItem> =
        withContext(Dispatchers.IO) {
            val resolved = requireScopedPlaylistId(playlistId) ?: return@withContext emptyList()
            vodStreamDao.recentForPlaylist(resolved, limit).map { it.toDomain() }
        }

    override suspend fun vodSampleForRecommendations(sampleSize: Int): List<VodItem> =
        withContext(Dispatchers.IO) {
            var total = cachedMoviesCount()
            if (total == 0 && !sessionDbCountsLoaded) {
                total = refreshCatalogCountsFromDb(
                    trigger = VodRefreshTrigger.VOD_HUB_MOUNT,
                    force = true,
                    publishUi = false,
                    persist = true
                ).movies
            }
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

    override suspend fun seriesRecentSample(limit: Int): List<SeriesShow> =
        withContext(Dispatchers.IO) {
            seriesShowDao.recent(limit.coerceAtLeast(1)).map { it.toDomain() }
        }

    override suspend fun discoverVodContentLanguages(maxTitlesPerSource: Int): List<String> =
        withContext(Dispatchers.IO) {
            val labels = ArrayList<String>()
            val batchSize = 500

            val vodTotal = vodStreamDao.countTotal()
            var vodOffset = 0
            while (vodOffset < vodTotal && vodOffset < maxTitlesPerSource) {
                labels += vodStreamDao.titleBatch(batchSize, vodOffset)
                vodOffset += batchSize
            }

            val seriesTotal = seriesShowDao.countTotal()
            var seriesOffset = 0
            while (seriesOffset < seriesTotal && seriesOffset < maxTitlesPerSource) {
                labels += seriesShowDao.nameBatch(batchSize, seriesOffset)
                seriesOffset += batchSize
            }

            labels += vodCategoryDao.all().map { it.name }
            labels += seriesCategoryDao.all().map { it.name }

            com.grid.tv.feature.vod.discoverLanguageCodesFromLabels(labels.asSequence())
        }

    override suspend fun loadMovieBrowseRows(
        itemsPerRow: Int,
        maxRows: Int,
        playlistId: Long?
    ): List<VodBrowseRow> =
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<VodBrowseRow>()
            val scopedPlaylistId = scopedPlaylistId(playlistId)
            if (scopedPlaylistId != null) {
                vodStreamDao.recentForPlaylist(scopedPlaylistId, itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                    rows += VodBrowseRow("recent", "Recently Added", movies = it.map { e -> e.toDomain() })
                }
                vodStreamDao.topRatedForPlaylist(scopedPlaylistId, itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                    rows += VodBrowseRow("top_imdb", "Top IMDB", movies = it.map { e -> e.toDomain() })
                }
                vodStreamDao.fourKForPlaylist(scopedPlaylistId, itemsPerRow).takeIf { it.isNotEmpty() }?.let {
                    rows += VodBrowseRow("4k", "4K Movies", movies = it.map { e -> e.toDomain() })
                }
            }
            val lookup = buildCategoryNameLookup()
            val storedCategories = vodCategoryDao.topCategoriesByStreamCount(maxRows)
            if (storedCategories.isNotEmpty()) {
                storedCategories.forEach { category ->
                    logCategoryKey(category.playlistId, category.categoryId)
                    val items = vodStreamsForCategory(category.playlistId, category.categoryId, itemsPerRow)
                    if (items.isNotEmpty()) {
                        rows += VodBrowseRow(
                            id = categoryBrowseRowId(category.playlistId, category.categoryId),
                            title = resolvedCategoryDisplayName(
                                categoryId = category.categoryId,
                                storedName = category.name,
                                playlistId = category.playlistId,
                                lookup = lookup
                            ),
                            movies = items.map { it.toDomain() }
                        )
                    }
                }
            } else {
                vodStreamDao.topCategoryPairsByStreamCount(maxRows).forEach { pair ->
                    logCategoryKey(pair.playlistId, pair.categoryId)
                    val items = vodStreamsForCategory(pair.playlistId, pair.categoryId, itemsPerRow)
                    if (items.isNotEmpty()) {
                        val displayName = resolvedCategoryDisplayName(
                            categoryId = pair.categoryId,
                            storedName = lookupCategoryLabel(lookup, pair.playlistId, pair.categoryId),
                            playlistId = pair.playlistId,
                            lookup = lookup
                        )
                        rows += VodBrowseRow(
                            id = categoryBrowseRowId(pair.playlistId, pair.categoryId),
                            title = displayName,
                            movies = items.map { it.toDomain() }
                        )
                    }
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
        seriesShowDao.seriesPage(category, sqlSearchPrefix(search), limit, offset).map { it.toDomain() }
    }

    override suspend fun seriesFilteredCount(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?
    ): Int = withContext(Dispatchers.IO) {
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val searchPrefix = sqlSearchPrefix(search.trim())
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        if (resolvedPlaylistId.isPlaylistScoped()) {
            seriesShowDao.countFilteredByIdsForPlaylist(resolvedPlaylistId!!, matchAll, ids, searchPrefix)
        } else {
            seriesShowDao.countFilteredByIds(matchAll, ids, searchPrefix)
        }
    }

    override suspend fun seriesShowsBatch(
        categoryIds: Set<String>?,
        search: String,
        playlistId: Long?,
        limit: Int,
        offset: Int
    ): List<SeriesShow> = withContext(Dispatchers.IO) {
        val matchAll = categoryIds.isNullOrEmpty()
        val ids = categoryIds?.toList()?.sorted() ?: listOf("")
        val searchPrefix = sqlSearchPrefix(search.trim())
        val resolvedPlaylistId = scopedPlaylistId(playlistId)
        val entities = if (resolvedPlaylistId.isPlaylistScoped()) {
            seriesShowDao.filteredBatchByIdsForPlaylist(
                resolvedPlaylistId!!,
                matchAll,
                ids,
                searchPrefix,
                limit,
                offset
            )
        } else {
            seriesShowDao.filteredBatchByIds(matchAll, ids, searchPrefix, limit, offset)
        }
        entities.map { it.toDomain() }
    }

    override suspend fun findSeriesShow(playlistId: Long, seriesId: Long): SeriesShow? =
        withContext(Dispatchers.IO) {
            val resolved = requireScopedPlaylistId(playlistId) ?: return@withContext null
            seriesShowDao.findBySeriesId(resolved, seriesId)?.toDomain()
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
            val lookup = buildCategoryNameLookup()
            val storedCategories = seriesCategoryDao.all()
            if (storedCategories.isNotEmpty()) {
                storedCategories.forEach { category ->
                    logCategoryKey(category.playlistId, category.categoryId)
                    val items = seriesShowsForCategory(category.playlistId, category.categoryId, itemsPerRow)
                    if (items.isNotEmpty()) {
                        rows += VodBrowseRow(
                            id = categoryBrowseRowId(category.playlistId, category.categoryId),
                            title = resolvedCategoryDisplayName(
                                categoryId = category.categoryId,
                                storedName = category.name,
                                playlistId = category.playlistId,
                                lookup = lookup
                            ),
                            series = items.map { it.toDomain() }
                        )
                    }
                }
            } else {
                seriesShowDao.distinctCategoryPairs().forEach { pair ->
                    logCategoryKey(pair.playlistId, pair.categoryId)
                    val items = seriesShowsForCategory(pair.playlistId, pair.categoryId, itemsPerRow)
                    if (items.isNotEmpty()) {
                        val displayName = resolvedCategoryDisplayName(
                            categoryId = pair.categoryId,
                            storedName = lookupCategoryLabel(lookup, pair.playlistId, pair.categoryId),
                            playlistId = pair.playlistId,
                            lookup = lookup
                        )
                        rows += VodBrowseRow(
                            id = categoryBrowseRowId(pair.playlistId, pair.categoryId),
                            title = displayName,
                            series = items.map { it.toDomain() }
                        )
                    }
                }
            }
            rows.filter { !it.isEmpty }.distinctBy { it.id }.take(maxRows)
        }

    override suspend fun searchVod(query: String, limit: Int): List<VodItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        vodStreamDao.search(sqlSearchPrefix(query.trim()), limit).map { it.toDomain() }
    }

    override suspend fun searchSeriesShows(query: String, limit: Int): List<SeriesShow> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            seriesShowDao.search(sqlSearchPrefix(query.trim()), limit).map { it.toDomain() }
        }

    override suspend fun distinctSeriesCategories(): List<String> = withContext(Dispatchers.IO) {
        val stored = seriesCategoryDao.all()
        if (stored.isNotEmpty()) {
            return@withContext stored.map { it.name }
        }
        buildFallbackSeriesCategories().map { it.name }
    }

    override suspend fun seriesSeasons(playlistId: Long, seriesId: Long): List<SeriesSeason> =
        loadSeriesDetail(playlistId, seriesId).seasons

    override suspend fun loadSeriesDetail(playlistId: Long, seriesId: Long): SeriesDetail =
        withContext(Dispatchers.IO) {
            if (playlistId <= 0L) return@withContext SeriesDetail()
            val show = seriesShowDao.findBySeriesId(playlistId, seriesId)
                ?: return@withContext SeriesDetail()
            Log.d(
                VOD_FLOW_TAG,
                "METADATA_TRACE series click playlist=$playlistId seriesId=$seriesId show=${show.name.take(80)}"
            )
            val cacheKey = playlistId to seriesId
            seriesSeasonsCache.get(cacheKey)?.let { cached ->
                Log.d(
                    VOD_FLOW_TAG,
                    "METADATA_TRACE series emitted cache seriesId=$seriesId seasons=${cached.seasons.size} " +
                        "plot=${!cached.plot.isNullOrBlank()}"
                )
                return@withContext SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(cached)
            }
            val storedEpisodes = database.vodCatalogEpisodeDao().episodesForSeries(playlistId, seriesId)
            if (storedEpisodes.isNotEmpty() && storedEpisodes.any { it.streamUrl.isNotBlank() }) {
                val cachedDetail = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(
                    storedEpisodes.toSeriesDetail()
                )
                seriesSeasonsCache.put(cacheKey, cachedDetail)
                Log.d(
                    VOD_FLOW_TAG,
                    "METADATA_TRACE series emitted db-cache seriesId=$seriesId seasons=${cachedDetail.seasons.size} " +
                        "plot=${!cachedDetail.plot.isNullOrBlank()}"
                )
                return@withContext cachedDetail
            }
            val playlist = playlistDao.getById(playlistId) ?: return@withContext SeriesDetail()
            val server = playlist.xtreamServerUrl ?: return@withContext SeriesDetail()
            val user = playlist.xtreamUsername ?: return@withContext SeriesDetail()
            val pass = resolveXtreamPassword(playlist) ?: return@withContext SeriesDetail()
            val url = buildXtreamApiUrl(server, user, pass, action = "get_series_info", extra = "series_id=$seriesId")
            Log.i(VOD_FLOW_TAG, "METADATA_TRACE series request url=$url")
            val raw = remoteTextFetcher.tryFetch(url)
            if (raw == null) {
                Log.w(VOD_FLOW_TAG, "get_series_info unavailable for seriesId=$seriesId playlist=$playlistId")
                return@withContext SeriesDetail()
            }
            Log.d(VOD_FLOW_TAG, "METADATA_TRACE series body sample=${raw.take(600).replace('\n', ' ')}")
            val detail = JsonParseMetrics.onIoThread(
                label = "series_info seriesId=$seriesId",
                itemCount = -1
            ) {
                xtreamParser.parseSeriesInfo(raw, user, pass, server)
            }
            Log.d(
                VOD_FLOW_TAG,
                "METADATA_TRACE series parsed seriesId=$seriesId seasons=${detail.seasons.size} " +
                    "plot=${!detail.plot.isNullOrBlank()}"
            )
            val normalized = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(detail)
            persistSeriesEpisodes(playlistId, seriesId, show.name, normalized)
            seriesSeasonsCache.put(cacheKey, normalized)
            Log.d(
                VOD_FLOW_TAG,
                "METADATA_TRACE series emitted seriesId=$seriesId seasons=${normalized.seasons.size} " +
                    "plot=${!normalized.plot.isNullOrBlank()}"
            )
            return@withContext normalized
        }

    private suspend fun persistSeriesEpisodes(
        playlistId: Long,
        seriesId: Long,
        seriesTitle: String,
        detail: SeriesDetail
    ) {
        val episodeDao = database.vodCatalogEpisodeDao()
        episodeDao.deleteForSeries(playlistId, seriesId)
        val entities = detail.toEpisodeEntities(playlistId, seriesId, seriesTitle)
        if (entities.isNotEmpty()) {
            episodeDao.upsertAll(entities)
        }
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
        }.flowOn(Dispatchers.IO)

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

    override suspend fun saveVodWatchPosition(
        streamId: Long,
        positionMs: Long,
        title: String,
        durationMs: Long,
        playlistId: Long
    ) {
        ensureDefaultProfile()
        val syntheticId = com.grid.tv.domain.model.VodProgressKeys.syntheticChannelId(playlistId, streamId)
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
        if (playlistId > 0L) {
            val legacyId = -streamId
            if (legacyId != syntheticId) {
                profileWatchHistoryDao.delete(activeProfileId, legacyId)
            }
        }
    }

    override fun vodWatchProgress(): Flow<Map<Pair<Long, Long>, Long>> =
        profileWatchHistoryDao.observeVodPositions(activeProfileId).map { rows ->
            rows.associate { row ->
                val key = com.grid.tv.domain.model.VodProgressKeys.decode(row.channelId)
                key.asPair() to row.lastPosition
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun mapChannelEntity(entity: com.grid.tv.data.db.entity.ChannelEntity): Channel? =
        mapChannelEntities(listOf(entity)).firstOrNull()

    override suspend fun channelById(channelId: Long): Channel? = withContext(Dispatchers.IO) {
        if (channelId <= 0L) return@withContext null
        val entity = channelDao.getById(channelId) ?: return@withContext null
        mapChannelEntity(entity)
    }

    override suspend fun channelByNumber(playlistId: Long, number: Int): Channel? =
        withContext(Dispatchers.IO) {
            val resolved = requireScopedPlaylistId(playlistId) ?: return@withContext null
            if (number <= 0) return@withContext null
            val entity = channelDao.getByNumber(resolved, number) ?: return@withContext null
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
            guideFiltersConfigured = db.guideFiltersConfigured,
            channelGroupNavigationMode = ChannelGroupNavigationMode.fromStored(db.channelGroupNavigationMode),
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
                guideFiltersConfigured = guideConfigured,
                channelGroupNavigationMode = settings.channelGroupNavigationMode.name,
            )
        )
        cachedSettings = cachedSettings.copy(
            guideChannelGroups = GuideChannelFilter.decode(guideGroupsEncoded),
            guideFiltersConfigured = guideConfigured,
            channelGroupNavigationMode = settings.channelGroupNavigationMode,
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
        invalidateGuideBootstrapChannelCache()
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
        viewportEpgLastFetch.clear()
        viewportEpgFailureUntil.clear()
        seriesSeasonsCache.clear()
        appCacheRegistry.logInventory("clear_app_cache")
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
        seriesCategoryDao.clearAll()
        seriesShowDao.clearAll()
        vodCatalogDiskCache.clearAll()
        vodCatalogCacheManifestStore.clearAll()
        seriesCatalogHydrationStore.clearAll()
        seriesSeasonsCache.clear()
        invalidateCategoryLookupCache()
        invalidateCatalogCountCache()
        startupCatalogCountsStore.clear()
        _vodStreamCountFlow.value = 0
        _seriesShowCountFlow.value = 0
        _channelCountFlow.value = 0
        _vodCatalogRevision.value = 0L
        activeProfileId = 0L
        cachedSettings = AppSettings()
        guestSessionPreferences.clearGuestSession()
        saveSettings(AppSettings())
    }
}
