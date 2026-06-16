package com.grid.tv.data.sync

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only stub until Supabase is wired for auth and sync. */
@Singleton
class LocalOnlyCloudSyncClient @Inject constructor() : CloudSyncClient {
    override suspend fun isSignedIn(): Boolean = false

    override suspend fun syncSettings(profileId: Long, settings: AppSettings) = Unit

    override suspend fun syncProfiles(profiles: List<UserProfile>) = Unit

    override suspend fun syncWatchProgress(profileId: Long, payload: WatchProgressSyncPayload) = Unit

    override suspend fun createTvPairingSession(): TvPairingSession? = null
}
