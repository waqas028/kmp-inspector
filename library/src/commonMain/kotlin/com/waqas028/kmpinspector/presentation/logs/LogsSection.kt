package com.waqas028.kmpinspector.presentation.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorPlatform
import com.waqas028.kmpinspector.data.InspectorShare
import com.waqas028.kmpinspector.data.logsToText
import com.waqas028.kmpinspector.presentation.common.ActionPill
import androidx.compose.foundation.layout.Spacer
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.formatClock
import com.waqas028.kmpinspector.domain.model.LogLevel
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.SortOrder
import com.waqas028.kmpinspector.presentation.flip
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.NoResults
import com.waqas028.kmpinspector.presentation.common.HitTarget
import com.waqas028.kmpinspector.presentation.common.ScrollToTop
import com.waqas028.kmpinspector.presentation.common.SortToggle
import com.waqas028.kmpinspector.presentation.common.StatusLine
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

private fun LogLevel.tone(): Color = when (this) {
    LogLevel.Error -> DebugPalette.bad
    LogLevel.Warn -> DebugPalette.warn
    LogLevel.Info -> DebugPalette.text
    LogLevel.Debug, LogLevel.Verbose -> DebugPalette.textDim
}

/** Single full-width pane, newest line on top by default. Logs has no detail view. */
@Composable
internal fun LogsSection(state: InspectorState) {
    val all = InspectorStore.logs
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.Subject,
            title = "Nothing logged yet",
            sentence = "Route your logging through InspectorLog and lines will stream here as they are written.",
            snippet = "InspectorLog.i(\"CartStore\", message)",
        )
        return
    }

    val tags = remember(all.size) { all.map { it.tag }.distinct().sorted() }
    val q = state.query.trim()
    val filtered = all.filter { line ->
        (state.logLevel == null || line.level == state.logLevel) &&
            (state.logTag == null || line.tag == state.logTag) &&
            (q.isEmpty() || line.message.contains(q, true) || line.tag.contains(q, true))
    }.let { if (state.logSort == SortOrder.NewestFirst) it.asReversed() else it }

    val listState = rememberLazyListState()
    // Tailing follows the live end of the list, which is the top when newest is first. Search
    // filters but does not stop tailing.
    LaunchedEffect(filtered.size, state.tailing, state.logSort) {
        if (state.tailing && filtered.isNotEmpty()) {
            listState.scrollToItem(if (state.logSort == SortOrder.NewestFirst) 0 else filtered.lastIndex)
        }
    }
    // A finger on the list means the reader wants to read, so tailing pauses instead of yanking
    // the list back to the live end on the next batch. Tapping Tailing resumes and re-snaps.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && state.tailing) {
                state.tailing = false
                state.pausedAt = formatClock(InspectorPlatform.currentTimeMillis())
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filters scroll; the tailing toggle is pinned outside so it stays reachable at 380dp.
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "LEVEL",
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = InspectorType.kicker,
                )
                LevelPicker(state)
                TagFilter(state, tags)
            }
            TailingToggle(state)
        }
        Hairline()

        if (filtered.isEmpty()) {
            val q = state.query.trim()
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NoResults(
                    message = buildString {
                        append("No ").append(state.logLevel?.name ?: "matching").append(" logs")
                        state.logTag?.let { append(" tagged ").append(it) }
                        if (q.isNotEmpty()) append(" matching \"").append(q).append("\"")
                    },
                    actionLabel = "Reset filters",
                    onAction = {
                        state.logLevel = null
                        state.logTag = null
                        state.query = ""
                    },
                )
            }
        } else {
        Box(Modifier.weight(1f)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { line ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .border(1.dp, line.level.tone(), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                line.level.letter.toString(),
                                style = InspectorType.mono(10.sp, FontWeight.Medium, line.level.tone()),
                            )
                        }
                        Text(
                            formatClock(line.timestampMillis),
                            modifier = Modifier.padding(start = 8.dp),
                            style = InspectorType.meta,
                        )
                        Text(
                            line.tag,
                            modifier = Modifier.padding(start = 8.dp),
                            style = InspectorType.mono(
                                10.5.sp, FontWeight.Medium, line.level.tone(), tracking = 0.04.em,
                            ),
                        )
                    }
                    Text(
                        line.message,
                        modifier = Modifier.padding(top = 4.dp),
                        style = InspectorType.mono(
                            size = 12.sp,
                            color = if (line.level == LogLevel.Verbose) DebugPalette.textDim else DebugPalette.text,
                            lineHeight = 19.8.sp,
                        ),
                    )
                }
                Hairline(color = DebugPalette.lineFaint)
            }
        }
        // Pausing on scroll would fight the user; the pill just offers the way back.
        ScrollToTop(listState)
        }
        }

        Hairline()
        StatusLine(
            text = buildString {
                append("${filtered.size} of ${all.size} lines · ring buffer ${InspectorStore.LOG_CAPACITY}")
                append(if (state.tailing) " · live" else " · paused at ${state.pausedAt ?: "—"}")
            },
        ) {
            if (InspectorShare.available) {
                ActionPill("Share", onClick = {
                    InspectorShare.share(logsToText(filtered), subject = "${InspectorStore.appId} logs")
                })
                Spacer(Modifier.width(8.dp))
            }
            ActionPill("Clear", onClick = { InspectorStore.clearLogs() })
            Spacer(Modifier.width(8.dp))
            SortToggle(state.logSort, onToggle = { state.logSort = state.logSort.flip() })
        }
    }
}

