package com.grid.tv.feature.startup

import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.player.LowEndDeviceMode

/**
 * Staggered startup delays — Phase 1 immediate (persisted UI), Phase 2 after first frame,
 * Phase 3 idle window for EPG / network VOD sync.
 */
object StartupTierPolicy {

    /** After DB prewarm — Phase 2A applies cached counts (instant, no COUNT query). */
    fun phase2DelayMs(): Long = if (LowEndDeviceMode.current().active) 3_000L else 1_500L

    /** No SQLite COUNT queries before this elapsed time from cold start. */
    fun phase2InteractiveDelayMs(): Long = if (LowEndDeviceMode.current().active) 6_000L else 5_000L

    /** Total time from process start before Phase 3 (EPG schedule + VOD maintenance). */
    fun phase3DelayMs(): Long = if (LowEndDeviceMode.current().active) 12_000L else 8_000L

    /** Idle disk window after last DAO before any OkHttp / worker scheduling. */
    fun networkIdleAfterDiskMs(): Long = 2_000L

    /** Quiet window with no UI activity before UI_IDLE. */
    fun uiIdleQuietMs(): Long = 500L

    /** Pause between SQLite COUNT queries when phase2b runs after input-safe. */
    fun phase2CountChunkDelayMs(): Long = if (LowEndDeviceMode.current().active) 200L else 100L

    /** Max wait for UI_IDLE before INPUT_SAFE fallback. */
    fun uiIdleTimeoutMs(): Long = if (LowEndDeviceMode.current().active) 20_000L else 15_000L

    @Deprecated("Use uiIdleQuietMs", ReplaceWith("uiIdleQuietMs()"))
    fun uiStableQuietMs(): Long = uiIdleQuietMs()

    @Deprecated("Use uiIdleTimeoutMs", ReplaceWith("uiIdleTimeoutMs()"))
    fun uiStableTimeoutMs(): Long = uiIdleTimeoutMs()

    @Deprecated("Use phase2DelayMs", ReplaceWith("phase2DelayMs()"))
    fun tier2DelayMs(): Long = phase2DelayMs()

    @Deprecated("Use phase3DelayMs", ReplaceWith("phase3DelayMs()"))
    fun tier3DelayMs(): Long = phase3DelayMs()

    /** Guide channel page + EPG hydrate — after UI is visible. */
    fun guideBootstrapDelayMs(): Long = if (LowEndDeviceMode.current().active) 600L else 150L

    /** First guide channel SQL page — keep small so the grid is interactive before full paging. */
    fun guideInitialChannelPageSize(): Int = if (LowEndDeviceMode.current().active) 40 else 80

    /** EPG programme rows loaded during the first bootstrap channel page. */
    fun guideBootstrapEpgChannelCount(): Int = if (LowEndDeviceMode.current().active) 16 else 32

    /** Defer GROUP BY group metadata until after the guide accepts input. */
    fun guideGroupMetadataDelayMs(): Long = if (LowEndDeviceMode.current().active) 3_000L else 1_500L

    fun epgHydrateDelayMs(): Long = if (LowEndDeviceMode.current().active) 800L else 300L

    fun deferredVodRefreshDelayMs(trigger: VodRefreshTrigger): Long = when (trigger) {
        VodRefreshTrigger.VOD_HUB_MOUNT,
        VodRefreshTrigger.MANUAL_RETRY -> if (LowEndDeviceMode.current().active) 3_000L else 1_500L
        else -> phase3DelayMs()
    }

    /** Faster retry when the on-disk catalog is empty and the hub needs content. */
    fun emptyCatalogVodRefreshDelayMs(): Long = 0L

    fun vodPagingInitialLoadSize(pageSize: Int): Int =
        if (LowEndDeviceMode.current().active) pageSize else pageSize * 2

    fun recommendationSampleSize(): Int = if (LowEndDeviceMode.current().active) 150 else 500
}
