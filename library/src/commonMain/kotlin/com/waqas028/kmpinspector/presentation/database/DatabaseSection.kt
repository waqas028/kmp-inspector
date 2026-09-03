package com.waqas028.kmpinspector.presentation.database

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorStore
import kotlin.time.TimeSource
import com.waqas028.kmpinspector.data.formatBytes
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.presentation.CellAddress
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.NameOrder
import com.waqas028.kmpinspector.presentation.SortOrder
import com.waqas028.kmpinspector.presentation.flip
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.HitTarget
import com.waqas028.kmpinspector.presentation.common.NoResults
import com.waqas028.kmpinspector.presentation.common.OutlineChip
import com.waqas028.kmpinspector.presentation.common.ScrollToTop
import com.waqas028.kmpinspector.presentation.common.SortToggle
import com.waqas028.kmpinspector.presentation.common.StatusLine
import com.waqas028.kmpinspector.presentation.shell.MasterDetail
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType

private const val CELL_WIDTH_DP = 132

@Composable
internal fun DatabaseSection(state: InspectorState, pane: PaneWidth) {
    val tables = InspectorStore.tables
    if (tables.isEmpty()) {
        EmptyState(
            glyph = Glyph.TableChart,
            title = "No database registered",
            sentence = "Hand the inspector your schema and it will browse tables, run read-only queries and edit cells.",
            snippet = "Inspector.setDatabase(info, tables)",
        )
        return
    }

    val q = state.query.trim()
    val filtered = tables
        .filter { q.isEmpty() || it.name.contains(q, true) }
        .let { list ->
            if (state.tableSort == NameOrder.Ascending) list.sortedBy { it.name.lowercase() }
            else list.sortedByDescending { it.name.lowercase() }
        }
    val selected = tables.firstOrNull { it.name == state.selectedTable }

    MasterDetail(
        pane = pane,
        // Opening the SQL editor is itself a selection. Without this the editor lives in a pane
        // that is never shown until a table is picked, so the SQL button looks dead.
        hasSelection = selected != null || state.sqlOpen,
        onBack = {
            state.selectedTable = null
            state.sqlOpen = false
        },
        placeholder = "Select a table, or open SQL",
        list = { TableList(state, filtered) },
        detail = { DatabaseDetail(selected, state) },
    )
}

@Composable
private fun TableList(state: InspectorState, tables: List<DbTable>) {
    val info = InspectorStore.database
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                info?.let { "${it.fileName} · ${it.engine} · ${it.sizeLabel}" } ?: "database",
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = InspectorType.meta,
                maxLines = 2,
            )
            HitTarget(onClick = { toggleSql(state, state.selectedTable) }) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(
                            if (state.sqlOpen) DebugPalette.activePillFill else Color.Transparent,
                            RoundedCornerShape(4.dp),
                        )
                        .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InspectorIcon(Glyph.Terminal, null, size = 13.dp, tint = DebugPalette.accent)
                    Text(
                        if (state.sqlOpen) "Hide SQL" else "SQL",
                        modifier = Modifier.padding(start = 6.dp),
                        style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent),
                        maxLines = 1,
                    )
                }
            }
        }
        Hairline()
        if (tables.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NoResults(
                    message = "No tables match \"${state.query.trim()}\"",
                    actionLabel = "Clear search",
                    onAction = { state.query = "" },
                )
            }
            return@Column
        }
        val listState = rememberLazyListState()
        Box(Modifier.weight(1f)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(tables, key = { it.name }) { table ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            if (table.name == state.selectedTable) DebugPalette.selectionFill else Color.Transparent,
                        )
                        .clickable {
                            // Selecting a table clears any query result.
                            state.selectedTable = table.name
                            state.sqlResult = null
                            state.sqlStatus = null
                            state.sqlError = null
                            if (isAutoQuery(state.sqlText)) state.sqlText = autoQueryFor(table.name)
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InspectorIcon(Glyph.TableChart, null, size = 15.dp, tint = DebugPalette.textDim)
                    Text(
                        table.name,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = InspectorType.mono(13.sp, color = DebugPalette.text),
                    )
                    Text(
                        table.rowCount.toString(),
                        style = InspectorType.mono(
                            11.5.sp,
                            color = if (table.rowCount == 0) DebugPalette.textFaint else DebugPalette.textDim,
                            tabular = true,
                        ),
                    )
                    InspectorIcon(
                        Glyph.ChevronRight, null,
                        modifier = Modifier.padding(start = 8.dp),
                        size = 14.dp, tint = DebugPalette.textFaint,
                    )
                }
                Hairline(color = DebugPalette.lineFaint)
            }
        }
        ScrollToTop(listState)
        }
        Hairline()
        // Sort and refresh sit on the status line: it never scrolls, so nothing gets clipped.
        StatusLine(text = "${tables.size} tables") {
            SortToggle(state.tableSort, onToggle = { state.tableSort = state.tableSort.flip() })
            Spacer(Modifier.width(8.dp))
            RefreshButton()
        }
    }
}

