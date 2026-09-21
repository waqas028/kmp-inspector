package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.CrashRecord
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Crashes panel keys its LazyColumn by [CrashRecord.id], so a repeated id is not a cosmetic
 * problem — Compose throws and takes the host app down. These lock that down at the store.
 */
class CrashIdTest {

    private fun crash(id: Long, message: String = "boom") = CrashRecord(
        id = id,
        fatal = true,
        exceptionType = "IllegalStateException",
        message = message,
        origin = "Checkout.kt:118",
        timestampMillis = 1_789_825_131_688,
    )

    @BeforeTest
    fun setUp() {
        Inspector.enabled = true
        Inspector.clearCrashes()
    }

    @AfterTest
    fun tearDown() {
        Inspector.clearCrashes()
    }

    @Test
    fun the_same_id_is_never_stored_twice() {
        InspectorStore.addCrash(crash(id = 7))
        InspectorStore.addCrash(crash(id = 7, message = "a different message, same id"))

        assertEquals(1, InspectorStore.crashes.size)
    }

    @Test
    fun two_crashes_in_the_same_millisecond_get_different_ids() {
        // The original bug: two handlers fired for one crash and both read the clock, so both
        // records carried the same id.
        captureFatal("IllegalStateException", "boom", "main", listOf("Foo.kt:1"), "com.example")
        captureFatal("IllegalStateException", "boom", "main", listOf("Foo.kt:1"), "com.example")

        val ids = InspectorStore.crashes.map { it.id }
        assertEquals(2, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun every_recorded_crash_keeps_a_distinct_id() {
        repeat(30) { Inspector.recordNonFatal("JsonDecodingException", "bad token", "Mapper.kt:41") }

        val ids = InspectorStore.crashes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun a_new_id_sits_above_anything_restored_from_disk() {
        // Ids written by an older build were epoch milliseconds; the counter has to clear them or a
        // fresh record could reuse one.
        val restoredId = 1_789_825_131_688L
        InspectorStore.ensureIdAbove(restoredId)

        assertTrue(InspectorStore.nextPublicId() > restoredId)
    }
}
