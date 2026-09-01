package com.waqas028.kmpinspector.sample.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.WorkJob
import com.waqas028.kmpinspector.domain.model.WorkState
import com.waqas028.kmpinspector.sample.SampleApp
import com.waqas028.kmpinspector.sample.requireAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NewsRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refreshed = SampleApp.repository.refresh()
        // Result.retry() surfaces WorkManager's backoff in the inspector's Background Work tab.
        return if (refreshed) Result.success() else Result.retry()
    }
}

internal actual fun startNewsRefresh(scope: CoroutineScope) {
    val request = PeriodicWorkRequestBuilder<NewsRefreshWorker>(
        REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES,
    )
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        )
        .addTag("news")
        .build()

    WorkManager.getInstance(requireAppContext()).enqueueUniquePeriodicWork(
        REFRESH_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}

internal actual fun observeWorkForInspector(scope: CoroutineScope) {
    val manager = WorkManager.getInstance(requireAppContext())
    scope.launch {
        manager.getWorkInfosByTagFlow("news").collect { infos ->
            Inspector.setWork(
                engineLabel = "WorkManager 2.11",
                jobs = infos.map { info ->
                    WorkJob(
                        id = info.id.toString().take(9),
                        name = "NewsRefreshWorker",
                        state = info.state.toInspectorState(),
                        tag = "news",
                        attempt = info.runAttemptCount + 1,
                        nextRun = "every $REFRESH_INTERVAL_MINUTES min (periodic)",
                        constraints = listOf("NETWORK: CONNECTED"),
                    )
                },
            )
        }
    }
}

private fun WorkInfo.State.toInspectorState(): WorkState = when (this) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkState.Enqueued
    WorkInfo.State.RUNNING -> WorkState.Running
    WorkInfo.State.SUCCEEDED -> WorkState.Succeeded
    WorkInfo.State.FAILED -> WorkState.Failed
    WorkInfo.State.CANCELLED -> WorkState.Cancelled
}