/**
 * Bottom-right of both Database panes. Re-reads the live database, or replays the open hooks.
 * While the snapshot is in flight the arrow becomes a spinner. The spinner stays up for at least
 * a second even when the read is instant, so the tap visibly did something, and a timeout clears
 * it in case a host never answers.
 */
@Composable
private fun RefreshButton() {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { InspectorStore.databaseRefreshing }.collect { active ->
            if (!active) return@collect
            refreshing = true
            val started = TimeSource.Monotonic.markNow()
            withTimeoutOrNull(10_000) {
                snapshotFlow { InspectorStore.databaseRefreshing }.first { !it }
            }
            InspectorStore.databaseRefreshing = false
            delay((1_000 - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            refreshing = false
        }
    }
    HitTarget(onClick = { if (!refreshing) InspectorStore.refreshDatabase() }) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .background(DebugPalette.activePillFill, RoundedCornerShape(16.dp))
                .border(1.dp, DebugPalette.accent, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = DebugPalette.accent,
                    strokeWidth = 1.5.dp,
                )
            } else {
                Text("↻", style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent))
            }
            Text(
                if (refreshing) "Refreshing" else "Refresh",
                modifier = Modifier.padding(start = 6.dp),
                style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DatabaseDetail(table: DbTable?, state: InspectorState) {
    val result = state.sqlResult
    Column(Modifier.fillMaxSize()) {
        // The SQL toggle has to be reachable from here too: at phone width the detail pane replaces
        // the list, so the list's own button is off-screen exactly when you are looking at a table.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = table?.name ?: result?.name ?: "Query",
                modifier = Modifier.weight(1f),
                style = InspectorType.mono(12.5.sp, FontWeight.Medium, DebugPalette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HitTarget(onClick = { toggleSql(state, table?.name ?: result?.name) }) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(
                            if (state.sqlOpen) DebugPalette.activePillFill else Color.Transparent,
                            RoundedCornerShape(4.dp),
                        )
                        .border(1.dp, DebugPalette.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InspectorIcon(Glyph.Terminal, null, size = 13.dp, tint = DebugPalette.accent)
                    Text(
                        if (state.sqlOpen) "Hide SQL" else "SQL",
                        modifier = Modifier.padding(start = 6.dp),
                        style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent),
                        maxLines = 1,
                    )
                }
            }
        }
        Hairline()

        if (state.sqlOpen) SqlEditor(state)

        Box(Modifier.weight(1f)) {
            when {
                // A query result takes over the grid it was run from, and is read-only.
                result != null -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "Query results are read-only.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
                    )
                    Box(Modifier.weight(1f)) { DataGrid(result, state, readOnly = true) }
                }

                table != null -> DataGrid(table, state, readOnly = false)

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Run a query, or pick a table.",
                        style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
                    )
                }
            }
        }
        Hairline()
        StatusLine(text = (result ?: table)?.let { "${it.rowCount} rows" } ?: "") {
            SortToggle(state.rowSort, onToggle = { state.rowSort = state.rowSort.flip() })
            Spacer(Modifier.width(8.dp))
            RefreshButton()
        }
        state.editingCell?.let { CellEditorSheet(it, state) }
    }
}

