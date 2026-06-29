package com.grid.tv.feature.vod

/** Why series catalog hydration was requested — used for debug lifecycle tracing. */
enum class VodSeriesHydrationReason {
    BOOTSTRAP,
    DEEP_LINK,
    TAB_SELECT,
}
