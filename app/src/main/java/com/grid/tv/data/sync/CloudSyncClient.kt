package com.grid.tv.data.sync

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.UserProfile

/**
 * Cloud sync boundary for Supabase-backed features only:
 * auth, settings, profiles, watch progress, and TV pairing.
 *
 * Title metadata, channel lists, and bulk content stay on-device.
 */
// TODO: replace with real Supabase SDK when cloud sync is enabled
interface CloudSyncClient {
    suspend fun isSignedIn(): Boolean
    suspend fun syncSettings(profileId: Long, settings: AppSettings)
    suspend fun syncProfiles(profiles: List<UserProfile>)
    suspend fun syncWatchProgress(profileId: Long, payload: WatchProgressSyncPayload)
    suspend fun createTvPairingSession(): TvPairingSession?
}

data class WatchProgressSyncPayload(
    val contentKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long
)

data class TvPairingSession(
    val sessionCode: String,
    val expiresAtEpochSec: Long
)
