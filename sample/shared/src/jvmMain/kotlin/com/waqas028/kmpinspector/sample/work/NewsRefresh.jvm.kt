package com.waqas028.kmpinspector.sample.work

import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.sample.SampleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** No WorkManager here, so the refresh is an in-process loop on the same interval. */
internal actual fun startNewsRefresh(scope: CoroutineScope) {
    scope.launch {
        while (true) {
            delay(REFRESH_INTERVAL_MINUTES * 60_000L)
            InspectorLog.i("Sync", "Periodic refresh (in-process, no WorkManager on this platform)")
            SampleApp.repository.refresh()
        }
    }
}

// No WorkManager on this platform, and the inspector hides the tab here anyway.
internal actual fun observeWorkForInspector(scope: CoroutineScope) = Unit
