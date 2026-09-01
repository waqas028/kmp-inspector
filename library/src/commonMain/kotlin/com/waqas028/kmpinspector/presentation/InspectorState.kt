package com.waqas028.kmpinspector.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.LogLevel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

internal enum class InspectorTab(val label: String) {
    Network("Network"), Database("Database"), Work("Background Work"), Logs("Logs"), Crashes("Crashes")
}

internal enum class NetworkFilter(val label: String) { All("All"), Errors("Errors"), Slow("Slow"), Writes("Writes") }
internal enum class CrashFilter(val label: String) { All("All"), Fatal("Fatal"), NonFatal("Non-fatal") }
internal enum class RequestDetailTab(val label: String) { Request("Request"), Response("Response"), Headers("Headers") }

/**
 * Three arrangements of one body. Breakpoints come straight from the handoff.
 */
internal enum class PaneWidth { Compact, Medium, Expanded }

internal fun paneWidthFor(width: Dp): PaneWidth = when {
    width < 768.dp -> PaneWidth.Compact
    width < 1440.dp -> PaneWidth.Medium
    else -> PaneWidth.Expanded
}

/** 0 means "no separate list pane" — the detail replaces the list. */
internal val PaneWidth.listPaneWidth: Dp
    get() = when (this) {
        PaneWidth.Compact -> 0.dp
        PaneWidth.Medium -> 300.dp
        PaneWidth.Expanded -> 420.dp
    }

/** How many characters of a path fit before head-truncation. */
internal val PaneWidth.pathBudget: Int
    get() = when (this) {
        PaneWidth.Compact -> 26
        PaneWidth.Medium -> 30
        PaneWidth.Expanded -> 48
    }

@Stable
internal class InspectorState {
    var tab by mutableStateOf(InspectorTab.Network)
        private set

    /** Scoped to the active tab, so it resets when the tab changes. */
    var query by mutableStateOf("")

    var networkFilter by mutableStateOf(NetworkFilter.All)
    var selectedRequestId by mutableStateOf<Long?>(null)
    var requestDetailTab by mutableStateOf(RequestDetailTab.Response)
    val collapsedJsonPaths = mutableStateListOf<String>()
    var curlVisible by mutableStateOf(false)

    var selectedTable by mutableStateOf<String?>(null)
    var sqlOpen by mutableStateOf(false)
    var sqlText by mutableStateOf("")
    var sqlError by mutableStateOf<String?>(null)
    var sqlStatus by mutableStateOf<String?>(null)
    var sqlResult by mutableStateOf<DbTable?>(null)
    var editingCell by mutableStateOf<CellAddress?>(null)
    var cellDraft by mutableStateOf("")

    var logMinLevel by mutableStateOf(LogLevel.Verbose)
    var logTag by mutableStateOf<String?>(null)
    var tailing by mutableStateOf(true)
    var pausedAt by mutableStateOf<String?>(null)

    var crashFilter by mutableStateOf(CrashFilter.All)
    var selectedCrashId by mutableStateOf<Long?>(null)
    var hideFrameworkFrames by mutableStateOf(false)

    var selectedWorkId by mutableStateOf<String?>(null)

    /** Selection is per section and clears on tab change, along with the scoped query. */
    fun selectTab(next: InspectorTab) {
        if (next == tab) return
        if (next == InspectorTab.Crashes) InspectorStore.markCrashesRead()
        tab = next
        query = ""
        selectedRequestId = null
        selectedTable = null
        sqlOpen = false
        sqlResult = null
        selectedCrashId = null
        selectedWorkId = null
        editingCell = null
    }
}

internal data class CellAddress(
    val table: String,
    val column: String,
    val rowIndex: Int,
    val type: String,
)

@Composable
internal fun rememberInspectorState(): InspectorState = remember { InspectorState() }