/**
 * Both the header row and the first column freeze: scrolling right is useless if you lose which row
 * you are on, so the primary key stays pinned while the rest slides.
 */
@Composable
private fun DataGrid(table: DbTable, state: InspectorState, readOnly: Boolean) {
    // One horizontal ScrollState shared by the header and every row keeps the columns aligned, and
    // a single LazyColumn keeps the frozen column and the scrolling columns on the same vertical
    // offset. Two independent lists would drift apart the moment you scrolled.
    val hScroll = rememberScrollState()
    val firstCol = table.columns.firstOrNull() ?: return
    val restCols = table.columns.drop(1)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            HeaderCell(firstCol.name, firstCol.type, DebugPalette.surfaceRaised)
            Box(Modifier.width(1.dp).height(48.dp).background(DebugPalette.line))
            Row(Modifier.horizontalScroll(hScroll)) {
                restCols.forEach { HeaderCell(it.name, it.type, DebugPalette.surfaceRaised) }
            }
        }
        // Display order may be reversed, but edits address the original row index, so the pair
        // travels together.
        val ordered = table.rows.withIndex().toList()
            .let { if (state.rowSort == SortOrder.NewestFirst) it.asReversed() else it }
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(ordered, key = { it.index }) { (rowIndex, row) ->
                Row(Modifier.fillMaxWidth()) {
                    GridCell(
                        value = row.getOrNull(0) ?: DbValue.Null,
                        frozen = true,
                        selected = isSelected(state, table, firstCol.name, rowIndex),
                        onClick = {
                            if (!readOnly) {
                                openEditor(state, table, firstCol.name, firstCol.type, rowIndex, row.getOrNull(0))
                            }
                        },
                    )
                    Box(Modifier.width(1.dp).height(48.dp).background(DebugPalette.line))
                    Row(Modifier.horizontalScroll(hScroll)) {
                        restCols.forEachIndexed { i, col ->
                            val value = row.getOrNull(i + 1) ?: DbValue.Null
                            GridCell(
                                value = value,
                                frozen = false,
                                selected = isSelected(state, table, col.name, rowIndex),
                                onClick = {
                                    if (!readOnly) {
                                        openEditor(state, table, col.name, col.type, rowIndex, value)
                                    }
                                },
                            )
                        }
                    }
                }
                Hairline(color = DebugPalette.lineFaint)
            }
        }
        ScrollToTop(listState)
        }
    }
}

private fun isSelected(state: InspectorState, table: DbTable, column: String, row: Int) =
    state.editingCell?.let { it.table == table.name && it.column == column && it.rowIndex == row } == true

private fun openEditor(
    state: InspectorState,
    table: DbTable,
    column: String,
    type: String,
    rowIndex: Int,
    value: DbValue?,
) {
    state.editingCell = CellAddress(table.name, column, rowIndex, type)
    state.cellDraft = when (value) {
        is DbValue.Text -> value.value
        is DbValue.Number -> value.value
        is DbValue.Blob -> ""
        else -> ""
    }
}

