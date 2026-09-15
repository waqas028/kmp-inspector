package com.waqas028.kmpinspector.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormTest {

    @Test
    fun a_form_body_splits_into_pairs_and_keeps_an_empty_trailing_value() {
        val pairs = parseFormEncodedOrNull("current_offset=0&page_size=15&matches_type=2&search_query=")!!
        assertEquals(
            listOf("current_offset" to "0", "page_size" to "15", "matches_type" to "2", "search_query" to ""),
            pairs,
        )
    }

    @Test
    fun values_are_percent_decoded_including_multi_byte_characters() {
        val pairs = parseFormEncodedOrNull("name=Ali+Raza&city=Lahore%2C+PK&emoji=%F0%9F%91%8D")!!
        assertEquals("Ali Raza", pairs[0].second)
        assertEquals("Lahore, PK", pairs[1].second)
        assertEquals("\uD83D\uDC4D", pairs[2].second)
    }

    @Test
    fun json_and_prose_are_not_mistaken_for_forms() {
        assertNull(parseFormEncodedOrNull("""{"page_size":15}"""))
        assertNull(parseFormEncodedOrNull("the total = 15 items"))
        assertNull(parseFormEncodedOrNull("=novalue"))
        assertNull(parseFormEncodedOrNull("just-a-token"))
        assertNull(parseFormEncodedOrNull(""))
    }

    @Test
    fun a_single_pair_still_counts() {
        assertEquals(listOf("id" to "42"), parseFormEncodedOrNull("id=42"))
    }
}
