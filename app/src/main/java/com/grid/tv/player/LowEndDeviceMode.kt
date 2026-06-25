package com.grid.tv.player

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log

/**
 * Automatic performance profile for constrained Android TV devices (≤2 GB RAM, [ActivityManager.isLowRamDevice]).
 *
 * Prioritizes UI responsiveness and memory headroom over playback resilience extras.
 */
object LowEndDeviceMode {

    data class Profile(
        val active: Boolean,
        val totalRamMb: Long,
        val isLowRamDevice: Boolean,
        val maxPaneCount: Int,
        val coilMemoryCacheBytes: Long,
        val coilDiskCacheBytes: Long,
        val liveStartupPriority: PlaybackStartupPriority,
        val maxBufferMsCap: Int,
        val telemetryVerbose: Boolean,
        val performanceAuditEnabled: Boolean,
        val epgStartupDelaySec: Long,
        val watchdogPollIntervalMs: Long,
        val watchDurationTickMs: Long,
        val decodeOnlyMultiPaneAudio: Boolean,
        val deferChannelHealthProbe: Boolean
    )

    @Volatile
    private var cached: Profile? = null

    fun init(context: Context) {
        cached = compute(context.applicationContext)
        Log.i(
            TAG,
            "profile active=${cached?.active} ramMb=${cached?.totalRamMb} panes=${cached?.maxPaneCount} " +
                "coilMemMb=${(cached?.coilMemoryCacheBytes ?: 0) / (1024 * 1024)} " +
                "bufferCapMs=${cached?.maxBufferMsCap} audit=${cached?.performanceAuditEnabled}"
        )
    }

    fun profile(context: Context): Profile =
        cached ?: compute(context.applicationContext).also { cached = it }

    fun isActive(context: Context): Boolean = profile(context).active

    /** True when the constrained-device profile is active (≤2 GB RAM / low-RAM TV). */
    fun isEnabled(): Boolean = current().active

    fun current(): Profile = cached ?: HIGH_END_DEFAULT

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.w(TAG, "trimMemory level=$level — recommend clearing image cache")
        }
    }

    private fun compute(context: Context): Profile {
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem) / (1024 * 1024)
        val lowRam = am?.isLowRamDevice == true
        val lowMemoryClass = (am?.memoryClass ?: 256) < 192
        val active = lowRam || totalRamMb in 1..2048 || lowMemoryClass

        if (!active) {
            return HIGH_END_DEFAULT.copy(totalRamMb = totalRamMb, isLowRamDevice = lowRam)
        }

        val maxPanes = when {
            totalRamMb <= 2048 || lowRam -> 2
            totalRamMb <= 3072 -> 3
            else -> 4
        }

        return Profile(
            active = true,
            totalRamMb = totalRamMb,
            isLowRamDevice = lowRam,
            maxPaneCount = maxPanes,
            coilMemoryCacheBytes = 16L * 1024 * 1024,
            coilDiskCacheBytes = 20L * 1024 * 1024,
            liveStartupPriority = PlaybackStartupPriority.BALANCED,
            maxBufferMsCap = 60_000,
            telemetryVerbose = false,
            performanceAuditEnabled = false,
            epgStartupDelaySec = 90L,
            watchdogPollIntervalMs = 4_000L,
            watchDurationTickMs = 10_000L,
            decodeOnlyMultiPaneAudio = true,
            deferChannelHealthProbe = true
        )
    }

    private val HIGH_END_DEFAULT = Profile(
        active = false,
        totalRamMb = 4096,
        isLowRamDevice = false,
        maxPaneCount = 4,
        coilMemoryCacheBytes = 48L * 1024 * 1024,
        coilDiskCacheBytes = 50L * 1024 * 1024,
        liveStartupPriority = PlaybackStartupPriority.STABLE,
        maxBufferMsCap = 300_000,
        telemetryVerbose = true,
        performanceAuditEnabled = true,
        epgStartupDelaySec = 5L,
        watchdogPollIntervalMs = 2_000L,
        watchDurationTickMs = 5_000L,
        decodeOnlyMultiPaneAudio = false,
        deferChannelHealthProbe = false
    )

    private const val TAG = "LowEndDeviceMode"
}
