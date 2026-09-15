package com.waqas028.kmpinspector.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A JSON document passed inside a string field should read as a tree, not as one escaped line. */
class EmbeddedJsonTest {

    @Test
    fun a_string_holding_an_object_becomes_an_expandable_node() {
        val raw = """{"cityIds":[],"educationIds":[3412],"maxAge":63}"""
        val node = assertIs<JsonNode.Embedded>(JsonNode.Str(raw).withEmbeddedJson())

        assertEquals(raw, node.raw)
        val parsed = assertIs<JsonNode.Obj>(node.parsed)
        assertEquals(listOf("cityIds", "educationIds", "maxAge"), parsed.entries.map { it.first })
        assertTrue(node.isBranch())
    }

    @Test
    fun ordinary_strings_are_left_alone() {
        assertIs<JsonNode.Str>(JsonNode.Str("15").withEmbeddedJson())
        assertIs<JsonNode.Str>(JsonNode.Str("hello world").withEmbeddedJson())
        assertIs<JsonNode.Str>(JsonNode.Str("").withEmbeddedJson())
        // Looks like an object but is not valid JSON, so it stays a string.
        assertIs<JsonNode.Str>(JsonNode.Str("{not json").withEmbeddedJson())
        assertNull(parseEmbeddedJson("\"just a quoted word\""))
    }

    @Test
    fun a_document_nested_two_levels_deep_is_expanded_all_the_way() {
        // Built up rather than written out: three levels of hand-escaped quotes are unreadable
        // and easy to get wrong in the test itself.
        val deep = """{"deep":1}"""
        val mid = "{\"mid\":${quoted(deep)}}"
        val node = parseJsonOrNull("{\"outer\":${quoted(mid)}}")!!.withEmbeddedJson()

        val outer = assertIs<JsonNode.Obj>(node)
        val level1 = assertIs<JsonNode.Embedded>(outer.entries.single().second)
        val level2 = assertIs<JsonNode.Obj>(level1.parsed)
        val level3 = assertIs<JsonNode.Embedded>(level2.entries.single().second)
        assertIs<JsonNode.Obj>(level3.parsed)
    }

    private fun quoted(text: String) =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun a_collapsed_embedded_document_reports_its_size_as_a_string() {
        val node = JsonNode.Str("""{"a":1,"b":2}""").withEmbeddedJson()
        assertEquals("\"{ … 2 keys }\"", node.collapsedLabel())
    }
}
