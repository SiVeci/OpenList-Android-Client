package io.openlist.client.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [buildScopedHttpHeaders]'s host-scoping decision (v0.4_EXECUTION_PLAN.md
 * §11 S5-T1, PRD §10.4): the `Authorization` header must only ever be attached
 * when the request url's host exactly matches the owning instance's host.
 */
class ScopedHttpHeadersTest {

    @Test
    fun `matching host attaches the Authorization header`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://openlist.example.com/d/movie.mp4?sign=abc",
            instanceBaseUrl = "https://openlist.example.com",
            token = "token-123",
        )

        assertEquals(mapOf("Authorization" to "token-123"), headers)
    }

    @Test
    fun `mismatched host such as a CDN or third-party direct link attaches nothing`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://cdn.otherhost.com/bucket/movie.mp4?sign=abc",
            instanceBaseUrl = "https://openlist.example.com",
            token = "token-123",
        )

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `null token attaches nothing even for a matching host`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://openlist.example.com/d/movie.mp4",
            instanceBaseUrl = "https://openlist.example.com",
            token = null,
        )

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `blank token attaches nothing`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://openlist.example.com/d/movie.mp4",
            instanceBaseUrl = "https://openlist.example.com",
            token = "   ",
        )

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `unparsable request url attaches nothing`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "not a url",
            instanceBaseUrl = "https://openlist.example.com",
            token = "token-123",
        )

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `unparsable instance base url attaches nothing`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://openlist.example.com/d/movie.mp4",
            instanceBaseUrl = "not a url",
            token = "token-123",
        )

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `same host different port attaches nothing`() {
        val headers = buildScopedHttpHeaders(
            requestUrl = "https://openlist.example.com:8080/d/movie.mp4",
            instanceBaseUrl = "https://openlist.example.com:9000",
            token = "token-123",
        )

        assertTrue(headers.isEmpty())
    }
}
