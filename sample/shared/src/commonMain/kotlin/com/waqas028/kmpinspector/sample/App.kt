package com.waqas028.kmpinspector.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.KmpInspector
import com.waqas028.kmpinspector.sample.data.NewsUiState
import com.waqas028.kmpinspector.sample.data.publishDatabaseToInspector
import com.waqas028.kmpinspector.sample.work.observeWorkForInspector
import com.waqas028.kmpinspector.sample.work.startNewsRefresh
import kotlinx.coroutines.launch

@Composable
fun App() {
    var state by remember { mutableStateOf(NewsUiState(loading = true)) }
    val scope = rememberCoroutineScope()

    suspend fun load(isRefresh: Boolean) {
        val repository = SampleApp.repository
        val user = repository.ensureUser()

        // Show whatever is cached first: a failed refresh should never blank the screen.
        val cached = repository.cached()
        state = state.copy(articles = cached, user = user, loading = true, message = null)

        repository.refresh()
        val articles = repository.cached()

        state = if (articles.isEmpty()) {
            // Nothing real to show — seed the handoff fixtures so the inspector is still worth
            // opening, and say so on screen rather than passing them off as live traffic.
            seedDemoData(nowMillis())
            InspectorLog.w("News", "No articles available; falling back to demo data")
            state.copy(
                loading = false,
                usingDemoData = true,
                message = "Offline or feed unavailable — showing demo data",
            )
        } else {
            publishDatabaseToInspector(SampleApp.database)
            state.copy(articles = articles, loading = false, usingDemoData = false, message = null)
        }
        if (isRefresh) InspectorLog.i("News", "Manual refresh finished")
    }

    LaunchedEffect(Unit) {
        Inspector.configure(appId = "com.waqas028.kmpinspector.sample", variant = "debug")

        // Enqueue before observing: reporting first would look at an empty queue and the
        // Background Work tab would stay blank until something else refreshed it.
        startNewsRefresh(this)
        observeWorkForInspector(this)

        load(isRefresh = false)
    }

    MaterialTheme {
        KmpInspector {
            NewsScreen(
                state = state,
                onRefresh = { scope.launch { load(isRefresh = true) } },
            )
        }
    }
}
