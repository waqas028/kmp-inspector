package com.waqas028.kmpinspector.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.WorkJob
import com.waqas028.kmpinspector.domain.model.WorkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Mirrors every WorkManager job into the Background Work panel.
 *
 * WorkManager is a compileOnly dependency of the library, so nothing here is touched unless
 * [isAvailable] says the host app ships it. Keep every reference to `androidx.work` inside this
 * file for that reason.
 */
internal object WorkManagerCollector {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun isAvailable(): Boolean =
        runCatching { Class.forName("androidx.work.WorkManager") }.isSuccess

    fun start(context: Context) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val label = "WorkManager"

        manager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.entries))
            .onEach { infos -> Inspector.setWork(infos.map { it.toWorkJob() }, label) }
            .launchIn(scope)
    }

    private fun WorkInfo.toWorkJob(): WorkJob {
        // WorkManager tags every request with its worker class; the unique work name is not
        // exposed on WorkInfo, so the class name is the best label available.
        val classTag = tags.firstOrNull { it.contains('.') }
        val userTags = tags.filter { it != classTag }
        val next = runCatching { nextScheduleTimeMillis }.getOrNull()
            ?.takeIf { it != Long.MAX_VALUE && state == WorkInfo.State.ENQUEUED }
        val periodic = runCatching { periodicityInfo }.getOrNull()

        return WorkJob(
            id = id.toString().take(8),
            name = classTag?.substringAfterLast('.') ?: "Work",
            state = state.toInspectorState(),
            tag = userTags.joinToString().ifEmpty { null },
            attempt = runAttemptCount + 1,
            nextRun = when {
                periodic != null -> "every ${periodic.repeatIntervalMillis / 60_000} min" +
                    (next?.let { " · at ${formatClock(it, withMillis = false)}" } ?: "")
                next != null -> "at ${formatClock(next, withMillis = false)}"
                else -> null
            },
            constraints = runCatching { constraints.describe() }.getOrDefault(emptyList()),
            outputData = outputData.keyValueMap.map { (k, v) -> k to v.toString() },
            failureReason = runCatching { stopReason }.getOrNull()
                ?.takeIf { it != WorkInfo.STOP_REASON_NOT_STOPPED }
                ?.let { "stop reason $it" },
        )
    }

    private fun Constraints.describe(): List<String> = buildList {
        if (requiredNetworkType != NetworkType.NOT_REQUIRED) add("NETWORK: $requiredNetworkType")
        if (requiresCharging()) add("CHARGING")
        if (requiresBatteryNotLow()) add("BATTERY NOT LOW")
        if (requiresStorageNotLow()) add("STORAGE NOT LOW")
        if (requiresDeviceIdle()) add("DEVICE IDLE")
    }

    private fun WorkInfo.State.toInspectorState(): WorkState = when (this) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkState.Enqueued
        WorkInfo.State.RUNNING -> WorkState.Running
        WorkInfo.State.SUCCEEDED -> WorkState.Succeeded
        WorkInfo.State.FAILED -> WorkState.Failed
        WorkInfo.State.CANCELLED -> WorkState.Cancelled
    }
}
