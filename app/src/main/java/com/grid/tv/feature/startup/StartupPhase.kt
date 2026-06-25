package com.grid.tv.feature.startup

/**
 * Cold-start phases.
 *
 * SQLite COUNT queries and channel page warm run only after [PHASE2_SAFE]
 * (see [StartupTierPolicy.phase2InteractiveDelayMs]).
 */
enum class StartupPhase {
    BOOTING,
    /** Persisted counts + settings only; UI may render. */
    PHASE1_READY,
    /** Phase 2A — applying cached counts (instant, no DB COUNT). */
    PHASE2_RUNNING,
    /** Cached counts published; pipeline continues without waiting for DB. */
    PHASE2_CACHE_READY,
    /** UI interactive window elapsed — background COUNT queries allowed. */
    PHASE2_SAFE,
    /** Background COUNT + channel page warm finished. */
    PHASE2_COMPLETE,
    PHASE3_RUNNING,
    READY
}