/**
 * One level at a time, or All. Letters — not colours — name the level, so it reads in greyscale.
 *
 * Tapping the active letter again clears back to All: without that there would be no way to see
 * every level once a filter had been applied.
 */
@Composable
private fun LevelPicker(state: InspectorState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HitTarget(onClick = { state.logLevel = null }, minSize = 40.dp) {
            LevelChip(label = "All", selected = state.logLevel == null, wide = true)
        }
        LogLevel.entries.forEach { level ->
            HitTarget(
                onClick = { state.logLevel = if (state.logLevel == level) null else level },
                minSize = 40.dp,
            ) {
                LevelChip(
                    label = level.letter.toString(),
                    selected = state.logLevel == level,
                    // Everything unselected is excluded, so it dims — the exclusion stays visible.
                    dimmed = state.logLevel != null && state.logLevel != level,
                )
            }
        }
    }
}

@Composable
private fun LevelChip(
    label: String,
    selected: Boolean,
    dimmed: Boolean = false,
    wide: Boolean = false,
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .then(if (wide) Modifier else Modifier.width(32.dp))
            .background(
                if (selected) DebugPalette.activePillFill else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .border(
                1.dp,
                if (selected) DebugPalette.accent else DebugPalette.line,
                RoundedCornerShape(4.dp),
            )
            .then(if (wide) Modifier.padding(horizontal = 10.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = InspectorType.mono(
                12.sp,
                FontWeight.Medium,
                when {
                    selected -> DebugPalette.accent
                    dimmed -> DebugPalette.textFaint
                    else -> DebugPalette.text
                },
            ),
            maxLines = 1,
        )
    }
}

/** A dropdown, because tag lists grow past what chips can hold. */
@Composable
private fun TagFilter(state: InspectorState, tags: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HitTarget(onClick = { expanded = true }) {
            Box(
                Modifier
                    .height(32.dp)
                    .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.logTag ?: "All tags",
                    style = InspectorType.mono(11.5.sp, color = DebugPalette.textDim),
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DebugPalette.surfaceRaised),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "All tags",
                        style = InspectorType.mono(
                            12.sp,
                            color = if (state.logTag == null) DebugPalette.accent else DebugPalette.text,
                        ),
                    )
                },
                onClick = { state.logTag = null; expanded = false },
            )
            tags.forEach { tag ->
                DropdownMenuItem(
                    text = {
                        Text(
                            tag,
                            style = InspectorType.mono(
                                12.sp,
                                color = if (state.logTag == tag) DebugPalette.accent else DebugPalette.text,
                            ),
                        )
                    },
                    onClick = { state.logTag = tag; expanded = false },
                )
            }
        }
    }
}

/** A word, not an ambiguous icon: live tailing is a primary feature. */
@Composable
private fun TailingToggle(state: InspectorState) {
    HitTarget(
        onClick = {
            state.tailing = !state.tailing
            state.pausedAt = if (state.tailing) null else formatClock(
                com.waqas028.kmpinspector.data.InspectorPlatform.currentTimeMillis(),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .background(
                    if (state.tailing) DebugPalette.activePillFill else Color.Transparent,
                    RoundedCornerShape(16.dp),
                )
                .border(
                    1.dp,
                    if (state.tailing) DebugPalette.accent else DebugPalette.lineStrong,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InspectorIcon(
                if (state.tailing) Glyph.Pause else Glyph.PlayArrow,
                null,
                size = 12.dp,
                tint = if (state.tailing) DebugPalette.accent else DebugPalette.textDim,
            )
            Text(
                if (state.tailing) "Tailing" else "Paused",
                modifier = Modifier.padding(start = 6.dp),
                style = InspectorType.mono(
                    11.5.sp,
                    FontWeight.Medium,
                    if (state.tailing) DebugPalette.accent else DebugPalette.textDim,
                ),
                maxLines = 1,
            )
        }
    }
}
