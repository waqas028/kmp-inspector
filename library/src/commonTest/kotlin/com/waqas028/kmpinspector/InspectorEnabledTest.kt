package com.waqas028.kmpinspector

import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.LogLevel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The master switch has to silence capture, not merely hide the UI. */
class InspectorEnabledTest {

    @BeforeTest
    fun setUp() {
        Inspector.enabled = true
        InspectorStore.clear()
    }

    @AfterTest
    fun tearDown() {
        Inspector.enabled = true
        InspectorStore.clear()
    }

    @Test
    fun nothing_is_captured_while_disabled() {
        Inspector.enabled = false

        Inspector.recordRequest(method = "GET", url = "https://x", statusCode = 200, durationMillis = 1)
        InspectorLog.i("Tag", "message")
        Inspector.recordNonFatal("Boom", "went wrong", "X.kt:1")

        assertTrue(InspectorStore.requests.isEmpty())
        assertTrue(InspectorStore.logs.isEmpty())
        assertTrue(InspectorStore.crashes.isEmpty())
        assertEquals(0, InspectorStore.unreadCount)
    }

    @Test
    fun no_database_handle_is_kept_while_disabled() {
        Inspector.enabled = false

        Inspector.setDatabase(
            info = DbInfo("app.db", "SQLite", "1 KB"),
            tables = listOf(DbTable("users", emptyList(), emptyList())),
        )

        assertNull(InspectorStore.database)
        assertNull(InspectorStore.databaseController)
        assertTrue(InspectorStore.tables.isEmpty())
    }

    @Test
    fun capture_resumes_once_it_is_switched_back_on() {
        Inspector.enabled = false
        InspectorLog.d("Tag", "dropped")
        Inspector.enabled = true
        InspectorLog.d("Tag", "kept")

        assertEquals(1, InspectorStore.logs.size)
        assertEquals("kept", InspectorStore.logs.single().message)
        assertEquals(LogLevel.Debug, InspectorStore.logs.single().level)
    }
}
