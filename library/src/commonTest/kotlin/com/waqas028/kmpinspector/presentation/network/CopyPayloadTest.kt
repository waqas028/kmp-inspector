package com.waqas028.kmpinspector.presentation.network

import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.presentation.RequestDetailTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each tab's copy button must hand over that tab's content and nothing else. */
class CopyPayloadTest {

    private val request = NetworkRequest(
        id = 1, method = "POST", url = "https://staging.example.com/api/v1/matches/get",
        statusCode = 200, durationMillis = 1_500, requestBytes = 58, responseBytes = 8_200,
        timestampMillis = 0,
        requestHeaders = listOf(HttpHeader("Authorization", "Bearer token"), HttpHeader("locale", "en")),
        responseHeaders = listOf(HttpHeader("Content-Type", "application/json")),
        requestBody = "current_offset=0&page_size=15&search_query=",
        responseBody = """{"matches":[]}""",
    )

    @Test
    fun the_headers_tab_copies_headers_only() {
        val copied = copyPayload(request, RequestDetailTab.Headers)
        assertTrue(copied.contains("Authorization: Bearer token"))
        assertTrue(copied.contains("Content-Type: application/json"))
        assertFalse(copied.contains("page_size"))
        assertFalse(copied.contains("matches"))
    }

    @Test
    fun the_request_tab_copies_the_request_body_as_shown() {
        val copied = copyPayload(request, RequestDetailTab.Request)
        assertEquals("current_offset: 0\npage_size: 15\nsearch_query: ", copied)
        assertFalse(copied.contains("Authorization"))
    }

    @Test
    fun the_response_tab_copies_the_response_body_verbatim() {
        assertEquals("""{"matches":[]}""", copyPayload(request, RequestDetailTab.Response))
    }

    @Test
    fun an_absent_body_says_so_rather_than_copying_nothing() {
        val empty = request.copy(requestBody = null, responseBody = "   ")
        assertEquals("No body", copyPayload(empty, RequestDetailTab.Request))
        assertEquals("No body", copyPayload(empty, RequestDetailTab.Response))
    }
}
