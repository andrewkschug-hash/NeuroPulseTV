package com.neuropulse.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IptvRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        repository.refreshEpgNow()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
