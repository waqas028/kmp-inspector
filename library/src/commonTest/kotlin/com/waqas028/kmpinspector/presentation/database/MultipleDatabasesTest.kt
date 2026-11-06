package com.waqas028.kmpinspector.presentation.database

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.domain.model.DbColumn
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.presentation.CellAddress
import com.waqas028.kmpinspector.presentation.InspectorState
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultipleDatabasesTest {

    private fun table(name: String, vararg values: String) = DbTable(
        name = name,
        columns = listOf(DbColumn("name", "TEXT")),
        rows = values.map { listOf(DbValue.Text(it)) },
    )

    private val users = table("users", "Momo")
    private val entries = table("entries", "a", "b")

    @BeforeTest
    fun seed() {
        Inspector.clear()
        Inspector.setDatabase(DbInfo("app.db", "SQLite", "1 KB"), listOf(users))
        Inspector.setDatabase(DbInfo("cache.db", "SQLite", "2 KB"), listOf(entries))
    }

    @Test
    fun a_second_database_is_added_not_swapped_in() {
        assertEquals(listOf("app.db", "cache.db"), InspectorStore.databases.map { it.info.fileName })
        // The first one stays on screen until the developer picks another.
        assertEquals("app.db", InspectorStore.database?.fileName)
        assertEquals(listOf(users), InspectorStore.tables)
    }

    @Test
    fun a_refresh_replaces_only_its_own_database() {
        val refreshed = table("users", "Momo", "Ali")
        Inspector.setDatabase(DbInfo("app.db", "SQLite", "1 KB"), listOf(refreshed))

        assertEquals(2, InspectorStore.databases.size)
        assertEquals(listOf(refreshed), InspectorStore.tables)
    }

    @Test
    fun picking_a_database_shows_its_tables_and_drops_the_old_selection() {
        val state = InspectorState()
        state.selectedTable = "users"
        state.sqlResult = users

        selectDatabase(state, "cache.db")

        assertEquals(listOf(entries), InspectorStore.tables)
        assertNull(state.selectedTable)
        assertNull(state.sqlResult)
    }

    @Test
    fun a_snapshot_edit_lands_in_the_shown_database_only() {
        val state = InspectorState()
        selectDatabase(state, "cache.db")
        state.editingCell = CellAddress("entries", "name", rowIndex = 0, type = "TEXT")

        commitCell(state, "z")

        assertEquals(DbValue.Text("z"), InspectorStore.tables.single().rows[0][0])
        val appDb = InspectorStore.databases.first { it.info.fileName == "app.db" }
        assertEquals(listOf(users), appDb.tables)
    }
}
