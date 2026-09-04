package com.waqas028.kmpinspector.presentation.network

import com.waqas028.kmpinspector.data.parseJsonOrNull
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
}
