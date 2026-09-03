package com.waqas028.kmpinspector.presentation.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.JsonNode
import com.waqas028.kmpinspector.data.collapsedLabel
import com.waqas028.kmpinspector.data.formatBytes
import com.waqas028.kmpinspector.data.formatClock
import com.waqas028.kmpinspector.data.formatDuration
import com.waqas028.kmpinspector.data.isBranch
import com.waqas028.kmpinspector.data.parseJsonOrNull
import com.waqas028.kmpinspector.domain.model.HttpOutcome
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.NetworkFilter
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.RequestDetailTab
import com.waqas028.kmpinspector.presentation.SortOrder
import com.waqas028.kmpinspector.presentation.flip
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.FilterPill
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.HitTarget
import com.waqas028.kmpinspector.presentation.common.KeyValueRow
import com.waqas028.kmpinspector.presentation.common.Kicker
import com.waqas028.kmpinspector.presentation.common.NoResults
import com.waqas028.kmpinspector.presentation.common.ScrollToTop
import com.waqas028.kmpinspector.presentation.common.SortToggle
import com.waqas028.kmpinspector.presentation.common.StatusLine
import com.waqas028.kmpinspector.presentation.common.StatusMark
import com.waqas028.kmpinspector.presentation.pathBudget
import com.waqas028.kmpinspector.presentation.shell.MasterDetail
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType
import kotlin.math.min

internal fun HttpOutcome.tone(): Color = when (this) {
    HttpOutcome.Success -> DebugPalette.ok
    HttpOutcome.Redirect -> DebugPalette.neutralState
    HttpOutcome.ClientError -> DebugPalette.warn
    HttpOutcome.ServerError, HttpOutcome.TransportError -> DebugPalette.bad
}

/** Glyph first, so the state reads in greyscale and to a colour-blind eye. */
internal fun HttpOutcome.glyph(): String = when (this) {
    HttpOutcome.Success -> "✓"
    HttpOutcome.Redirect -> "↻"
    HttpOutcome.ClientError -> "!"
    HttpOutcome.ServerError, HttpOutcome.TransportError -> "✕"
}

/**
 * Head-truncate: the tail is the part that differs between requests, so the ellipsis goes in front.
 */
internal fun String.headTruncate(budget: Int): String =
    if (length <= budget) this else "…" + takeLast(budget)

@Composable
internal fun NetworkSection(state: InspectorState, pane: PaneWidth) {
    val all = InspectorStore.requests
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.SwapVert,
            title = "No requests captured",
            sentence = "Install the inspector plugin on your HTTP client and requests will appear here as they run.",
            snippet = "HttpClient { install(InspectorPlugin) }",
        )
        return
    }

    // The store keeps requests newest first; oldest first is a reversal of the same list.
    val filtered = all.filter { r ->
        val matchesFilter = when (state.networkFilter) {
            NetworkFilter.All -> true
            NetworkFilter.Errors -> r.outcome == HttpOutcome.ClientError ||
                r.outcome == HttpOutcome.ServerError || r.outcome == HttpOutcome.TransportError
            NetworkFilter.Slow -> r.durationMillis >= 800
            NetworkFilter.Writes -> r.isWrite
        }
        val q = state.query.trim()
        matchesFilter && (q.isEmpty() || r.url.contains(q, true) || r.method.contains(q, true))
    }.let { if (state.networkSort == SortOrder.OldestFirst) it.asReversed() else it }

    val selected = filtered.firstOrNull { it.id == state.selectedRequestId }
        ?: all.firstOrNull { it.id == state.selectedRequestId }

    MasterDetail(
        pane = pane,
        hasSelection = selected != null,
        onBack = { state.selectedRequestId = null },
        placeholder = "Select a request",
        list = {
            Column(Modifier.fillMaxSize()) {
                NetworkFilterChips(state, all)
                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NoResults(
                            message = "No requests match ${state.networkFilter.label.lowercase()}",
                            actionLabel = "Show all",
                            onAction = {
                                state.networkFilter = NetworkFilter.All
                                state.query = ""
                            },
                        )
                    }
                } else {
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { request ->
                            NetworkRow(
                                request = request,
                                budget = pane.pathBudget,
                                selected = request.id == state.selectedRequestId,
                                onClick = {
                                    state.selectedRequestId = request.id
                                    state.curlVisible = false
                                },
                            )
                            Hairline(color = DebugPalette.lineFaint)
                        }
                    }
                    ScrollToTop(listState)
                }
                }
                Hairline()
                // The status line never scrolls and has room on the right, so the sort toggle
                // lives here rather than crowding the filter chips.
                StatusLine(
                    text = "${filtered.size} of ${all.size} requests · capture buffer ${InspectorStore.NETWORK_CAPACITY}",
                ) {
                    SortToggle(state.networkSort, onToggle = { state.networkSort = state.networkSort.flip() })
                }
            }
        },
        detail = { selected?.let { NetworkDetail(it, state, pane) } },
    )
}

