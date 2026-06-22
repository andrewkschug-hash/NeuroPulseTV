package com.grid.tv.feature.scanner

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.dao.ProfileFavoriteDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.entity.ChannelScanEntity
import com.grid.tv.data.db.model.ChannelScanProbeRow
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.ChannelScanStatus
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.domain.model.ScannerSettings
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.epg.EpgJobCoordinator
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import com.grid.tv.util.cache.AppCacheRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

@Singleton
class ChannelScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val channelScanDao: ChannelScanDao,
    private val profileFavoriteDao: ProfileFavoriteDao,
    private val profileWatchHistoryDao: ProfileWatchHistoryDao,
    private val repository: IptvRepository,
    private val appHttpClient: AppHttpClient,
    private val epgJobCoordinator: EpgJobCoordinator,
    private val hostFailureTracker: HostFailureTracker,
    private val epgDownloadTracker: EpgDownloadTracker,
    private val playlistImportCoordinator: PlaylistImportCoordinator,
    private val scanMetrics: ScanMetricsLogger,
    appCacheRegistry: AppCacheRegistry
) : ChannelScanGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probe by lazy {
        ChannelProbe(appHttpClient.probeClient(), hostFailureTracker, scanMetrics)
    }
    private val limiter = ScanConcurrencyLimiter(ScanConcurrencyLimiter.MAX_CONCURRENCY)
    private val stateMutex = Mutex()
    private val statusCache = ChannelScanStatusCache(registry = appCacheRegistry)

    private val _statuses = MutableStateFlow<Map<Long, ChannelScanSnapshot>>(emptyMap())
    val statuses: StateFlow<Map<Long, ChannelScanSnapshot>> = _statuses.asStateFlow()

    private val _runtime = MutableStateFlow(ScannerRuntimeState())
    val runtime: StateFlow<ScannerRuntimeState> = _runtime.asStateFlow()

    private val _validationActive = MutableStateFlow(false)
    override val isValidationActive: StateFlow<Boolean> = _validationActive.asStateFlow()

    @Volatile
    private var settings = ScannerSettings()

    @Volatile
    private var priorityChannelIds: Set<Long> = emptySet()

    @Volatile
    private var appInForeground = true

    @Volatile
    private var scansSuspendedForPlayback = false

    private var playbackSuspendStartedAtMs = 0L

    private var probesSkippedDuringPlayback = 0

    @Volatile
    private var forceFullScan = false

    private val priorityValidationRunning = AtomicBoolean(false)

    @Volatile
    private var scanBatchOffset = 0

    private var scanCycleCount = 0

    private var publishJob: Job? = null

    @Volatile
    private var maintenanceScanUnlocked = false

    init {
        scope.launch { refreshSettingsLoop() }
        scope.launch {
            awaitMaintenanceScanWindow()
            runSafely("hydrate") { hydrateFromDatabase() }
        }
        scope.launch {
            awaitMaintenanceScanWindow()
            scanSchedulerLoop()
        }
    }

    private suspend fun awaitMaintenanceScanWindow() {
        delay(BOOT_MAINTENANCE_MIN_DELAY_MS)
        while (isStartupBusy()) {
            delay(MAINTENANCE_IDLE_POLL_MS)
        }
        maintenanceScanUnlocked = true
        android.util.Log.i(
            TAG,
            "Maintenance scan unlocked after ${BOOT_MAINTENANCE_MIN_DELAY_MS}ms minimum delay " +
                "and idle window (no import, no EPG download)"
        )
    }

    private fun isStartupBusy(): Boolean =
        playlistImportCoordinator.isImportActive() || epgDownloadTracker.isInProgress()

    private fun unlockMaintenanceScan(reason: String) {
        if (!maintenanceScanUnlocked) {
            maintenanceScanUnlocked = true
            android.util.Log.i(TAG, "Maintenance scan unlocked early: $reason")
        }
    }

    fun setAppInForeground(visible: Boolean) {
        appInForeground = visible
    }

    /**
     * Pauses background channel probes while fullscreen live playback is active.
     * Scan progress ([scanBatchOffset], status cache) is preserved across suspend/resume.
     */
    fun setPlaybackScanSuspended(suspended: Boolean) {
        if (scansSuspendedForPlayback == suspended) return
        scansSuspendedForPlayback = suspended
        if (suspended) {
            playbackSuspendStartedAtMs = System.currentTimeMillis()
            scanMetrics.logPlaybackScanSuspend(
                suspended = true,
                scanBatchOffset = scanBatchOffset,
                probesSkippedDuringSession = probesSkippedDuringPlayback
            )
        } else {
            val durationMs = if (playbackSuspendStartedAtMs > 0L) {
                System.currentTimeMillis() - playbackSuspendStartedAtMs
            } else {
                0L
            }
            scanMetrics.logPlaybackScanSuspend(
                suspended = false,
                scanBatchOffset = scanBatchOffset,
                probesSkippedDuringSession = probesSkippedDuringPlayback,
                suspendedDurationMs = durationMs
            )
            playbackSuspendStartedAtMs = 0L
            probesSkippedDuringPlayback = 0
        }
    }

    val isPlaybackScanSuspended: Boolean
        get() = scansSuspendedForPlayback

    fun setPriorityChannelIds(ids: Collection<Long>) {
        priorityChannelIds = ids.toSet()
    }

    fun updateSettings(newSettings: ScannerSettings) {
        settings = newSettings
        limiter.updateLimit(newSettings.concurrentChecks)
    }

    fun scanNow() {
        if (scansSuspendedForPlayback) {
            android.util.Log.i(TAG, "scanNow ignored — live fullscreen playback active")
            return
        }
        unlockMaintenanceScan("manual scanNow")
        forceFullScan = true
    }

    override fun beginPostImportPriorityWorkflow() {
        if (scansSuspendedForPlayback) {
            android.util.Log.i(TAG, "Post-import validation deferred — live fullscreen playback active")
            return
        }
        unlockMaintenanceScan("post-import priority validation")
        scope.launch {
            runSafely("postImportPriority") {
                hostFailureTracker.resetSession()
                val priorityIds = collectPriorityChannelIds(ChannelScanGate.PRIORITY_VALIDATION_LIMIT)
                android.util.Log.i(
                    TAG,
                    "Phase 1: priority validation for ${priorityIds.size} channels " +
                        "(favorites + recent + visible, max ${ChannelScanGate.PRIORITY_VALIDATION_LIMIT})"
                )
                if (priorityIds.isEmpty()) {
                    scheduleEpgPhase2()
                    forceFullScan = true
                    return@runSafely
                }
                runPriorityValidationBurst(priorityIds)
                scheduleEpgPhase2()
                forceFullScan = true
                android.util.Log.i(TAG, "Phase 3: background full-catalog validation enabled")
            }
        }
    }

    override suspend fun awaitValidationIdle(maxWaitMs: Long) {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (!priorityValidationRunning.get() && !_validationActive.value) return
            delay(VALIDATION_POLL_MS)
        }
        android.util.Log.w(TAG, "awaitValidationIdle timed out after ${maxWaitMs}ms")
    }

    fun reportPlaybackResult(channelId: Long, isLive: Boolean) {
        val status = if (isLive) ChannelScanStatus.LIVE else ChannelScanStatus.DEAD
        val snapshot = ChannelScanSnapshot(status = status, lastCheckedAt = System.currentTimeMillis())
        scope.launch {
            runSafely("reportPlayback") {
                persistSnapshot(channelId, snapshot)
                putStatus(channelId, snapshot)
                refreshRuntimeCounts()
            }
        }
    }

    private suspend fun collectPriorityChannelIds(limit: Int): LinkedHashSet<Long> {
        val ordered = LinkedHashSet<Long>(limit)
        val profileId = repository.activeProfile()?.id ?: 1L

        profileFavoriteDao.allChannelIdsForProfile(profileId).forEach { id ->
            if (ordered.size >= limit) return@forEach
            ordered.add(id)
        }

        profileWatchHistoryDao.recentLiveChannelIds(profileId, limit = limit).forEach { id ->
            if (ordered.size >= limit) return@forEach
            ordered.add(id)
        }

        val visibleBatch = channelDao.scanProbeBatch(limit = limit, offset = 0)
        visibleBatch.forEach { row ->
            if (ordered.size >= limit) return@forEach
            ordered.add(row.id)
        }

        return ordered
    }

    private suspend fun runPriorityValidationBurst(priorityIds: Set<Long>) {
        if (priorityIds.isEmpty()) return
        priorityValidationRunning.set(true)
        _validationActive.value = true
        _runtime.update { it.copy(isScanning = true, totalCount = channelDao.countTotal()) }
        try {
            val rows = channelDao.scanProbeByIds(priorityIds.toList())
            rows.chunked(SCAN_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                if (scansSuspendedForPlayback) return@forEachIndexed
                scanMetrics.logBatchStart(batchIndex, batch.size, rows.size - batchIndex * SCAN_BATCH_SIZE)
                coroutineScope {
                    batch.map { row ->
                        async {
                            limiter.withPermit { checkChannel(row.id, row.streamUrl) }
                        }
                    }.awaitAll()
                }
                scanMetrics.logBatchEnd(batchIndex, batch.size)
                yield()
                delay(BATCH_PAUSE_MS)
            }
        } finally {
            priorityValidationRunning.set(false)
            _validationActive.value = false
            _runtime.update { it.copy(isScanning = false) }
        }
    }

    private fun scheduleEpgPhase2() {
        android.util.Log.i(TAG, "Phase 2: scheduling EPG download")
        epgJobCoordinator.scheduleImportEpg()
    }

    private suspend fun hydrateFromDatabase() {
        val rows = channelScanDao.recentLimited(ChannelScanStatusCache.MAX_ENTRIES)
        if (rows.isEmpty()) return
        val loaded = rows.associate { row ->
            row.channelId to ChannelScanSnapshot(
                status = ChannelScanStatus.fromStored(row.status),
                lastCheckedAt = row.lastCheckedAt
            )
        }
        statusCache.replaceAll(loaded)
        channelScanDao.deleteOrphans()
        maybeEvictStatuses(includeOrphanCheck = true)
        publishStatusesNow()
        refreshRuntimeCounts()
        _runtime.update { it.copy(lastFullScanAt = repository.lastFullScanAt()) }
    }

    private suspend fun refreshSettingsLoop() {
        var backoffMs = SETTINGS_POLL_MS
        while (true) {
            val ok = runSafely("refreshSettings") {
                val appSettings = repository.loadSettings()
                updateSettings(
                    ScannerSettings(
                        autoScanEnabled = appSettings.autoScanEnabled,
                        scanIntervalMinutes = appSettings.scanIntervalMinutes,
                        concurrentChecks = appSettings.concurrentChecks,
                        scanOnMetered = appSettings.scanOnMetered
                    )
                )
            }
            val delayMs = if (ok) {
                backoffMs = SETTINGS_POLL_MS
                SETTINGS_POLL_MS
            } else {
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                backoffMs
            }
            delaySafely(delayMs)
        }
    }

    private suspend fun scanSchedulerLoop() {
        var backoffMs = SCAN_TICK_MS
        while (true) {
            val ok = runSafely("scanScheduler") {
                if (canRunBackgroundScans()) {
                    runScanCycle()
                } else if (scansSuspendedForPlayback) {
                    probesSkippedDuringPlayback++
                    if (probesSkippedDuringPlayback % PLAYBACK_SKIP_LOG_EVERY == 0) {
                        scanMetrics.logProbeSkippedForPlayback(probesSkippedDuringPlayback)
                    }
                }
            }
            val delayMs = if (ok) {
                backoffMs = SCAN_TICK_MS
                SCAN_TICK_MS
            } else {
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                backoffMs
            }
            delaySafely(delayMs)
        }
    }

    private fun canRunBackgroundScans(): Boolean =
        maintenanceScanUnlocked &&
            settings.autoScanEnabled &&
            appInForeground &&
            !scansSuspendedForPlayback &&
            !priorityValidationRunning.get() &&
            !isMeteredBlocked() &&
            !isLowOnMemory() &&
            !epgDownloadTracker.isInProgress() &&
            !playlistImportCoordinator.isImportActive()

    private suspend fun runScanCycle() {
        val total = channelDao.countTotal()
        if (total == 0) return

        scanCycleCount++
        if (scanCycleCount % EVICT_EVERY_N_CYCLES == 0) {
            maybeEvictStatuses(includeOrphanCheck = scanCycleCount % (EVICT_EVERY_N_CYCLES * 5) == 0)
        }

        val now = System.currentTimeMillis()
        val maxProbe = SCAN_BATCH_SIZE
        val toProbe = LinkedHashSet<ChannelScanProbeRow>(maxProbe)

        if (priorityChannelIds.isNotEmpty()) {
            val priorityRows = channelDao.scanProbeByIds(priorityChannelIds.toList())
            for (row in priorityRows) {
                if (forceFullScan || isDue(row.id, statusCache.get(row.id), now)) {
                    toProbe.add(row)
                    if (toProbe.size >= maxProbe) break
                }
            }
        }

        if (toProbe.size < maxProbe) {
            val pageSize = SCAN_PAGE_SIZE.coerceAtMost(total)
            var pagesScanned = 0
            val maxPages = ((total + pageSize - 1) / pageSize).coerceAtMost(MAX_PAGES_PER_CYCLE)

            while (toProbe.size < maxProbe && pagesScanned < maxPages) {
                val offset = scanBatchOffset
                val batch = channelDao.scanProbeBatch(pageSize, offset)
                scanBatchOffset = if (total <= pageSize) 0 else (offset + pageSize) % total
                pagesScanned++
                if (batch.isEmpty()) break

                for (row in batch) {
                    if (priorityChannelIds.contains(row.id)) continue
                    if (forceFullScan || isDue(row.id, statusCache.get(row.id), now)) {
                        toProbe.add(row)
                        if (toProbe.size >= maxProbe) break
                    }
                }
            }
        }

        if (toProbe.isEmpty()) {
            if (forceFullScan) {
                forceFullScan = false
                repository.updateLastFullScanAt(now)
                _runtime.update { it.copy(lastFullScanAt = now, isScanning = false) }
            }
            _validationActive.value = false
            refreshRuntimeCounts()
            return
        }

        _validationActive.value = true
        _runtime.update { it.copy(isScanning = true, totalCount = total) }

        val probeList = toProbe.toList()
        probeList.chunked(SCAN_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            if (scansSuspendedForPlayback) return@forEachIndexed
            val remaining = (probeList.size - (batchIndex * SCAN_BATCH_SIZE)).coerceAtLeast(0)
            scanMetrics.logBatchStart(batchIndex, batch.size, remaining)
            coroutineScope {
                batch.map { row ->
                    async {
                        limiter.withPermit { checkChannel(row.id, row.streamUrl) }
                    }
                }.awaitAll()
            }
            scanMetrics.logBatchEnd(batchIndex, batch.size)
            yield()
            delay(BATCH_PAUSE_MS)
        }

        refreshRuntimeCounts()
        _runtime.update { it.copy(isScanning = false) }
        _validationActive.value = false
    }

    private suspend fun checkChannel(channelId: Long, streamUrl: String) {
        if (scansSuspendedForPlayback) return
        markChecking(channelId)
        val detail = probe.probe(streamUrl)
        val snapshot = ChannelScanSnapshot(
            status = when (detail.result) {
                ProbeResult.LIVE -> ChannelScanStatus.LIVE
                ProbeResult.DEAD -> ChannelScanStatus.DEAD
                ProbeResult.UNKNOWN -> ChannelScanStatus.UNKNOWN
            },
            lastCheckedAt = System.currentTimeMillis(),
            responseCode = detail.responseCode,
            latencyMs = detail.latencyMs
        )
        persistSnapshot(channelId, snapshot)
        putStatus(channelId, snapshot)
    }

    private suspend fun markChecking(channelId: Long) {
        statusCache.put(
            channelId,
            ChannelScanSnapshot(
                status = ChannelScanStatus.CHECKING,
                lastCheckedAt = statusCache.get(channelId)?.lastCheckedAt
            )
        )
    }

    private fun putStatus(channelId: Long, snapshot: ChannelScanSnapshot) {
        statusCache.put(channelId, snapshot)
        if (snapshot.status != ChannelScanStatus.CHECKING) {
            schedulePublishStatuses()
        }
    }

    private fun schedulePublishStatuses() {
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            _statuses.value = statusCache.snapshot()
        }
    }

    private fun publishStatusesNow() {
        publishJob?.cancel()
        _statuses.value = statusCache.snapshot()
    }

    private suspend fun maybeEvictStatuses(includeOrphanCheck: Boolean) {
        var removed = statusCache.evict(
            maxAgeMs = ChannelScanStatusCache.MAX_AGE_MS,
            validChannelIds = null
        )
        if (includeOrphanCheck) {
            removed += channelScanDao.deleteOrphans()
            removed += evictOrphanCacheEntries()
        }
        if (removed > 0) {
            android.util.Log.i(
                TAG,
                "Evicted $removed scan status entries (cacheSize=${statusCache.size()}, orphanCheck=$includeOrphanCheck)"
            )
            publishStatusesNow()
        }
    }

    private suspend fun evictOrphanCacheEntries(): Int {
        val keys = statusCache.keySet()
        if (keys.isEmpty()) return 0
        var removed = 0
        keys.chunked(ORPHAN_CHECK_BATCH).forEach { batch ->
            val existing = channelDao.filterExistingIds(batch).toSet()
            batch.filter { it !in existing }.forEach { orphanId ->
                if (statusCache.remove(orphanId)) removed++
            }
        }
        return removed
    }

    private suspend fun persistSnapshot(channelId: Long, snapshot: ChannelScanSnapshot) {
        if (snapshot.status == ChannelScanStatus.CHECKING) return
        stateMutex.withLock {
            channelScanDao.upsert(
                ChannelScanEntity(
                    channelId = channelId,
                    status = snapshot.status.name,
                    lastCheckedAt = snapshot.lastCheckedAt ?: System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun refreshRuntimeCounts() {
        val statuses = statusCache.snapshot()
        val live = statuses.values.count { it.status == ChannelScanStatus.LIVE }
        val total = channelDao.countTotal()
        _runtime.update { it.copy(liveCount = live, totalCount = total) }
    }

    private fun isDue(channelId: Long, snapshot: ChannelScanSnapshot?, now: Long): Boolean {
        if (snapshot == null || snapshot.status == ChannelScanStatus.UNKNOWN) return true
        if (snapshot.status == ChannelScanStatus.CHECKING) return false
        val last = snapshot.lastCheckedAt ?: return true
        val liveIntervalMs = settings.scanIntervalMinutes.coerceAtLeast(1) * 60_000L
        val deadIntervalMs = (settings.scanIntervalMinutes / 2).coerceAtLeast(1) * 60_000L
        val interval = when (snapshot.status) {
            ChannelScanStatus.LIVE -> liveIntervalMs
            ChannelScanStatus.DEAD -> deadIntervalMs
            ChannelScanStatus.UNKNOWN -> 0L
            ChannelScanStatus.CHECKING -> return false
        }
        return now - last >= interval.coerceAtLeast(VERIFIED_TTL_MS)
    }

    private fun isMeteredBlocked(): Boolean {
        if (settings.scanOnMetered) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not() &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI).not() &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET).not()
    }

    private fun isLowOnMemory(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) return true
        val usedRatio = 1.0 - (info.availMem.toDouble() / info.totalMem.coerceAtLeast(1L).toDouble())
        return usedRatio > LOW_MEMORY_USED_RATIO
    }

    private suspend fun delaySafely(ms: Long) {
        try {
            delay(ms)
        } catch (_: OutOfMemoryError) {
            runCatching { System.gc() }
            Thread.sleep(ms.coerceAtMost(MAX_BACKOFF_MS))
        }
    }

    private suspend fun runSafely(label: String, block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (oom: OutOfMemoryError) {
            android.util.Log.e(TAG, "OOM in ChannelScanner.$label — backing off", oom)
            runCatching { System.gc() }
            false
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "ChannelScanner.$label failed: ${t.message}", t)
            true
        }
    }

    private companion object {
        private const val TAG = "ChannelScanner"
        private const val SCAN_TICK_MS = 2_000L
        private const val SETTINGS_POLL_MS = 5_000L
        private const val MAX_BACKOFF_MS = 120_000L
        private const val SCAN_PAGE_SIZE = 200
        private const val MAX_PAGES_PER_CYCLE = 8
        private const val SCAN_BATCH_SIZE = 25
        private const val BATCH_PAUSE_MS = 100L
        private const val VALIDATION_POLL_MS = 500L
        private const val LOW_MEMORY_USED_RATIO = 0.85
        private const val EVICT_EVERY_N_CYCLES = 10
        private const val PUBLISH_DEBOUNCE_MS = 400L
        private const val ORPHAN_CHECK_BATCH = 500
        private const val BOOT_MAINTENANCE_MIN_DELAY_MS = 45_000L
        private const val MAINTENANCE_IDLE_POLL_MS = 5_000L
        private const val PLAYBACK_SKIP_LOG_EVERY = 15
        /** Minimum time between stream probes once a channel has been verified. */
        private const val VERIFIED_TTL_MS = 12L * 60L * 60L * 1000L
    }
}
