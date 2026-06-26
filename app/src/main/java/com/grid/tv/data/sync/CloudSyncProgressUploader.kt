package com.grid.tv.data.sync

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Queues watch-progress uploads when cloud auth is available.
 * Falls back to local-only until Supabase sync is fully enabled.
 */
@Singleton
class CloudSyncProgressUploader @Inject constructor(
    private val delegate: CloudSyncClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun uploadIfSignedIn(profileId: Long, payload: WatchProgressSyncPayload) {
        scope.launch {
            if (!delegate.isSignedIn()) return@launch
            runCatching { delegate.syncWatchProgress(profileId, payload) }
        }
    }
}

/** Local stub — replaced by Supabase implementation when cloud sync ships. */
@Singleton
class LocalOnlyCloudSyncClient @Inject constructor() : CloudSyncClient {
    override suspend fun isSignedIn(): Boolean = false

    override suspend fun syncSettings(profileId: Long, settings: AppSettings) = Unit

    override suspend fun syncProfiles(profiles: List<UserProfile>) = Unit

    override suspend fun syncWatchProgress(profileId: Long, payload: WatchProgressSyncPayload) = Unit

    override suspend fun createTvPairingSession(): TvPairingSession? = null
}