/** Errors-only is the most-used view in any network log: one tap, always visible, count on the chip. */
@Composable
private fun NetworkFilterChips(state: InspectorState, all: List<NetworkRequest>) {
    val errors = all.count {
        it.outcome == HttpOutcome.ClientError || it.outcome == HttpOutcome.ServerError ||
            it.outcome == HttpOutcome.TransportError
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NetworkFilter.entries.forEach { filter ->
            val label = when (filter) {
                NetworkFilter.All -> "All · ${all.size}"
                NetworkFilter.Errors -> "Errors · $errors"
                else -> filter.label
            }
            FilterPill(label, state.networkFilter == filter, { state.networkFilter = filter })
        }
    }
}

/**
 * Two lines, six facts. One line cannot hold method, path, status, duration, size and time at 380dp
 * without truncating all of them: line one identifies the call, line two measures it.
 */
@Composable
private fun NetworkRow(
    request: NetworkRequest,
    budget: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 2dp accent bar on the leading edge marks selection alongside the tint.
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .background(if (selected) DebugPalette.accent else Color.Transparent),
        )
        Column(Modifier.padding(start = 14.dp, end = 16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusMark(request.outcome.glyph(), request.outcome.tone())
                Text(
                    text = request.statusCode?.toString() ?: "ERR",
                    modifier = Modifier.padding(start = 8.dp),
                    style = InspectorType.mono(
                        12.5.sp, FontWeight.Medium, request.outcome.tone(), tabular = true,
                    ),
                )
                Text(
                    text = request.method,
                    modifier = Modifier.padding(start = 8.dp),
                    style = InspectorType.mono(
                        11.sp, FontWeight.Medium, DebugPalette.textDim, tracking = 0.04.em,
                    ),
                )
                Text(
                    text = request.pathAndQuery.headTruncate(budget),
                    modifier = Modifier.padding(start = 8.dp),
                    style = InspectorType.mono(12.5.sp, color = DebugPalette.text),
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDuration(request.durationMillis),
                    modifier = Modifier.width(52.dp),
                    style = InspectorType.meta,
                )
                DurationBar(request.durationMillis, Modifier.weight(1f).padding(end = 10.dp))
                Text(
                    if (request.statusCode == null) "—" else formatBytes(request.responseBytes),
                    modifier = Modifier.width(52.dp),
                    style = InspectorType.meta,
                    maxLines = 1,
                )
                Text(
                    formatClock(request.timestampMillis),
                    modifier = Modifier.width(88.dp),
                    style = InspectorType.meta,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `min(ms/1500, 1)` of the width; warn above 800ms. */
@Composable
private fun DurationBar(millis: Long, modifier: Modifier = Modifier) {
    val fraction = min(millis.toFloat() / 1500f, 1f).coerceAtLeast(0.02f)
    Box(modifier.height(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(if (millis > 800) DebugPalette.warn else DebugPalette.text.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun NetworkDetail(request: NetworkRequest, state: InspectorState, pane: PaneWidth) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                request.method,
                style = InspectorType.mono(13.sp, FontWeight.Medium, DebugPalette.textDim, tracking = 0.04.em),
            )
            StatusMark(
                request.outcome.glyph(),
                request.outcome.tone(),
                Modifier.padding(start = 8.dp),
            )
            Text(
                "${request.statusCode ?: "ERR"} ${request.reasonPhrase}".trim(),
                modifier = Modifier.padding(start = 8.dp),
                style = InspectorType.mono(13.sp, FontWeight.Medium, request.outcome.tone(), tabular = true),
            )
        }

        // The URL wraps; nothing in this pane scrolls sideways.
        Text(
            request.url,
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(12.sp, color = DebugPalette.text, lineHeight = 19.sp),
        )

        Text(
            buildString {
                append("↑ ").append(formatBytes(request.requestBytes))
                append("  ↓ ").append(formatBytes(request.responseBytes))
                append("  ·  ").append(formatDuration(request.durationMillis))
                append("  ·  ").append(request.protocol)
                append("  ·  ").append(formatClock(request.timestampMillis))
            },
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(11.sp, color = DebugPalette.textDim, tabular = true),
        )

        if (request.errorText != null) {
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth()
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, DebugPalette.bad, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(request.errorText, style = InspectorType.mono(11.5.sp, color = DebugPalette.bad))
            }
        }

        // Copy as cURL is peer to the tabs, not buried in a menu.
        Row(
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RequestDetailTab.entries.forEach { tab ->
                val selected = state.requestDetailTab == tab
                Box(
                    Modifier
                        .height(48.dp)
                        .clickable { state.requestDetailTab = tab }
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tab.label,
                            style = InspectorType.mono(
                                12.5.sp,
                                color = if (selected) DebugPalette.accent else DebugPalette.textDim,
                            ),
                        )
                        Box(
                            Modifier.padding(top = 6.dp).height(2.dp)
                                .width(if (selected) 40.dp else 0.dp)
                                .background(DebugPalette.accent),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f))
            CopyAsCurl(request, pane) {
                clipboard.setText(AnnotatedString(it))
                state.curlVisible = true
            }
        }

        when (state.requestDetailTab) {
            RequestDetailTab.Request -> BodyPane(request.requestBody, request.contentType, request.requestBytes, state)
            RequestDetailTab.Response -> BodyPane(request.responseBody, request.contentType, request.responseBytes, state)
            RequestDetailTab.Headers -> HeadersPane(request)
        }

        // Reveal the exact command, so the developer can see what went to the clipboard.
        if (state.curlVisible) {
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth()
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, DebugPalette.line, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    curlFor(request),
                    style = InspectorType.mono(11.sp, color = DebugPalette.textDim, lineHeight = 18.sp),
                )
            }
        }
    }
}

