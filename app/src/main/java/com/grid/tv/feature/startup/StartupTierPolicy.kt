package com.grid.tv.feature.startup

import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.player.LowEndDeviceMode

/**
 * Staggered startup delays — Tier 1 is immediate (UI shell), Tier 2 after first frame,
 * Tier 3 several seconds later for EPG refresh, VOD network sync, analytics, workers.
 */
object StartupTierPolicy {

    fun tier2DelayMs(): Long = if (LowEndDeviceMode.current().active) 1_200L else 400L

    fun tier3DelayMs(): Long = if (LowEndDeviceMode.current().active) 12_000L else 5_000L

    /** Guide channel page + EPG hydrate — after UI is visible. */
    fun guideBootstrapDelayMs(): Long = if (LowEndDeviceMode.current().active) 600L else 150L

    fun epgHydrateDelayMs(): Long = if (LowEndDeviceMode.current().active) 800L else 300L

    fun deferredVodRefreshDelayMs(trigger: VodRefreshTrigger): Long = when (trigger) {
        VodRefreshTrigger.VOD_HUB_MOUNT,
        VodRefreshTrigger.MANUAL_RETRY -> if (LowEndDeviceMode.current().active) 3_000L else 1_500L
        else -> tier3DelayMs()
    }

    /** Faster retry when the on-disk catalog is empty and the hub needs content. */
    fun emptyCatalogVodRefreshDelayMs(): Long = 0L

    fun vodPagingInitialLoadSize(pageSize: Int): Int =
        if (LowEndDeviceMode.current().active) pageSize else pageSize * 2

    fun recommendationSampleSize(): Int = if (LowEndDeviceMode.current().active) 150 else 500
}
