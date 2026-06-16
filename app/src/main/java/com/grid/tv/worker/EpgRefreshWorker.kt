package com.grid.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.recording.SeriesRuleScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        repository.refreshEpgNow()
        seriesRuleScheduler.applyRulesAfterEpgRefresh()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
