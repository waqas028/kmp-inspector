package com.waqas028.kmpinspector.sample.work

import kotlinx.coroutines.CoroutineScope

/** WorkManager's own floor is 15 minutes; an hour is plenty for a news feed. */
internal const val REFRESH_INTERVAL_MINUTES = 60L

internal const val REFRESH_WORK_NAME = "news-refresh"

/**
 * Android schedules real WorkManager work. WorkManager has no iOS or desktop equivalent, so those
 * platforms run an in-process coroutine on the same interval — which is also why the inspector's
 * Background Work tab only exists on Android.
 */
internal expect fun startNewsRefresh(scope: CoroutineScope)

/**
 * Keeps the inspector's Background Work tab in step with WorkManager for as long as [scope] lives.
 * A one-shot read would catch the job once and then go stale as it moves ENQUEUED -> RUNNING ->
 * SUCCEEDED. A no-op where there is no WorkManager.
 */
internal expect fun observeWorkForInspector(scope: CoroutineScope)