@Composable
private fun CopyAsCurl(request: NetworkRequest, pane: PaneWidth, onCopy: (String) -> Unit) {
    val command = curlFor(request)
    if (pane == PaneWidth.Compact) {
        HitTarget(onClick = { onCopy(command) }) {
            InspectorIcon(Glyph.ContentCopy, "Copy as cURL", size = 18.dp, tint = DebugPalette.accent)
        }
    } else {
        HitTarget(onClick = { onCopy(command) }) {
            Box(
                Modifier
                    .height(36.dp)
                    .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Copy as cURL",
                    style = InspectorType.mono(12.sp, FontWeight.Medium, DebugPalette.accent),
                )
            }
        }
    }
}

private fun curlFor(request: NetworkRequest): String = buildString {
    append("curl -X ").append(request.method).append(" '").append(request.url).append("'")
    request.requestHeaders.forEach { append(" \\\n  -H '").append(it.name).append(": ").append(it.value).append("'") }
    request.requestBody?.let { append(" \\\n  --data '").append(it).append("'") }
}

@Composable
private fun BodyPane(body: String?, contentType: String?, bytes: Long, state: InspectorState) {
    if (body.isNullOrBlank()) {
        Text(
            "No body",
            modifier = Modifier.padding(top = 16.dp),
            style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
        )
        return
    }
    val node = parseJsonOrNull(body)
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${contentType ?: "text/plain"} · ${formatBytes(bytes)}",
                modifier = Modifier.weight(1f),
                style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint, tabular = true),
            )
            if (node != null) {
                HitTarget(onClick = { state.collapsedJsonPaths.clear() }) {
                    Text("expand all", style = InspectorType.mono(11.sp, color = DebugPalette.accent))
                }
                HitTarget(
                    onClick = {
                        state.collapsedJsonPaths.clear()
                        collectBranchPaths(node, "$", state.collapsedJsonPaths)
                    },
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text("collapse all", style = InspectorType.mono(11.sp, color = DebugPalette.accent))
                }
            }
        }
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth()
                .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                .padding(10.dp),
        ) {
            if (node == null) {
                Text(body, style = InspectorType.code)
            } else {
                Column { JsonTree(node, "$", 0, null, state) }
            }
        }
    }
}

