package com.waqas028.kmpinspector.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The list row shows the endpoint alone, so the split has to hold up on odd URLs too. */
class UrlPartsTest {

    private fun request(url: String) = NetworkRequest(
        id = 1, method = "GET", url = url, statusCode = 200,
        durationMillis = 1, requestBytes = 0, responseBytes = 0, timestampMillis = 0,
    )

    @Test
    fun a_normal_url_splits_into_host_and_full_path() {
        val r = request("https://staging.dilkarishta.com/api/v4/get_system_recommended_broadcasts")
        assertEquals("staging.dilkarishta.com", r.host)
        assertEquals("/api/v4/get_system_recommended_broadcasts", r.pathAndQuery)
    }

    @Test
    fun the_query_string_stays_with_the_path() {
        val r = request("https://example.com/search?q=a&page=2")
        assertEquals("example.com", r.host)
        assertEquals("/search?q=a&page=2", r.pathAndQuery)
    }

    @Test
    fun a_host_only_url_has_a_root_path_rather_than_repeating_the_host() {
        val r = request("https://example.com")
        assertEquals("example.com", r.host)
        assertEquals("/", r.pathAndQuery)
    }

    @Test
    fun a_query_with_no_path_still_reads_as_a_path() {
        val r = request("https://example.com?token=abc")
        assertEquals("example.com", r.host)
        assertEquals("/?token=abc", r.pathAndQuery)
    }

    @Test
    fun a_port_belongs_to_the_host_and_a_trailing_slash_is_kept() {
        val r = request("http://localhost:8080/")
        assertEquals("localhost:8080", r.host)
        assertEquals("/", r.pathAndQuery)
    }
}
