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
internal enum class RequestDetailTab(val label: String) { Headers("Headers"), Request("Request"), Response("Response") }

/**
 * Every list defaults to newest first: the thing you just did is the thing you are looking for.
 * The toggle flips to oldest first for reading a sequence in the order it happened.
 */
internal enum class SortOrder { NewestFirst, OldestFirst }

internal fun SortOrder.flip(): SortOrder =
    if (this == SortOrder.NewestFirst) SortOrder.OldestFirst else SortOrder.NewestFirst

/** Table names have no time, so the Database list sorts by name instead. */
internal enum class NameOrder { Ascending, Descending }

internal fun NameOrder.flip(): NameOrder =
    if (this == NameOrder.Ascending) NameOrder.Descending else NameOrder.Ascending

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
    var networkSort by mutableStateOf(SortOrder.NewestFirst)
    var selectedRequestId by mutableStateOf<Long?>(null)
    var requestDetailTab by mutableStateOf(RequestDetailTab.Response)
    /** A set, not a list: the tree checks membership once per node, and bodies have thousands. */
    var collapsedJsonPaths by mutableStateOf<Set<String>>(emptySet())
    var curlVisible by mutableStateOf(false)

    var selectedTable by mutableStateOf<String?>(null)
    var tableSort by mutableStateOf(NameOrder.Ascending)
    /** Rows come back in rowid order, so newest first means the last inserted row on top. */
    var rowSort by mutableStateOf(SortOrder.NewestFirst)
    var sqlOpen by mutableStateOf(false)
    var sqlText by mutableStateOf("")
    var sqlError by mutableStateOf<String?>(null)
    var sqlStatus by mutableStateOf<String?>(null)
    var sqlResult by mutableStateOf<DbTable?>(null)
    var editingCell by mutableStateOf<CellAddress?>(null)
    var cellDraft by mutableStateOf("")

    /**
     * Exactly one level, or null for all of them.
     *
     * The handoff specified a floor (tapping W meant W and E). Changed to exact match on request:
     * V/D/I/W/E name one level each, so tapping I shows Info and nothing else. Because there is no
     * longer a level that means "everything", the row carries an explicit All.
     */
    var logLevel by mutableStateOf<LogLevel?>(null)
    var logTag by mutableStateOf<String?>(null)
    var tailing by mutableStateOf(true)
    var logSort by mutableStateOf(SortOrder.NewestFirst)
    var pausedAt by mutableStateOf<String?>(null)

    var crashFilter by mutableStateOf(CrashFilter.All)
    var selectedCrashId by mutableStateOf<Long?>(null)
    var hideFrameworkFrames by mutableStateOf(false)

    var selectedWorkId by mutableStateOf<String?>(null)
    var workSort by mutableStateOf(SortOrder.NewestFirst)

    /**
     * System back at phone width: step out of whatever is open on top first, one layer per press,
     * and report whether anything was consumed. The caller closes the inspector when nothing was.
     */
    fun handleBack(): Boolean = when {
        editingCell != null -> { editingCell = null; true }
        sqlOpen -> { sqlOpen = false; true }
        selectedRequestId != null -> { selectedRequestId = null; curlVisible = false; true }
        selectedTable != null || sqlResult != null -> { selectedTable = null; sqlResult = null; true }
        selectedCrashId != null -> { selectedCrashId = null; true }
        selectedWorkId != null -> { selectedWorkId = null; true }
        else -> false
    }

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

/**
 * One instance for the process, not one per composition: on Android every Activity hosts its own
 * overlay, and a rotation recreates it, so per-composition state would forget the open tab and
 * selection each time the screen changed underneath the inspector.
 */
private val sharedState = InspectorState()

@Composable
internal fun rememberInspectorState(): InspectorState = sharedState
