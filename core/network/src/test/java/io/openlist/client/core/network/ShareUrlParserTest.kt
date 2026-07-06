package io.openlist.client.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exhaustive cases for [ShareUrlParser] (v1.0_EXECUTION_PLAN.md §11 S3-T1
 * DoD): https/http, explicit port, sub-path deployment, share root vs.
 * in-share path, malformed input.
 */
class ShareUrlParserTest {

    @Test
    fun `plain https share root`() {
        val result = ShareUrlParser.parse("https://example.com/@s/abcd1234")
        assertEquals("https", result?.scheme)
        assertEquals("example.com", result?.host)
        assertEquals(443, result?.port)
        assertEquals("abcd1234", result?.sid)
        assertNull(result?.path)
    }

    @Test
    fun `http with explicit non-default port`() {
        val result = ShareUrlParser.parse("http://192.168.1.10:5244/@s/xyz")
        assertEquals("http", result?.scheme)
        assertEquals("192.168.1.10", result?.host)
        assertEquals(5244, result?.port)
        assertEquals("xyz", result?.sid)
    }

    @Test
    fun `share with a sub-path resolves both sid and path`() {
        val result = ShareUrlParser.parse("https://nas.example.com:18443/@s/sid1/sub/dir/file.txt")
        assertEquals("sid1", result?.sid)
        assertEquals("sub/dir/file.txt", result?.path)
    }

    @Test
    fun `sub-path deployment still finds the @s marker`() {
        val result = ShareUrlParser.parse("https://example.com/deploy/base/@s/sid2/a.txt")
        assertEquals("example.com", result?.host)
        assertEquals("sid2", result?.sid)
        assertEquals("a.txt", result?.path)
    }

    @Test
    fun `trailing slash after sid is not treated as a sub-path`() {
        val result = ShareUrlParser.parse("https://example.com/@s/sid3/")
        assertEquals("sid3", result?.sid)
        assertNull(result?.path)
    }

    @Test
    fun `no @s marker returns null`() {
        assertNull(ShareUrlParser.parse("https://example.com/openlist/local/a.txt"))
    }

    @Test
    fun `@s with no sid segment returns null`() {
        assertNull(ShareUrlParser.parse("https://example.com/@s"))
    }

    @Test
    fun `not a URL at all returns null`() {
        assertNull(ShareUrlParser.parse("not a url"))
        assertNull(ShareUrlParser.parse(""))
    }

    @Test
    fun `non-http scheme returns null`() {
        assertNull(ShareUrlParser.parse("ftp://example.com/@s/sid"))
    }
}
