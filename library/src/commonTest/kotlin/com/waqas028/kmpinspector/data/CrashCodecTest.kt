package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.StackFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashCodecTest {

    private fun crash(
        id: Long = 1,
        message: String = "Cart total was null",
        causedBy: String? = null,
    ) = CrashRecord(
        id = id,
        fatal = true,
        exceptionType = "IllegalStateException",
        message = message,
        origin = "CheckoutViewModel.kt:118",
        threadName = "main",
        occurrences = 3,
        causedBy = causedBy,
        frames = listOf(
            StackFrame("com.example.shop.Checkout.confirm(Checkout.kt:118)", true),
            StackFrame("android.os.Looper.loop(Looper.java:294)", false),
        ),
        timestampMillis = 1_756_700_000_000,
    )

    @Test
    fun a_record_survives_a_round_trip() {
        val original = crash(causedBy = "Caused by: NumberFormatException")

        val decoded = CrashCodec.decode(CrashCodec.encode(listOf(original)))

        assertEquals(1, decoded.size)
        assertEquals(original, decoded.single())
    }

    @Test
    fun newlines_and_backslashes_in_a_message_survive() {
        // A stack trace pasted into a message would otherwise break the line format entirely.
        val nasty = "line one\nline two\\ttab\\\\slash\r\nwindows"
        val decoded = CrashCodec.decode(CrashCodec.encode(listOf(crash(message = nasty))))

        assertEquals(nasty, decoded.single().message)
    }

    @Test
    fun app_and_framework_frames_keep_their_classification() {
        val decoded = CrashCodec.decode(CrashCodec.encode(listOf(crash())))

        val frames = decoded.single().frames
        assertEquals(2, frames.size)
        assertTrue(frames[0].isAppFrame)
        assertTrue(!frames[1].isAppFrame)
    }

    @Test
    fun a_half_written_file_keeps_the_records_that_completed() {
        val text = CrashCodec.encode(listOf(crash(id = 1), crash(id = 2)))
        // Simulate dying mid-write: keep everything up to the second record's separator.
        val torn = text.substringBeforeLast("--")

        val decoded = CrashCodec.decode(torn)

        // The first record is intact and must not be lost with the truncated one.
        assertEquals(1, decoded.size)
        assertEquals(1L, decoded.single().id)
    }

    @Test
    fun a_record_missing_required_fields_is_dropped_not_invented() {
        val decoded = CrashCodec.decode("fatal=true\nmessage=orphan\n--\n")

        assertEquals(0, decoded.size)
    }

    @Test
    fun the_persisted_count_is_capped() {
        val many = (1..50L).map { crash(id = it) }

        val decoded = CrashCodec.decode(CrashCodec.encode(many))

        assertEquals(PERSISTED_CRASH_LIMIT, decoded.size)
    }

    @Test
    fun an_empty_file_decodes_to_nothing_rather_than_throwing() {
        assertEquals(0, CrashCodec.decode("").size)
        assertEquals(0, CrashCodec.decode("garbage without separators").size)
    }
}
