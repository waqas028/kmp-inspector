package com.waqas028.kmpinspector.presentation.network

import com.waqas028.kmpinspector.data.JsonNode
import com.waqas028.kmpinspector.data.parseJsonOrNull
import com.waqas028.kmpinspector.data.withEmbeddedJson
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkTextTest {
    private val request = NetworkRequest(
        id = 1, method = "POST", url = "https://api.example.com/login", statusCode = 200,
        durationMillis = 120, requestBytes = 10, responseBytes = 20, timestampMillis = 0,
        requestHeaders = listOf(HttpHeader("Authorization", "Bearer secret-token"), HttpHeader("Accept", "application/json")),
        responseHeaders = listOf(HttpHeader("Set-Cookie", "session=abc")),
        requestBody = """{"user":"w"}""",
        responseBody = """{"ok":true}""",
    )

    @Test
    fun sharing_masks_secrets_but_the_clipboard_copy_keeps_them() {
        val shared = shareableText(request, redact = true)
        assertTrue(shared.contains("Authorization: <redacted>"))
        assertTrue(shared.contains("Set-Cookie: <redacted>"))
        assertTrue(shared.contains("Accept: application/json"))
        assertFalse(shared.contains("secret-token"))

        val copied = curlFor(request)
        assertTrue(copied.contains("Bearer secret-token"))
        assertTrue(curlFor(request, redact = true).contains("Authorization: <redacted>"))
    }

    @Test
    fun text_export_lists_sections_in_tab_order() {
        val text = shareableText(request)
        val order = listOf("Request headers", "Request body", "Response headers", "Response body").map { text.indexOf(it) }
        assertTrue(order.all { it >= 0 }, text)
        assertEquals(order, order.sorted())
    }

    @Test
    fun flattening_hides_children_of_collapsed_branches_and_keys_stay_unique() {
        val node = parseJsonOrNull("""{"a":1,"list":[{"b":2},{"c":3}],"d":"x"}""")!!
        val open = flattenJson(node, emptySet())
        val collapsed = flattenJson(node, setOf("$.list"))

        assertTrue(open.size > collapsed.size)
        assertTrue(collapsed.none { it.path.startsWith("$.list[") })
        assertEquals(open.map { it.key }.toSet().size, open.size)
        assertTrue(collapsed.filterIsInstance<JsonRow.Branch>().single { it.path == "$.list" }.collapsed)
    }

    @Test
    fun a_json_document_inside_a_string_flattens_into_its_own_rows() {
        val node = parseJsonOrNull("""{"sort":"{\"minAge\":19,\"maxAge\":63}"}""")!!.withEmbeddedJson()

        val open = flattenJson(node, emptySet())
        assertTrue(open.any { it.path == "$.sort.minAge" }, open.map { it.path }.toString())
        assertTrue(open.any { it.path == "$.sort.maxAge" })

        // Collapsing the field hides what is inside it, and nothing else.
        val shut = flattenJson(node, setOf("$.sort"))
        assertTrue(shut.none { it.path.startsWith("$.sort.") })
        assertTrue(shut.any { it.path == "$.sort" })
    }

    @Test
    fun form_pairs_render_without_a_wrapping_root() {
        val form = JsonNode.Obj(
            listOf(
                "page_size" to JsonNode.Str("15"),
                "sort" to JsonNode.Str("""{"minAge":19}""").withEmbeddedJson(),
            ),
        )
        val rows = flattenJson(form, emptySet(), rootPath = "form", includeRoot = false)

        // No "form" row of its own: the fields start at the top level.
        assertTrue(rows.none { it.path == "form" }, rows.map { it.path }.toString())
        assertEquals(0, rows.first { it.path == "form.page_size" }.depth)
        assertTrue(rows.any { it.path == "form.sort.minAge" })
    }
}
