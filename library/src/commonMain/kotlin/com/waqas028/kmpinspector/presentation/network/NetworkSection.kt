package com.waqas028.kmpinspector.presentation.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorShare
import kotlinx.coroutines.delay
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.JsonNode
import com.waqas028.kmpinspector.data.branchBody
import com.waqas028.kmpinspector.data.collapsedLabel
import com.waqas028.kmpinspector.data.withEmbeddedJson
import com.waqas028.kmpinspector.data.formatBytes
import com.waqas028.kmpinspector.data.formatClock
import com.waqas028.kmpinspector.data.formatDuration
import com.waqas028.kmpinspector.data.isBranch
import com.waqas028.kmpinspector.data.parseFormEncodedOrNull
import com.waqas028.kmpinspector.data.parseJsonOrNull
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.HttpOutcome
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.NetworkFilter
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.RequestDetailTab
import com.waqas028.kmpinspector.presentation.SortOrder
import com.waqas028.kmpinspector.presentation.flip
import com.waqas028.kmpinspector.presentation.common.ActionPill
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
 * Line breaks inside a URL only ever happen at whitespace, and a path has none — so a long
 * endpoint would break mid-word. Zero-width spaces after the separators give the layout somewhere
 * sensible to wrap, without changing what the text says or how wide it measures.
 */
private fun String.breakableAtSeparators(): String =
    if (length <= 28) this
    else replace("/", "/\u200B").replace("?", "?\u200B").replace("&", "&\u200B")

