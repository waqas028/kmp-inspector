package com.waqas028.kmpinspector.presentation.database

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.DbColumn
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.presentation.InspectorState
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlQueryTest {

    private val orderItems = DbTable(
        name = "order_items",
        columns = listOf(DbColumn("id", "INTEGER PK")),
        rows = listOf(listOf(DbValue.Number("1")), listOf(DbValue.Number("2"))),
    )

    @BeforeTest
    fun seed() {
        Inspector.clear()
        Inspector.setDatabase(DbInfo("app.db", "SQLDelight", "2.1 MB"), listOf(orderItems))
    }

    @Test
    fun select_resolves_the_from_table() {
        val state = InspectorState()
        state.sqlText = "SELECT * FROM order_items WHERE qty > 1"

        runBlocking { runQuery(state) }

        assertEquals(orderItems, state.sqlResult)
        // Status carries a timing suffix, so assert the part that is deterministic.
        assertTrue(state.sqlStatus!!.startsWith("ok · 2 rows · "))
        assertNull(state.sqlError)
    }

    @Test
    fun writes_are_rejected_before_touching_any_table() {
        val state = InspectorState()
        state.sqlText = "DELETE FROM order_items"

        runBlocking { runQuery(state) }

        assertNull(state.sqlResult)
        assertEquals("Read-only. SELECT and WITH only.", state.sqlError)
    }

    @Test
    fun an_unknown_table_reports_the_error_and_keeps_the_previous_result() {
        val state = InspectorState()
        state.sqlText = "SELECT * FROM order_items"
        runBlocking { runQuery(state) }

        state.sqlText = "SELECT * FROM nope"
        runBlocking { runQuery(state) }

        // The spec is explicit: on error the previous result stays on screen.
        assertNotNull(state.sqlResult)
        assertEquals(orderItems, state.sqlResult)
        assertTrue(state.sqlError!!.contains("nope"))
    }

    @Test
    fun opening_sql_on_a_table_prefills_that_table_s_query() {
        val state = InspectorState()

        toggleSql(state, "order_items")

        assertTrue(state.sqlOpen)
        assertEquals("SELECT * FROM order_items", state.sqlText)
    }

    @Test
    fun a_hand_written_query_survives_switching_tables() {
        val state = InspectorState()
        state.sqlText = "SELECT sku FROM order_items WHERE qty > 1"

        toggleSql(state, "products")

        // Only generated queries may be replaced; anything typed is the developer's.
        assertEquals("SELECT sku FROM order_items WHERE qty > 1", state.sqlText)
    }

    @Test
    fun a_generated_query_is_replaced_when_the_table_changes() {
        val state = InspectorState()
        state.sqlText = "SELECT * FROM order_items"

        assertTrue(isAutoQuery(state.sqlText))
        assertEquals("SELECT * FROM products", autoQueryFor("products"))
    }

    @Test
    fun running_a_query_keeps_the_table_selected() {
        val state = InspectorState()
        state.selectedTable = "order_items"
        state.sqlText = "SELECT * FROM order_items"

        runBlocking { runQuery(state) }

        // The result takes over the grid, but the table context is not lost.
        assertEquals("order_items", state.selectedTable)
        assertEquals(orderItems, state.sqlResult)
    }

    @Test
    fun an_empty_query_is_not_treated_as_a_write() {
        val state = InspectorState()
        state.sqlText = "   "

        runBlocking { runQuery(state) }

        assertEquals("Enter a query.", state.sqlError)
    }
}
