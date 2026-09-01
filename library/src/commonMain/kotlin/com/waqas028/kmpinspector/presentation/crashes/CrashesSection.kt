package com.waqas028.kmpinspector.presentation.crashes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.formatClock
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.presentation.CrashFilter
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.FilterPill
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.HitTarget
import com.waqas028.kmpinspector.presentation.common.Kicker
import com.waqas028.kmpinspector.presentation.common.OutlineChip
import com.waqas028.kmpinspector.presentation.common.StateBadge
import com.waqas028.kmpinspector.presentation.shell.MasterDetail
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType

/**
 * Fatal and non-fatal are one time-ordered list with a filter, not two lists: the order in time is
 * what tells you a handled exception preceded the crash.
 */
@Composable
internal fun CrashesSection(state: InspectorState, pane: PaneWidth) {
    val all = InspectorStore.crashes
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.CheckCircle,
            glyphTint = DebugPalette.ok,
            title = "No crashes this session",
            sentence = "Handled exceptions you report show up here alongside any fatal crash, in the order they happened.",
            snippet = "Inspector.recordNonFatal(e)",
            footnote = "Buffer survives process death · cleared on uninstall",
        )
        return
    }

    val q = state.query.trim()
    val filtered = all.filter { c ->
        val matches = when (state.crashFilter) {
            CrashFilter.All -> true
            CrashFilter.Fatal -> c.fatal
            CrashFilter.NonFatal -> !c.fatal
        }
        matches && (q.isEmpty() || c.exceptionType.contains(q, true) || c.message.contains(q, true))
    }
    val selected = all.firstOrNull { it.id == state.selectedCrashId }

    MasterDetail(
        pane = pane,
        hasSelection = selected != null,
        onBack = { state.selectedCrashId = null },
        placeholder = "Select an exception",
        list = {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CrashFilter.entries.forEach { filter ->
                        val count = when (filter) {
                            CrashFilter.All -> all.size
                            CrashFilter.Fatal -> all.count { it.fatal }
                            CrashFilter.NonFatal -> all.count { !it.fatal }
                        }
                        FilterPill(
                            "${filter.label} · $count",
                            state.crashFilter == filter,
                            { state.crashFilter = filter },
                        )
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { crash ->
                        CrashRow(crash, crash.id == state.selectedCrashId) {
                            state.selectedCrashId = crash.id
                        }
                        Hairline(color = DebugPalette.lineFaint)
                    }
                }
            }
        },
        detail = { selected?.let { CrashDetail(it, state) } },
    )
}

@Composable
private fun CrashRow(crash: CrashRecord, selected: Boolean, onClick: () -> Unit) {
    val tone = if (crash.fatal) DebugPalette.bad else DebugPalette.warn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .background(if (selected) DebugPalette.accent else Color.Transparent),
        )
        Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateBadge(
                    glyph = if (crash.fatal) "✕" else "!",
                    word = if (crash.fatal) "FATAL" else "CAUGHT",
                    tone = tone,
                )
                Box(Modifier.weight(1f))
                if (crash.occurrences > 1) {
                    OutlineChip(
                        "×${crash.occurrences}",
                        fill = DebugPalette.repeatChipFill,
                        borderColor = Color.Transparent,
                        tone = DebugPalette.text,
                    )
                }
                Text(
                    formatClock(crash.timestampMillis),
                    modifier = Modifier.padding(start = 8.dp),
                    style = InspectorType.meta,
                )
            }
            Text(
                crash.exceptionType,
                modifier = Modifier.padding(top = 6.dp),
                style = InspectorType.mono(13.sp, FontWeight.Medium, DebugPalette.text),
            )
            // The message is prose, so it is set in the body face and reads faster.
            Text(
                crash.message,
                modifier = Modifier.padding(top = 2.dp),
                style = InspectorType.prose(),
            )
            Text(
                crash.origin,
                modifier = Modifier.padding(top = 4.dp),
                style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
            )
        }
    }
}

@Composable
private fun CrashDetail(crash: CrashRecord, state: InspectorState) {
    val clipboard = LocalClipboardManager.current
    val tone = if (crash.fatal) DebugPalette.bad else DebugPalette.warn
    val appFrames = crash.frames.count { it.isAppFrame }
    val frameworkFrames = crash.frames.size - appFrames
    val shown = if (state.hideFrameworkFrames) crash.frames.filter { it.isAppFrame } else crash.frames

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (crash.fatal) "FATAL · app terminated" else "NON-FATAL · caught",
            style = InspectorType.mono(11.sp, FontWeight.Medium, tone, tracking = 0.08.em),
        )
        Text(
            crash.exceptionType,
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(14.sp, FontWeight.Medium, DebugPalette.text),
        )
        Text(
            crash.message,
            modifier = Modifier.padding(top = 6.dp),
            style = InspectorType.prose(),
        )
        Text(
            "${crash.threadName} thread · ${formatClock(crash.timestampMillis)} · ${crash.occurrences} occurrences",
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(11.sp, color = DebugPalette.textFaint, tabular = true),
        )

        // Actions at the top, where you reach after reading.
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlineAction("Copy trace") {
                clipboard.setText(AnnotatedString(crash.frames.joinToString("\n") { it.text }))
            }
            OutlineAction("Share", Glyph.Share) { }
            OutlineAction(
                if (state.hideFrameworkFrames) "Show framework frames" else "Hide framework frames",
            ) { state.hideFrameworkFrames = !state.hideFrameworkFrames }
        }

        // The root cause is usually the answer, so it sits above the trace in its own block.
        if (crash.causedBy != null) {
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth()
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, DebugPalette.bad, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    crash.causedBy,
                    style = InspectorType.mono(11.5.sp, color = DebugPalette.bad, lineHeight = 19.sp),
                )
            }
        }

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Stack trace")
            Text(
                "$appFrames app · $frameworkFrames framework",
                modifier = Modifier.padding(start = 8.dp),
                style = InspectorType.mono(10.sp, color = DebugPalette.textFaint, tabular = true),
            )
        }

        // Three signals — rule, tint, brightness — so actionable frames survive a greyscale shot.
        Column(Modifier.padding(top = 8.dp)) {
            shown.forEach { frame ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(start = if (frame.isAppFrame) 0.dp else 10.dp, top = 1.dp, bottom = 1.dp)
                        .background(if (frame.isAppFrame) DebugPalette.appFrameFill else Color.Transparent),
                ) {
                    Box(
                        Modifier
                            .width(if (frame.isAppFrame) 2.dp else 1.dp)
                            .fillMaxHeight()
                            .background(if (frame.isAppFrame) DebugPalette.accent else DebugPalette.line),
                    )
                    Text(
                        frame.text,
                        modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                        style = InspectorType.mono(
                            size = 11.5.sp,
                            color = if (frame.isAppFrame) DebugPalette.text else DebugPalette.textDim,
                            lineHeight = 18.4.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlineAction(label: String, glyph: Glyph? = null, onClick: () -> Unit) {
    HitTarget(onClick = onClick) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                InspectorIcon(glyph, null, size = 13.dp, tint = DebugPalette.accent)
            }
            Text(
                label,
                modifier = Modifier.padding(start = if (glyph != null) 6.dp else 0.dp),
                style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent),
                maxLines = 1,
            )
        }
    }
}