@Composable
internal fun NetworkSection(state: InspectorState, pane: PaneWidth) {
    val all = InspectorStore.requests
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.SwapVert,
            title = "No requests captured",
            sentence = "On Android, add the interceptor to your OkHttp client. Elsewhere, call Inspector.recordRequest from your HTTP client's hook.",
            snippet = "OkHttpClient.Builder()\n    .addInterceptor(KmpInspectorInterceptor())",
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

    // Most apps talk to one host, and repeating it on every row is what pushed the endpoint off
    // the screen in the first place. Whichever host is most common is left unsaid; anything else
    // is named, so a call to a second backend is never mistaken for the usual one.
    val usualHost = remember(all.size, all.firstOrNull()?.id) {
        all.groupingBy { it.host }.eachCount().maxByOrNull { it.value }?.key
    }

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
                                showHost = request.host != usualHost,
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
                    ActionPill("Clear", onClick = {
                        InspectorStore.clearRequests()
                        state.selectedRequestId = null
                    })
                    Spacer(Modifier.width(8.dp))
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
 * Three lines, six facts. One line cannot hold method, path, status, duration, size and time at
 * 380dp without truncating all of them — which is what used to happen to the path. So the endpoint
 * gets a line of its own at full width, with the badges and the clock above it and the timings
 * below.
 */
@Composable
private fun NetworkRow(
    request: NetworkRequest,
    showHost: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The endpoint gets a line to itself and the full width. Sharing line one with the badges
    // left it about twenty characters, which is why it used to arrive truncated to a stub.
    val endpoint = remember(request.url, showHost) {
        buildAnnotatedString {
            if (showHost) {
                withStyle(SpanStyle(color = DebugPalette.textFaint)) {
                    append(request.host.breakableAtSeparators())
                }
            }
            append(request.pathAndQuery.breakableAtSeparators())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            // Painted rather than laid out: the row's height now depends on how far the endpoint
            // wraps, so a sibling asking to fill that height has nothing to measure against.
            .drawBehind {
                if (selected) drawRect(DebugPalette.accent, size = Size(2.dp.toPx(), size.height))
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                .fillMaxWidth(),
        ) {
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
                // The clock moves up here, into space line one was wasting anyway.
                Box(Modifier.weight(1f))
                Text(
                    formatClock(request.timestampMillis),
                    style = InspectorType.meta,
                    maxLines = 1,
                )
            }

            Text(
                text = endpoint,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                style = InspectorType.mono(12.5.sp, color = DebugPalette.text, lineHeight = 18.sp),
                // Three lines is past any real endpoint; the detail pane has the whole URL anyway.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

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
                    // Fixed, so every row's duration bar gets the same track and their lengths
                    // stay comparable down the list.
                    modifier = Modifier.width(52.dp),
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
    val node = remember(request.id, tab) {
        body?.takeIf { it.isNotBlank() }?.let(::parseJsonOrNull)?.withEmbeddedJson()
    }
    // Only when it is not JSON: a form body and a JSON body are never the same text.
    val form = remember(request.id, tab, node) {
        if (node != null) null else body?.takeIf { it.isNotBlank() }?.let(::parseFormEncodedOrNull)
    }
    val collapsed = state.collapsedJsonPaths
    val rows = remember(node, collapsed) { node?.let { flattenJson(it, collapsed) } ?: emptyList() }
    // A form field often carries a whole JSON document as its value. Wrapping the pairs in an
    // object lets the same flattener expand those, so both bodies behave identically.
    val formRows = remember(form, collapsed) {
        form?.let { pairs ->
            flattenJson(
                root = JsonNode.Obj(pairs.map { (k, v) -> k to JsonNode.Str(v).withEmbeddedJson() }),
                collapsed = collapsed,
                rootPath = "form",
                includeRoot = false,
            )
        }
    }
    val curl = remember(request.id) { curlFor(request) }

    // A lazy list, not a scrolling column: a large JSON body has thousands of rows and composing
    // them all at once is what made opening a request feel slow.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item("summary") {
            DetailSummary(request) {
                clipboard.setText(AnnotatedString(curl))
                state.curlVisible = true
            }
        }

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
                CopyTab(pane) { clipboard.setText(AnnotatedString(copyPayload(request, tab))) }
                // Hidden where the platform has no share sheet, rather than shown and inert.
                if (InspectorShare.available) {
                    ShareRequest(request, tab, curl, pane)
                }
            }
        }

        when (tab) {
            RequestDetailTab.Headers -> headerItems(request)
            else -> bodyItems(body, request.contentType, bytes, node, rows, form, formRows, state, request.bodiesEvicted)
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
private fun DetailSummary(request: NetworkRequest, onCopyCurl: () -> Unit) {
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

        // Peer to the summary, not to a tab: a cURL command describes the whole call.
        HitTarget(onClick = onCopyCurl, minSize = 32.dp, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                "copy as cURL",
                style = InspectorType.mono(11.sp, FontWeight.Medium, DebugPalette.accent),
            )
        }

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

/**
 * Copies whatever the selected tab is showing: the headers, the request body or the response body.
 * It used to copy a cURL command from every tab, which meant the button did the same thing three
 * times and never what the tab in front of you said.
 *
 * Confirmation is the control itself changing for a moment, so nothing on this crowded row moves.
 */
@Composable
private fun CopyTab(pane: PaneWidth, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_400)
            copied = false
        }
    }
    val click = { onCopy(); copied = true }

    if (pane == PaneWidth.Compact) {
        HitTarget(onClick = click) {
            InspectorIcon(
                if (copied) Glyph.CheckCircle else Glyph.ContentCopy,
                if (copied) "Copied" else "Copy this tab",
                size = 18.dp,
                tint = DebugPalette.accent,
            )
        }
    } else {
        HitTarget(onClick = click) {
            Box(
                Modifier
                    .height(36.dp)
                    .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (copied) "Copied" else "Copy",
                    style = InspectorType.mono(12.sp, FontWeight.Medium, DebugPalette.accent),
                )
            }
        }
    }
}

/** What the copy button puts on the clipboard: exactly what the tab in front of you shows. */
internal fun copyPayload(request: NetworkRequest, tab: RequestDetailTab): String = when (tab) {
    RequestDetailTab.Headers -> headersText(request)
    RequestDetailTab.Request -> bodyPayload(request.requestBody)
    RequestDetailTab.Response -> bodyPayload(request.responseBody)
}

private fun bodyPayload(body: String?): String {
    val text = body?.takeIf { it.isNotBlank() } ?: return "No body"
    // A form body is copied the way it is displayed, one decoded pair per line.
    return parseFormEncodedOrNull(text)?.joinToString("\n") { (k, v) -> "$k: $v" } ?: text
}

/** The headers alone, in the order the tab lists them. Verbatim: the clipboard never redacts. */
internal fun headersText(request: NetworkRequest): String = buildString {
    if (request.requestHeaders.isNotEmpty()) {
        append("── Request headers\n")
        request.requestHeaders.forEach { append(it.name).append(": ").append(it.value).append('\n') }
    }
    if (request.responseHeaders.isNotEmpty()) {
        if (isNotEmpty()) append('\n')
        append("── Response headers\n")
        request.responseHeaders.forEach { append(it.name).append(": ").append(it.value).append('\n') }
    }
    if (isEmpty()) append("No headers")
}

