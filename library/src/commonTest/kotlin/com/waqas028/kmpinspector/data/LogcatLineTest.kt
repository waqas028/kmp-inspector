package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogcatLineTest {
    @Test
    fun brief_lines_parse_into_level_tag_and_message() {
        val line = parseLogcatLine("D/OkHttp  ( 2399): --> POST https://example.com")!!
        assertEquals(LogLevel.Debug, line.level)
        assertEquals("OkHttp", line.tag)
        assertEquals("--> POST https://example.com", line.message)
    }

    @Test
    fun tags_with_spaces_and_parentheses_survive() {
        val line = parseLogcatLine("W/My Tag (x)(12345): careful")!!
        assertEquals(LogLevel.Warn, line.level)
        assertEquals("My Tag (x)", line.tag)
        assertEquals("careful", line.message)
    }

    @Test
    fun fatal_maps_to_error_and_noise_is_dropped() {
        assertEquals(LogLevel.Error, parseLogcatLine("F/libc    (1): abort")!!.level)
        assertNull(parseLogcatLine("--------- beginning of main"))
        assertNull(parseLogcatLine(""))
    }
}
