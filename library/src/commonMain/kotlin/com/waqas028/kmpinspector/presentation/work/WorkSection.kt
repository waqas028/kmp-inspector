package com.waqas028.kmpinspector.presentation.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.formatClock
import com.waqas028.kmpinspector.domain.model.WorkJob
import com.waqas028.kmpinspector.domain.model.WorkState
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.KeyValueRow
import com.waqas028.kmpinspector.presentation.common.Kicker
import com.waqas028.kmpinspector.presentation.common.OutlineChip
import com.waqas028.kmpinspector.presentation.common.StateBadge
import com.waqas028.kmpinspector.presentation.shell.MasterDetail
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorType

private fun WorkState.tone(): Color = when (this) {
    WorkState.Enqueued -> DebugPalette.neutralState
    WorkState.Running -> DebugPalette.warn
    WorkState.Succeeded -> DebugPalette.ok
    WorkState.Failed -> DebugPalette.bad
    WorkState.Cancelled -> DebugPalette.cancelled
}

private fun WorkState.glyph(): String = when (this) {
    WorkState.Enqueued -> "○"
    WorkState.Running -> "◔"
    WorkState.Succeeded -> "✓"
    WorkState.Failed -> "✕"
    WorkState.Cancelled -> "⊘"
}

@Composable
internal fun WorkSection(state: InspectorState, pane: PaneWidth) {
    val all = InspectorStore.work
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.WorkHistory,
            title = "No work scheduled",
            sentence = "Jobs enqueued through WorkManager appear here with their state, constraints and run history.",
            snippet = "WorkManager.getInstance(ctx).enqueue(syncRequest)",
            footnote = "Android only — the tab is absent on iOS and desktop",
        )
        return
    }

    val q = state.query.trim()
    val filtered = all.filter { q.isEmpty() || it.name.contains(q, true) || (it.tag?.contains(q, true) == true) }
    val selected = all.firstOrNull { it.id == state.selectedWorkId }

    MasterDetail(
        pane = pane,
        hasSelection = selected != null,
        onBack = { state.selectedWorkId = null },
        placeholder = "Select a job",
        list = {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "WorkManager 2.10 · ${all.size} jobs",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = InspectorType.meta,
                )
                Hairline()
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { job ->
                        WorkRow(job, job.id == state.selectedWorkId) { state.selectedWorkId = job.id }
                        Hairline(color = DebugPalette.lineFaint)
                    }
                }
            }
        },
        detail = { selected?.let { WorkDetail(it) } },
    )
}

/**
 * State first, because the question is almost always "did it run, and when does it run again".
 * Attempts sit opposite the badge — they only matter when something failed.
 */
@Composable
private fun WorkRow(job: WorkJob, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .background(if (selected) DebugPalette.accent else Color.Transparent),
        )
        Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateBadge(job.state.glyph(), job.state.name.uppercase(), job.state.tone())
                Box(Modifier.weight(1f))
                Text(
                    "attempt ${job.attempt}",
                    style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint, tabular = true),
                )
            }
            Text(
                job.name,
                modifier = Modifier.padding(top = 6.dp),
                style = InspectorType.mono(13.sp, FontWeight.Medium, DebugPalette.text),
            )
            Text(
                buildString {
                    append("last  ").append(job.lastRunMillis?.let { formatClock(it, withMillis = false) } ?: "—")
                    append("\nnext  ").append(job.nextRun ?: "—")
                },
                modifier = Modifier.padding(top = 4.dp),
                style = InspectorType.mono(
                    10.5.sp, color = DebugPalette.textFaint, lineHeight = 16.sp, tabular = true,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkDetail(job: WorkJob) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(job.name, style = InspectorType.mono(14.sp, FontWeight.Medium, DebugPalette.text))
        StateBadge(
            job.state.glyph(),
            job.state.name.uppercase(),
            job.state.tone(),
            Modifier.padding(top = 8.dp),
        )
        Text(
            buildString {
                append("id ").append(job.id)
                job.tag?.let { append(" · tag \"").append(it).append("\"") }
                append(" · attempt ").append(job.attempt)
            },
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(11.sp, color = DebugPalette.textDim, tabular = true),
        )

        Kicker("Schedule", Modifier.padding(top = 20.dp, bottom = 6.dp))
        KeyValueRow("last", job.lastRunMillis?.let { formatClock(it, withMillis = false) } ?: "—")
        KeyValueRow("next", job.nextRun ?: "—")

        if (job.constraints.isNotEmpty()) {
            Kicker("Constraints", Modifier.padding(top = 20.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                job.constraints.forEach { OutlineChip(it, Modifier.padding(vertical = 3.dp)) }
            }
        }

        if (job.inputData.isNotEmpty()) {
            Kicker("Input data", Modifier.padding(top = 20.dp, bottom = 6.dp))
            job.inputData.forEach { (k, v) -> KeyValueRow(k, v, keyWeight = 0.38f) }
        }

        // Omitted entirely when a job has produced none, rather than shown empty.
        if (job.outputData.isNotEmpty()) {
            Kicker("Output data", Modifier.padding(top = 20.dp, bottom = 6.dp))
            job.outputData.forEach { (k, v) -> KeyValueRow(k, v, keyWeight = 0.38f) }
        }

        if (job.failureReason != null) {
            val cancelled = job.state == WorkState.Cancelled
            Kicker(
                if (cancelled) "Cancellation reason" else "Failure reason",
                Modifier.padding(top = 20.dp, bottom = 6.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.width(2.dp).heightIn(min = 24.dp).fillMaxHeight()
                        .background(if (cancelled) DebugPalette.textDim else DebugPalette.bad),
                )
                Text(
                    job.failureReason,
                    modifier = Modifier.padding(start = 10.dp),
                    style = InspectorType.mono(11.5.sp, color = DebugPalette.text, lineHeight = 19.6.sp),
                )
            }
        }
    }
}
