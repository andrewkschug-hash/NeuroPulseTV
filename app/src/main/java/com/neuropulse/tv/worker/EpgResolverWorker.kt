package com.neuropulse.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropulse.tv.domain.epg.EpgResolverEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

@HiltWorker
class EpgResolverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resolverEngine: EpgResolverEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val createdAfter = inputData.getLong(KEY_CREATED_AFTER, 0L)
        resolverEngine.resolveAllUnmatched(createdAfter).collect()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        const val KEY_CREATED_AFTER = "created_after"
    }
}