private fun collectBranchPaths(node: JsonNode, path: String, out: MutableList<String>) {
    when (node) {
        is JsonNode.Obj -> {
            out += path
            node.entries.forEach { collectBranchPaths(it.second, "$path.${it.first}", out) }
        }
        is JsonNode.Arr -> {
            out += path
            node.items.forEachIndexed { i, child -> collectBranchPaths(child, "$path[$i]", out) }
        }
        else -> Unit
    }
}

/**
 * Rows: 36dp for a collapsible branch (whole row is the target), 24dp for a leaf, 14dp indent per
 * level. Long values wrap; nothing scrolls sideways.
 */
@Composable
private fun JsonTree(
    node: JsonNode,
    path: String,
    depth: Int,
    label: String?,
    state: InspectorState,
) {
    val indent = (depth * 14).dp
    val collapsed = path in state.collapsedJsonPaths

    if (node.isBranch()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable {
                    if (collapsed) state.collapsedJsonPaths.remove(path)
                    else state.collapsedJsonPaths.add(path)
                }
                .padding(start = indent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (collapsed) "▸" else "▾",
                modifier = Modifier.width(14.dp),
                style = InspectorType.mono(10.sp, color = DebugPalette.textFaint),
            )
            if (label != null) {
                Text(
                    "$label: ",
                    style = InspectorType.mono(12.sp, color = DebugPalette.accent),
                )
            }
            Text(
                if (collapsed) node.collapsedLabel() else if (node is JsonNode.Obj) "{" else "[",
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }
        if (!collapsed) {
            when (node) {
                is JsonNode.Obj -> node.entries.forEach { (k, v) ->
                    JsonTree(v, "$path.$k", depth + 1, k, state)
                }
                is JsonNode.Arr -> node.items.forEachIndexed { i, v ->
                    JsonTree(v, "$path[$i]", depth + 1, null, state)
                }
                else -> Unit
            }
            Row(Modifier.fillMaxWidth().height(24.dp).padding(start = indent)) {
                Text(
                    if (node is JsonNode.Obj) "}" else "]",
                    modifier = Modifier.padding(start = 14.dp),
                    style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = indent + 14.dp, top = 3.dp, bottom = 3.dp),
        ) {
            if (label != null) {
                Text("$label: ", style = InspectorType.mono(12.sp, color = DebugPalette.accent))
            }
            when (node) {
                is JsonNode.Str -> Text(
                    "\"${node.value}\"",
                    style = InspectorType.mono(12.sp, color = DebugPalette.ok, lineHeight = 20.sp),
                )
                is JsonNode.Num -> Text(node.raw, style = InspectorType.mono(12.sp, color = DebugPalette.accent, tabular = true))
                is JsonNode.Bool -> Text(node.value.toString(), style = InspectorType.mono(12.sp, color = DebugPalette.accent))
                JsonNode.Null -> Text(
                    "null",
                    style = InspectorType.mono(12.sp, color = DebugPalette.textFaint)
                        .copy(fontStyle = FontStyle.Italic),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun HeadersPane(request: NetworkRequest) {
    Column(Modifier.padding(top = 12.dp)) {
        if (request.requestHeaders.isNotEmpty()) {
            Kicker("Request", Modifier.padding(bottom = 4.dp))
            request.requestHeaders.forEach {
                KeyValueRow(it.name, it.value)
                Hairline(color = DebugPalette.lineFaint)
            }
        }
        if (request.responseHeaders.isNotEmpty()) {
            Kicker("Response", Modifier.padding(top = 16.dp, bottom = 4.dp))
            request.responseHeaders.forEach {
                KeyValueRow(it.name, it.value)
                Hairline(color = DebugPalette.lineFaint)
            }
        }
    }
}
