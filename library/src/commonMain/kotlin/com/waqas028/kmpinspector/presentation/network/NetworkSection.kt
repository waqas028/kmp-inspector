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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
                                    state.collapsedJsonPaths = emptySet()
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
    val tab = state.requestDetailTab
    val body = when (tab) {
        RequestDetailTab.Request -> request.requestBody
        RequestDetailTab.Response -> request.responseBody
        RequestDetailTab.Headers -> null
    }
    val bytes = if (tab == RequestDetailTab.Request) request.requestBytes else request.responseBytes

    // Parsing a body can take tens of milliseconds for a large response; it happens once per
    // request and tab, never per recomposition. The flattened rows are keyed on the collapsed set
    // so toggling one branch rebuilds the row list but not the tree.
    val node = remember(request.id, tab) { body?.takeIf { it.isNotBlank() }?.let(::parseJsonOrNull) }
    val collapsed = state.collapsedJsonPaths
    val rows = remember(node, collapsed) { node?.let { flattenJson(it, collapsed) } ?: emptyList() }
    val curl = remember(request.id) { curlFor(request) }

    // A lazy list, not a scrolling column: a large JSON body has thousands of rows and composing
    // them all at once is what made opening a request feel slow.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item("summary") { DetailSummary(request) }

        item("tabs") {
            Row(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RequestDetailTab.entries.forEach { t ->
                    val selected = tab == t
                    Box(
                        Modifier
                            .height(48.dp)
                            .clickable { state.requestDetailTab = t }
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                t.label,
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
                CopyAsCurl(curl, pane) {
                    clipboard.setText(AnnotatedString(it))
                    state.curlVisible = true
                }
            }
        }

        when (tab) {
            RequestDetailTab.Headers -> headerItems(request)
            else -> bodyItems(body, request.contentType, bytes, node, rows, state)
        }

        // Reveal the exact command, so the developer can see what went to the clipboard.
        if (state.curlVisible) {
            item("curl") {
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth()
                        .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                        .border(1.dp, DebugPalette.line, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                ) {
                    Text(curl, style = InspectorType.mono(11.sp, color = DebugPalette.textDim, lineHeight = 18.sp))
                }
            }
        }
    }
}

@Composable
private fun DetailSummary(request: NetworkRequest) {
    Column(Modifier.fillMaxWidth()) {
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
    }
}

@Composable
private fun CopyAsCurl(command: String, pane: PaneWidth, onCopy: (String) -> Unit) {
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

/** Plain text bodies are shown up to this many characters; one enormous Text node lays out slowly. */
private const val RAW_BODY_LIMIT = 64 * 1024

private fun LazyListScope.bodyItems(
    body: String?,
    contentType: String?,
    bytes: Long,
    node: JsonNode?,
    rows: List<JsonRow>,
    state: InspectorState,
) {
    if (body.isNullOrBlank()) {
        item("no-body") {
            Text(
                "No body",
                modifier = Modifier.padding(top = 16.dp),
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }
        return
    }

    item("body-meta") {
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${contentType ?: "text/plain"} · ${formatBytes(bytes)}",
                modifier = Modifier.weight(1f),
                style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint, tabular = true),
            )
            if (node != null) {
                HitTarget(onClick = { state.collapsedJsonPaths = emptySet() }) {
                    Text("expand all", style = InspectorType.mono(11.sp, color = DebugPalette.accent))
                }
                HitTarget(
                    onClick = {
                        state.collapsedJsonPaths = buildSet { collectBranchPaths(node, "$", this) }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text("collapse all", style = InspectorType.mono(11.sp, color = DebugPalette.accent))
                }
            }
        }
    }

    if (node == null) {
        item("raw") {
            val shown = if (body.length > RAW_BODY_LIMIT) body.substring(0, RAW_BODY_LIMIT) else body
            Column(
                Modifier.padding(top = 8.dp).fillMaxWidth()
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .padding(10.dp),
            ) {
                Text(shown, style = InspectorType.code)
                if (shown.length < body.length) {
                    Text(
                        "… showing the first ${formatBytes(RAW_BODY_LIMIT.toLong())} of ${formatBytes(body.length.toLong())}",
                        modifier = Modifier.padding(top = 8.dp),
                        style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
                    )
                }
            }
        }
        return
    }

    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        val shape = when {
            rows.size == 1 -> RoundedCornerShape(4.dp)
            index == 0 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            index == rows.lastIndex -> RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            else -> RoundedCornerShape(0.dp)
        }
        Box(
            Modifier
                .then(if (index == 0) Modifier.padding(top = 8.dp) else Modifier)
                .fillMaxWidth()
                .background(DebugPalette.surface, shape)
                .padding(horizontal = 10.dp),
        ) {
            JsonRowView(row, state)
        }
    }
}

