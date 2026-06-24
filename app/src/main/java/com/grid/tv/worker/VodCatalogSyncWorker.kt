package com.grid.tv.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.playlist.PlaylistImportCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Periodic background VOD catalog sync — incremental UPSERT into Room, no full catalog in memory.
 */
@HiltWorker
class VodCatalogSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IptvRepository,
    private val playlistImportCoordinator: PlaylistImportCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "VodCatalogSyncWorker started workId=$id attempt=$runAttemptCount")
        return try {
            if (playlistImportCoordinator.isImportActive()) {
                Log.i(TAG, "VodCatalogSyncWorker deferred — playlist import active")
                return Result.retry()
            }
            repository.refreshVodSeriesCatalog(
                trigger = VodRefreshTrigger.BACKGROUND_SYNC,
                force = false
            )
            Log.i(TAG, "VodCatalogSyncWorker finished OK")
            Result.success()
        } catch (e: CancellationException) {
            Log.i(TAG, "VodCatalogSyncWorker cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "VodCatalogSyncWorker failed: ${e.message}", e)
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "VodCatalogPipeline"
        private const val MAX_ATTEMPTS = 3
    }
}
