package com.grid.tv.feature.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.entity.ChannelScanEntity
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.ChannelScanStatus
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.domain.model.ScannerSettings
import com.grid.tv.domain.repository.IptvRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.grid.tv.data.network.AppHttpClient

@Singleton
class ChannelScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val channelScanDao: ChannelScanDao,
    private val repository: IptvRepository,
    private val appHttpClient: AppHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun probeFor(url: String): ProbeResult = ChannelProbe(appHttpClient.client()).probe(url)
    private val limiter = ScanConcurrencyLimiter()
    private val stateMutex = Mutex()

    private val _statuses = MutableStateFlow<Map<Long, ChannelScanSnapshot>>(emptyMap())
    val statuses: StateFlow<Map<Long, ChannelScanSnapshot>> = _statuses.asStateFlow()

    private val _runtime = MutableStateFlow(ScannerRuntimeState())
    val runtime: StateFlow<ScannerRuntimeState> = _runtime.asStateFlow()

    @Volatile
    private var settings = ScannerSettings()

    @Volatile
    private var priorityChannelIds: Set<Long> = emptySet()

    @Volatile
    private var appInForeground = true

    @Volatile
    private var forceFullScan = false

    init {
        scope.launch { hydrateFromDatabase() }
        scope.launch { refreshSettingsLoop() }
        scope.launch { scanSchedulerLoop() }
    }

    fun setAppInForeground(visible: Boolean) {
        appInForeground = visible
    }

    fun setPriorityChannelIds(ids: Collection<Long>) {
        priorityChannelIds = ids.toSet()
    }

    fun updateSettings(newSettings: ScannerSettings) {
        settings = newSettings
        limiter.updateLimit(newSettings.concurrentChecks)
    }

    fun scanNow() {
        forceFullScan = true
    }

    fun reportPlaybackResult(channelId: Long, isLive: Boolean) {
        val status = if (isLive) ChannelScanStatus.LIVE else ChannelScanStatus.DEAD
        val snapshot = ChannelScanSnapshot(status = status, lastCheckedAt = System.currentTimeMillis())
        scope.launch {
            persistSnapshot(channelId, snapshot)
            _statuses.update { current -> current + (channelId to snapshot) }
            refreshRuntimeCounts()
        }
    }

    private suspend fun hydrateFromDatabase() {
        val rows = channelScanDao.all()
        if (rows.isEmpty()) return
        _statuses.value = rows.associate { row ->
            row.channelId to ChannelScanSnapshot(
                status = ChannelScanStatus.fromStored(row.status),
                lastCheckedAt = row.lastCheckedAt
            )
        }
        refreshRuntimeCounts()
        _runtime.update { it.copy(lastFullScanAt = repository.lastFullScanAt()) }
    }

    private suspend fun refreshSettingsLoop() {
        while (true) {
            runCatching {
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
            delay(5_000)
        }
    }

    private suspend fun scanSchedulerLoop() {
        while (true) {
            if (settings.autoScanEnabled && appInForeground && !isMeteredBlocked()) {
                runScanCycle()
            }
            delay(2_000)
        }
    }

    private suspend fun runScanCycle() {
        val channels = channelDao.all()
        if (channels.isEmpty()) return

        val now = System.currentTimeMillis()
        val currentStatuses = _statuses.value
        val due = channels.filter { channel ->
            forceFullScan || isDue(channel.id, currentStatuses[channel.id], now)
        }

        val ordered = buildList {
            due.filter { priorityChannelIds.contains(it.id) }.forEach { add(it) }
            due.filterNot { priorityChannelIds.contains(it.id) }.forEach { add(it) }
        }

        if (ordered.isEmpty()) {
            if (forceFullScan) {
                forceFullScan = false
                repository.updateLastFullScanAt(now)
                _runtime.update { it.copy(lastFullScanAt = now, isScanning = false) }
            }
            refreshRuntimeCounts()
            return
        }

        val batch = ordered.take(settings.concurrentChecks)
        _runtime.update { it.copy(isScanning = true, totalCount = channels.size) }

        coroutineScope {
            batch.map { channel ->
                async { checkChannel(channel.id, channel.streamUrl) }
            }.awaitAll()
        }

        refreshRuntimeCounts()
        _runtime.update { it.copy(isScanning = batch.isNotEmpty() && ordered.size > batch.size) }
    }

    private suspend fun checkChannel(channelId: Long, streamUrl: String) {
        markChecking(channelId)
        val result = limiter.withPermit { probeFor(streamUrl) }
        val snapshot = ChannelScanSnapshot(
            status = when (result) {
                ProbeResult.LIVE -> ChannelScanStatus.LIVE
                ProbeResult.DEAD -> ChannelScanStatus.DEAD
                ProbeResult.UNKNOWN -> ChannelScanStatus.UNKNOWN
            },
            lastCheckedAt = System.currentTimeMillis()
        )
        persistSnapshot(channelId, snapshot)
        _statuses.update { current -> current + (channelId to snapshot) }
    }

    private suspend fun markChecking(channelId: Long) {
        _statuses.update { current ->
            current + (channelId to ChannelScanSnapshot(ChannelScanStatus.CHECKING, current[channelId]?.lastCheckedAt))
        }
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
        val statuses = _statuses.value
        val live = statuses.values.count { it.status == ChannelScanStatus.LIVE }
        val total = channelDao.all().size
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
        return now - last >= interval
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
}
