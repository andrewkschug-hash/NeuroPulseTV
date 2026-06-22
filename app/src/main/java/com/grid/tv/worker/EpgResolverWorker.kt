package com.grid.tv.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.domain.epg.EpgResolverEngine
import com.grid.tv.domain.repository.IptvRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

@HiltWorker
class EpgResolverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resolverEngine: EpgResolverEngine,
    private val repository: IptvRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val createdAfter = inputData.getLong(KEY_CREATED_AFTER, 0L)
        Log.i(TAG, "EpgResolverWorker started createdAfter=$createdAfter")
        var lastProgress: String? = null
        resolverEngine.resolveAllUnmatched(createdAfter).collect { progress ->
            val line = "resolve progress ${progress.completed}/${progress.total} " +
                "auto=${progress.autoMatched} suggested=${progress.suggested} failed=${progress.failed}"
            if (line != lastProgress) {
                Log.d(TAG, line)
                lastProgress = line
            }
        }
        repository.notifyEpgLinksUpdated()
        Log.i(TAG, "EpgResolverWorker finished OK")
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { error ->
            Log.e(TAG, "EpgResolverWorker failed", error)
            Result.retry()
        }
    )

    companion object {
        const val KEY_CREATED_AFTER = "created_after"
        private const val TAG = "EpgFlow"
    }
}
