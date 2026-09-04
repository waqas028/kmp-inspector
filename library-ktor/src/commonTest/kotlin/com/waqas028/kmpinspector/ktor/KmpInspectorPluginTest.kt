package com.waqas028.kmpinspector.ktor

import com.waqas028.kmpinspector.Inspector
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpInspectorPluginTest {

    @BeforeTest
    fun reset() = Inspector.clearRequests()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine { request -> handler(request) }) { install(KmpInspectorPlugin) }

    @Test
    fun a_json_response_is_recorded_and_still_readable_by_the_caller() = runTest {
        val client = client {
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val text = client.get("https://example.com/items?page=2").bodyAsText()

        assertEquals("""{"ok":true}""", text)
        val recorded = Inspector.requests().single()
        assertEquals("GET", recorded.method)
        assertEquals("https://example.com/items?page=2", recorded.url)
        assertEquals(200, recorded.statusCode)
        assertEquals("""{"ok":true}""", recorded.responseBody)
        assertEquals("application/json", recorded.contentType)
    }

    @Test
    fun a_request_body_is_captured_and_an_error_status_is_kept() = runTest {
        val client = client { respondError(HttpStatusCode.UnprocessableEntity, """{"error":"bad"}""") }
        client.post("https://example.com/orders") { setBody("""{"qty":1}""") }

        val recorded = Inspector.requests().single()
        assertEquals("POST", recorded.method)
        assertEquals(422, recorded.statusCode)
        assertEquals("""{"qty":1}""", recorded.requestBody)
    }

    @Test
    fun a_binary_response_records_size_but_not_body() = runTest {
        val bytes = ByteArray(1024) { it.toByte() }
        val client = client {
            respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
        }
        client.get("https://example.com/a.png")

        val recorded = Inspector.requests().single()
        assertNull(recorded.responseBody)
        assertEquals(200, recorded.statusCode)
    }

    @Test
    fun a_transport_failure_is_recorded_and_rethrown() = runTest {
        val client = client { throw IllegalStateException("connection reset") }
        assertFailsWith<IllegalStateException> { client.get("https://example.com/down") }

        val recorded = Inspector.requests().single()
        assertNull(recorded.statusCode)
        assertTrue(recorded.errorText!!.contains("connection reset"))
    }
}
