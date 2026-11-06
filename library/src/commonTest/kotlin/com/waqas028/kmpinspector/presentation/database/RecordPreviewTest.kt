package com.waqas028.kmpinspector.presentation.database

import com.waqas028.kmpinspector.domain.model.DbValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordPreviewTest {

    @Test
    fun null_and_empty_text_stay_distinct_in_a_closed_row() {
        assertEquals("NULL", previewOf(DbValue.Null))
        assertEquals("—", previewOf(DbValue.Text("")))
    }

    @Test
    fun multi_line_text_is_flattened_to_one_line() {
        assertEquals("line one line two", previewOf(DbValue.Text("line one\nline two")))
    }

    @Test
    fun long_text_is_cut_before_it_is_measured() {
        val preview = previewOf(DbValue.Text("x".repeat(10_000)))
        assertTrue(preview.length <= 120)
    }
}
