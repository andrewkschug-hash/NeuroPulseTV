package com.grid.tv.domain.model

/** Identifies why a VOD/series catalog refresh was requested (for pipeline logging). */
enum class VodRefreshTrigger {
    UNKNOWN,
    REPOSITORY_INIT,
    VOD_HUB_MOUNT,
    MOVIES_VIEW_MODEL,
    SERIES_VIEW_MODEL,
    MANUAL_RETRY,
    PLAYLIST_CONNECT,
    ONBOARDING,
    SETTINGS,
    /** WorkManager periodic background sync — respects TTL unless stale. */
    BACKGROUND_SYNC
}