/**
 * Share as cURL (re-runnable), Text (the whole exchange, readable in a chat) or Body (just the
 * payload of the tab you are on). The menu opens from one control so the tab row stays short.
 */
@Composable
private fun ShareRequest(request: NetworkRequest, tab: RequestDetailTab, curl: String, pane: PaneWidth) {
    var open by remember { mutableStateOf(false) }
    val subject = "${request.method} ${request.pathAndQuery}"
    Box(Modifier.padding(start = if (pane == PaneWidth.Compact) 0.dp else 8.dp)) {
        if (pane == PaneWidth.Compact) {
            HitTarget(onClick = { open = true }) {
                InspectorIcon(Glyph.Share, "Share", size = 18.dp, tint = DebugPalette.accent)
            }
        } else {
            HitTarget(onClick = { open = true }) {
                Box(
                    Modifier
                        .height(36.dp)
                        .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Share", style = InspectorType.mono(12.sp, FontWeight.Medium, DebugPalette.accent))
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(DebugPalette.surfaceRaised),
        ) {
            // Shares leave the device, so secrets are masked; the clipboard copy stays verbatim.
            ShareItem("cURL") { open = false; InspectorShare.share(curlFor(request, redact = true), subject) }
            ShareItem("Text") { open = false; InspectorShare.share(shareableText(request, redact = true), subject) }
            ShareItem("Body") {
                open = false
                // The tab you are looking at decides which body goes out.
                val body = when (tab) {
                    RequestDetailTab.Request -> request.requestBody ?: request.responseBody
                    else -> request.responseBody ?: request.requestBody
                }
                InspectorShare.share(body?.takeIf { it.isNotBlank() } ?: "No body", subject)
            }
        }
    }
}

@Composable
private fun ShareItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = InspectorType.mono(12.sp, color = DebugPalette.text)) },
        onClick = onClick,
    )
}

private fun HttpHeader.displayValue(redact: Boolean): String =
    if (redact && name.lowercase() in InspectorStore.redactedHeaders) "<redacted>" else value

/** The whole exchange as plain text, in the order the tabs show it: headers, request, response. */
internal fun shareableText(request: NetworkRequest, redact: Boolean = false): String = buildString {
    append(request.method).append(' ').append(request.url).append('\n')
    append(request.statusCode?.toString() ?: "ERR")
    if (request.reasonPhrase.isNotEmpty()) append(' ').append(request.reasonPhrase)
    append(" · ").append(formatDuration(request.durationMillis))
    append(" · ").append(request.protocol)
    append(" · ").append(formatClock(request.timestampMillis)).append('\n')
    append("↑ ").append(formatBytes(request.requestBytes))
    append("  ↓ ").append(formatBytes(request.responseBytes)).append('\n')
    request.errorText?.let { append("Error: ").append(it).append('\n') }

    fun section(title: String, body: () -> Unit) {
        append('\n').append("── ").append(title).append('\n')
        body()
    }
    if (request.requestHeaders.isNotEmpty()) {
        section("Request headers") {
            request.requestHeaders.forEach { append(it.name).append(": ").append(it.displayValue(redact)).append('\n') }
        }
    }
    request.requestBody?.takeIf { it.isNotBlank() }?.let { body ->
        section("Request body") { append(body).append('\n') }
    }
    if (request.responseHeaders.isNotEmpty()) {
        section("Response headers") {
            request.responseHeaders.forEach { append(it.name).append(": ").append(it.displayValue(redact)).append('\n') }
        }
    }
    request.responseBody?.takeIf { it.isNotBlank() }?.let { body ->
        section("Response body") { append(body).append('\n') }
    }
}

