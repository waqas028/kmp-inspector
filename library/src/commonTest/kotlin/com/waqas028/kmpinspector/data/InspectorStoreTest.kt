package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.NetworkRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectorStoreTest {
    private val defaultBudget = InspectorStore.bodyBudgetChars

    @BeforeTest
    fun reset() = InspectorStore.clear()

    @AfterTest
    fun restore() {
        InspectorStore.bodyBudgetChars = defaultBudget
        InspectorStore.clear()
    }

    private fun request(id: Long, body: String) = NetworkRequest(
        id = id, method = "GET", url = "https://x/$id", statusCode = 200, durationMillis = 1,
        requestBytes = 0, responseBytes = body.length.toLong(), timestampMillis = id, responseBody = body,
    )

    @Test
    fun oldest_bodies_are_released_once_the_budget_is_exceeded_and_metadata_stays() {
        InspectorStore.bodyBudgetChars = 250
        repeat(3) { InspectorStore.addRequest(request(it.toLong(), "x".repeat(100))) }

        val newestFirst = InspectorStore.requests
        assertEquals(3, newestFirst.size)
        assertEquals(100, newestFirst[0].responseBody?.length)
        assertEquals(100, newestFirst[1].responseBody?.length)
        assertNull(newestFirst[2].responseBody)
        assertTrue(newestFirst[2].bodiesEvicted)
        assertEquals(100L, newestFirst[2].responseBytes)
        assertFalse(newestFirst[0].bodiesEvicted)
    }

    @Test
    fun clearing_requests_also_resets_the_unread_badge() {
        InspectorStore.addRequest(request(1, "a"))
        InspectorStore.addRequest(request(2, "b"))
        assertEquals(2, InspectorStore.unreadCount)

        InspectorStore.clearRequests()
        assertEquals(0, InspectorStore.requests.size)
        assertEquals(0, InspectorStore.unreadCount)
    }

    @Test
    fun a_log_batch_is_trimmed_to_capacity_in_one_go() {
        val entries = List(InspectorStore.LOG_CAPACITY + 5) {
            InspectorStore.LogEntry(com.waqas028.kmpinspector.domain.model.LogLevel.Debug, "t", "m$it", it.toLong())
        }
        InspectorStore.addLogs(entries)
        assertEquals(InspectorStore.LOG_CAPACITY, InspectorStore.logs.size)
        assertEquals("m5", InspectorStore.logs.first().message)
    }
}
