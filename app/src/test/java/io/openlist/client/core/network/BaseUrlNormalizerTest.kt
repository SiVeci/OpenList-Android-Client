package io.openlist.client.core.network

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlNormalizerTest {

    @Test
    fun `accepts a plain https host and derives display name`() {
        val result = BaseUrlNormalizer.normalize("https://example.com") as ApiResult.Success
        assertEquals("https://example.com", result.data.baseUrl)
        assertEquals("example.com", result.data.displayName)
    }

    @Test
    fun `strips trailing slash and surrounding whitespace`() {
        val result = BaseUrlNormalizer.normalize("  https://example.com/  ") as ApiResult.Success
        assertEquals("https://example.com", result.data.baseUrl)
    }

    @Test
    fun `preserves a sub-path deployment without its trailing slash`() {
        val result = BaseUrlNormalizer.normalize("https://example.com/openlist/") as ApiResult.Success
        assertEquals("https://example.com/openlist", result.data.baseUrl)
    }

    @Test
    fun `accepts http for local or intranet instances`() {
        val result = BaseUrlNormalizer.normalize("http://192.168.1.10:5244") as ApiResult.Success
        assertEquals("http://192.168.1.10:5244", result.data.baseUrl)
    }

    @Test
    fun `rejects an address with no scheme`() {
        val result = BaseUrlNormalizer.normalize("example.com") as ApiResult.Failure
        assertEquals(DomainError.InvalidInstance, result.error)
    }

    @Test
    fun `rejects a non-http scheme`() {
        val result = BaseUrlNormalizer.normalize("ftp://example.com") as ApiResult.Failure
        assertEquals(DomainError.InvalidInstance, result.error)
    }

    @Test
    fun `rejects garbage input`() {
        val result = BaseUrlNormalizer.normalize("not a url at all")
        assertTrue(result is ApiResult.Failure)
    }
}