internal fun curlFor(request: NetworkRequest, redact: Boolean = false): String = buildString {
    append("curl -X ").append(request.method).append(" '").append(request.url).append("'")
    request.requestHeaders.forEach { append(" \\\n  -H '").append(it.name).append(": ").append(it.displayValue(redact)).append("'") }
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
    form: List<Pair<String, String>>?,
    formRows: List<JsonRow>?,
    state: InspectorState,
    evicted: Boolean,
) {
    if (body.isNullOrBlank()) {
        item("no-body") {
            Text(
                if (evicted) "Body released to stay inside the ${formatBytes(InspectorStore.bodyBudgetChars.toLong())} memory budget; newer requests keep theirs."
                else "No body",
                modifier = Modifier.padding(top = 16.dp),
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }
        return
    }

    item("body-meta") {
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append(contentType ?: "text/plain").append(" · ").append(formatBytes(bytes))
                    // Named explicitly, because a form body is usually posted under a
                    // Content-Type that claims to be something else.
                    if (form != null) append(" · form · ").append(form.size).append(" fields")
                },
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

    if (formRows != null) {
        // The same two-column table as the Headers tab: the question being asked of a form body
        // is which parameter carried which value, and one long line answers it badly. A field
        // whose value is itself a JSON document opens as a tree instead, indented on a panel so
        // it reads as belonging to the row above it.
        itemsIndexed(formRows, key = { _, row -> row.key }) { _, row ->
            val plain = row is JsonRow.Leaf && row.depth == 0
            if (plain) {
                val value = ((row as JsonRow.Leaf).node as? JsonNode.Str)?.value.orEmpty()
                KeyValueRow(row.label.orEmpty(), value.ifEmpty { "—" })
                Hairline(color = DebugPalette.lineFaint)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(DebugPalette.surface)
                        .padding(horizontal = 10.dp),
                ) {
                    JsonRowView(row, state)
                }
            }
        }
        return
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
    if (!node.isBranch()) return
    out += path
    when (val body = node.branchBody()) {
        is JsonNode.Obj -> body.entries.forEach { collectBranchPaths(it.second, "$path.${it.first}", out) }
        is JsonNode.Arr -> body.items.forEachIndexed { i, child -> collectBranchPaths(child, "$path[$i]", out) }
        else -> Unit
    }
}

/** One visible line of the JSON tree, pre-flattened so the lazy list can address it by index. */
internal sealed class JsonRow(val path: String, val depth: Int, val key: String) {
    class Branch(path: String, depth: Int, val label: String?, val node: JsonNode, val collapsed: Boolean) :
        JsonRow(path, depth, "b:$path")
    class Leaf(path: String, depth: Int, val label: String?, val node: JsonNode) : JsonRow(path, depth, "l:$path")
    class Close(path: String, depth: Int, val isObj: Boolean, val quoted: Boolean = false) :
        JsonRow(path, depth, "c:$path")
}

internal fun flattenJson(
    root: JsonNode,
    collapsed: Set<String>,
    rootPath: String = "$",
    /** False renders a branch root's children directly, without a wrapping `{ … }` pair. */
    includeRoot: Boolean = true,
): List<JsonRow> {
    val out = ArrayList<JsonRow>()
    fun children(node: JsonNode, path: String, depth: Int) {
        when (val body = node.branchBody()) {
            is JsonNode.Obj -> body.entries.forEach { (k, v) -> walkInto(out, collapsed, v, "$path.$k", depth, k) }
            is JsonNode.Arr -> body.items.forEachIndexed { i, v -> walkInto(out, collapsed, v, "$path[$i]", depth, null) }
            else -> Unit
        }
    }
    if (!includeRoot && root.isBranch()) children(root, rootPath, 0)
    else walkInto(out, collapsed, root, rootPath, 0, null)
    return out
}

private fun walkInto(
    out: MutableList<JsonRow>,
    collapsed: Set<String>,
    node: JsonNode,
    path: String,
    depth: Int,
    label: String?,
) {
    if (!node.isBranch()) {
        out += JsonRow.Leaf(path, depth, label, node)
        return
    }
    val isCollapsed = path in collapsed
    out += JsonRow.Branch(path, depth, label, node, isCollapsed)
    if (isCollapsed) return
    val body = node.branchBody()
    when (body) {
        is JsonNode.Obj -> body.entries.forEach { (k, v) -> walkInto(out, collapsed, v, "$path.$k", depth + 1, k) }
        is JsonNode.Arr -> body.items.forEachIndexed { i, v -> walkInto(out, collapsed, v, "$path[$i]", depth + 1, null) }
        else -> Unit
    }
    out += JsonRow.Close(path, depth, body is JsonNode.Obj, quoted = node is JsonNode.Embedded)
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
                if (row.collapsed) {
                    row.node.collapsedLabel()
                } else {
                    val quote = if (row.node is JsonNode.Embedded) "\"" else ""
                    quote + if (row.node.branchBody() is JsonNode.Obj) "{" else "["
                },
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        }

        is JsonRow.Close -> Row(Modifier.fillMaxWidth().height(24.dp).padding(start = indent)) {
            Text(
                (if (row.isObj) "}" else "]") + if (row.quoted) "\"" else "",
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