private fun LazyListScope.headerItems(request: NetworkRequest) {
    if (request.requestHeaders.isNotEmpty()) {
        item("req-kicker") { Kicker("Request", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        itemsIndexed(request.requestHeaders, key = { i, h -> "req-$i-${h.name}" }) { _, h ->
            KeyValueRow(h.name, h.value)
            Hairline(color = DebugPalette.lineFaint)
        }
    }
    if (request.responseHeaders.isNotEmpty()) {
        item("res-kicker") { Kicker("Response", Modifier.padding(top = 16.dp, bottom = 4.dp)) }
        itemsIndexed(request.responseHeaders, key = { i, h -> "res-$i-${h.name}" }) { _, h ->
            KeyValueRow(h.name, h.value)
            Hairline(color = DebugPalette.lineFaint)
        }
    }
}

private fun collectBranchPaths(node: JsonNode, path: String, out: MutableSet<String>) {
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

/** One visible line of the JSON tree, pre-flattened so the lazy list can address it by index. */
private sealed class JsonRow(val path: String, val depth: Int, val key: String) {
    class Branch(path: String, depth: Int, val label: String?, val node: JsonNode, val collapsed: Boolean) :
        JsonRow(path, depth, "b:$path")
    class Leaf(path: String, depth: Int, val label: String?, val node: JsonNode) : JsonRow(path, depth, "l:$path")
    class Close(path: String, depth: Int, val isObj: Boolean) : JsonRow(path, depth, "c:$path")
}

private fun flattenJson(root: JsonNode, collapsed: Set<String>): List<JsonRow> {
    val out = ArrayList<JsonRow>()
    fun walk(node: JsonNode, path: String, depth: Int, label: String?) {
        if (node.isBranch()) {
            val isCollapsed = path in collapsed
            out += JsonRow.Branch(path, depth, label, node, isCollapsed)
            if (isCollapsed) return
            when (node) {
                is JsonNode.Obj -> node.entries.forEach { (k, v) -> walk(v, "$path.$k", depth + 1, k) }
                is JsonNode.Arr -> node.items.forEachIndexed { i, v -> walk(v, "$path[$i]", depth + 1, null) }
                else -> Unit
            }
            out += JsonRow.Close(path, depth, node is JsonNode.Obj)
        } else {
            out += JsonRow.Leaf(path, depth, label, node)
        }
    }
    walk(root, "$", 0, null)
    return out
}

/**
 * Rows: 36dp for a collapsible branch (whole row is the target), 24dp for a leaf, 14dp indent per
 * level. Long values wrap; nothing scrolls sideways.
 */
@Composable
private fun JsonRowView(row: JsonRow, state: InspectorState) {
    val indent = (row.depth * 14).dp
    when (row) {
        is JsonRow.Branch -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable {
                    state.collapsedJsonPaths =
                        if (row.collapsed) state.collapsedJsonPaths - row.path
                        else state.collapsedJsonPaths + row.path
                }
                .padding(start = indent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (row.collapsed) "▸" else "▾",
                modifier = Modifier.width(14.dp),
                style = InspectorType.mono(10.sp, color = DebugPalette.textFaint),
            )
            if (row.label != null) {
                Text("${row.label}: ", style = InspectorType.mono(12.sp, color = DebugPalette.accent))
            }
            Text(
                if (row.collapsed) row.node.collapsedLabel() else if (row.node is JsonNode.Obj) "{" else "[",
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }

        is JsonRow.Close -> Row(Modifier.fillMaxWidth().height(24.dp).padding(start = indent)) {
            Text(
                if (row.isObj) "}" else "]",
                modifier = Modifier.padding(start = 14.dp),
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }

        is JsonRow.Leaf -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = indent + 14.dp, top = 3.dp, bottom = 3.dp),
        ) {
            if (row.label != null) {
                Text("${row.label}: ", style = InspectorType.mono(12.sp, color = DebugPalette.accent))
            }
            when (val node = row.node) {
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