/** Column name with its type underneath — the type decides how a value should be read. */
@Composable
private fun HeaderCell(name: String, type: String, fill: Color) {
    Column(
        Modifier
            .width(CELL_WIDTH_DP.dp)
            .background(fill)
            .padding(8.dp),
    ) {
        Text(
            name,
            style = InspectorType.mono(11.sp, FontWeight.Medium, DebugPalette.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            type,
            style = InspectorType.mono(10.sp, color = DebugPalette.textFaint),
            maxLines = 1,
        )
    }
}

@Composable
private fun GridCell(value: DbValue, frozen: Boolean, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(CELL_WIDTH_DP.dp)
            .height(48.dp)
            .background(
                when {
                    selected -> DebugPalette.cellSelectedFill
                    frozen -> DebugPalette.surfaceSunken
                    else -> Color.Transparent
                },
            )
            .then(
                if (selected) Modifier.border(1.dp, DebugPalette.accent) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (value) {
            // NULL is the word, never an empty cell; an empty string is an em dash, so the two
            // stay visibly distinct; a BLOB reports type and size rather than mojibake.
            DbValue.Null -> Text(
                "NULL",
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint)
                    .copy(fontStyle = FontStyle.Italic),
            )
            is DbValue.Blob -> OutlineChip("BLOB · ${formatBytes(value.bytes)}")
            is DbValue.Text -> if (value.value.isEmpty()) {
                Text("—", style = InspectorType.mono(12.sp, color = DebugPalette.textFaint))
            } else {
                Text(
                    value.value,
                    style = InspectorType.mono(12.sp, color = DebugPalette.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            is DbValue.Number -> Text(
                value.value,
                style = InspectorType.mono(12.sp, color = DebugPalette.text, tabular = true),
                maxLines = 1,
            )
        }
    }
}

/**
 * A sheet pinned to the bottom of the pane, not an inline field: a phone keyboard would cover the
 * grid.
 */
@Composable
private fun CellEditorSheet(address: CellAddress, state: InspectorState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DebugPalette.surfaceRaised)
            .padding(16.dp),
    ) {
        Text(
            "${address.table} · ${address.column} · row ${address.rowIndex + 1} · ${address.type}",
            style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                .padding(10.dp),
        ) {
            BasicTextField(
                value = state.cellDraft,
                onValueChange = { state.cellDraft = it },
                textStyle = InspectorType.mono(12.sp, color = DebugPalette.text, lineHeight = 19.sp),
                cursorBrush = SolidColor(DebugPalette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetButton("Update row") { commitCell(state, state.cellDraft) }
            SheetButton("Set NULL") { commitCell(state, null) }
            SheetButton("Cancel") { state.editingCell = null }
        }
        val live = InspectorStore.databaseController != null &&
            InspectorStore.tables.firstOrNull { it.name == address.table }?.rowIds != null
        Text(
            if (live) "Writes go straight to the device database."
            else "No live database attached: this edits the snapshot only.",
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(10.5.sp, color = if (live) DebugPalette.warn else DebugPalette.textFaint),
        )
    }
}

@Composable
private fun SheetButton(label: String, onClick: () -> Unit) {
    HitTarget(onClick = onClick) {
        Box(
            Modifier
                .height(32.dp)
                .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = InspectorType.mono(11.5.sp, color = DebugPalette.text), maxLines = 1)
        }
    }
}

/**
 * Writes the edited cell. With a live database and a rowid the write goes to the host through
 * [DatabaseController]; otherwise the snapshot itself is patched so the grid still reflects it.
 */
internal fun commitCell(state: InspectorState, value: String?) {
    val address = state.editingCell ?: return
    state.editingCell = null
    val index = InspectorStore.tables.indexOfFirst { it.name == address.table }
    if (index < 0) return
    val table = InspectorStore.tables[index]
    val rowId = table.rowIds?.getOrNull(address.rowIndex)
    val controller = InspectorStore.databaseController
    if (controller != null && rowId != null) {
        controller.updateCell(table.name, rowId, address.column, value)
        return
    }
    val col = table.columns.indexOfFirst { it.name == address.column }
    val row = table.rows.getOrNull(address.rowIndex) ?: return
    if (col < 0 || col >= row.size) return
    val numeric = address.type.uppercase().let { it.contains("INT") || it.contains("REAL") || it.contains("NUM") }
    val newValue = when {
        value == null -> DbValue.Null
        numeric && value.toDoubleOrNull() != null -> DbValue.Number(value)
        else -> DbValue.Text(value)
    }
    val rows = table.rows.toMutableList()
    rows[address.rowIndex] = row.toMutableList().also { it[col] = newValue }
    InspectorStore.tables[index] = table.copy(rows = rows)
}

/** Results render in the same grid component: one presentation for browsing and querying. */
@Composable
private fun SqlEditor(state: InspectorState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DebugPalette.surfaceSunken)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                .padding(10.dp),
        ) {
            if (state.sqlText.isEmpty()) {
                Text(
                    "SELECT * FROM order_items WHERE qty > 1",
                    style = InspectorType.mono(12.sp, color = DebugPalette.textFaint, lineHeight = 19.2.sp),
                )
            }
            BasicTextField(
                value = state.sqlText,
                onValueChange = { state.sqlText = it },
                textStyle = InspectorType.mono(12.sp, color = DebugPalette.text, lineHeight = 19.2.sp),
                cursorBrush = SolidColor(DebugPalette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetButton("Run") { runQuery(state) }
            // On error the previous result stays on screen.
            Text(
                state.sqlError ?: state.sqlStatus ?: "Read-only. SELECT and WITH only.",
                modifier = Modifier.padding(start = 10.dp),
                style = InspectorType.mono(
                    11.sp,
                    color = if (state.sqlError != null) DebugPalette.bad else DebugPalette.textFaint,
                    tabular = true,
                ),
            )
        }
    }
    Hairline()
}

private val FROM_TABLE = Regex("""\bfrom\s+"?([A-Za-z_][A-Za-z0-9_]*)"?""", RegexOption.IGNORE_CASE)

/**
 * The inspector holds tables handed to it, not a live database, so there is no SQL engine here.
 * Run resolves the FROM clause against the registered tables and renders that table's rows in the
 * same grid. Anything that is not a SELECT or WITH is rejected before it gets that far.
 */
internal fun runQuery(state: InspectorState) {
    val started = TimeSource.Monotonic.markNow()
    val sql = state.sqlText.trim()
    fun elapsedMs() = started.elapsedNow().inWholeMilliseconds
    when {
        sql.isEmpty() -> {
            state.sqlError = "Enter a query."
            state.sqlStatus = null
        }

        !(sql.startsWith("SELECT", true) || sql.startsWith("WITH", true)) -> {
            state.sqlError = "Read-only. SELECT and WITH only."
            state.sqlStatus = null
        }

        else -> {
            val name = FROM_TABLE.find(sql)?.groupValues?.get(1)
            val table = InspectorStore.tables.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (table == null) {
                // On error the previous result stays on screen.
                state.sqlError = if (name == null) "Could not find a FROM clause." else "no such table: $name"
                state.sqlStatus = null
            } else {
                state.sqlResult = table
                state.sqlError = null
                state.sqlStatus = "ok · ${table.rowCount} rows · ${elapsedMs()} ms"
            }
        }
    }
}

/** The query the SQL button offers for a table you are already looking at. */
internal fun autoQueryFor(table: String) = "SELECT * FROM $table"

/**
 * True when the editor still holds a generated query, so switching tables may safely replace it.
 * Anything the developer typed themselves is left alone.
 */
internal fun isAutoQuery(text: String) =
    text.isBlank() || Regex("""^SELECT \* FROM [A-Za-z_][A-Za-z0-9_]*$""").matches(text.trim())

/** Opens the editor pre-filled for [table], or closes it. */
internal fun toggleSql(state: InspectorState, table: String?) {
    if (state.sqlOpen) {
        state.sqlOpen = false
        return
    }
    state.sqlOpen = true
    if (table != null && isAutoQuery(state.sqlText)) state.sqlText = autoQueryFor(table)
}
