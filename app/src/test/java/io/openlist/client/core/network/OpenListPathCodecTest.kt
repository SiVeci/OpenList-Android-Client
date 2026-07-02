package io.openlist.client.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.1_PRD §12.2 technical acceptance requires Chinese-path and space-path
 * support; this is the "path encoding test set" called for in that section
 * and in v0.1_EXECUTION_PLAN.md §11 (Sprint 6 stabilization).
 */
class OpenListPathCodecTest {

    @Test
    fun `normalize collapses root variants`() {
        assertEquals("/", OpenListPathCodec.normalize(""))
        assertEquals("/", OpenListPathCodec.normalize("   "))
        assertEquals("/", OpenListPathCodec.normalize("/"))
        assertEquals("/", OpenListPathCodec.normalize("///"))
    }

    @Test
    fun `normalize adds leading slash and strips trailing slash`() {
        assertEquals("/a/b", OpenListPathCodec.normalize("a/b"))
        assertEquals("/a/b", OpenListPathCodec.normalize("/a/b/"))
        assertEquals("/a/b", OpenListPathCodec.normalize("/a//b"))
    }

    @Test
    fun `normalize preserves chinese, spaces and symbols unencoded`() {
        // fs/list and fs/get send `path` as a plain JSON string field — never
        // URL-encoded, per this class's own kdoc.
        assertEquals("/中文目录/文件.txt", OpenListPathCodec.normalize("/中文目录/文件.txt"))
        assertEquals("/my folder/file name.txt", OpenListPathCodec.normalize("/my folder/file name.txt"))
        assertEquals("/a+b/c#d!/e", OpenListPathCodec.normalize("/a+b/c#d!/e"))
    }

    @Test
    fun `parent of root is root, parent of top-level entry is root`() {
        assertEquals("/", OpenListPathCodec.parent("/"))
        assertEquals("/", OpenListPathCodec.parent("/a"))
        assertEquals("/a", OpenListPathCodec.parent("/a/b"))
        assertEquals("/中文", OpenListPathCodec.parent("/中文/子目录"))
    }

    @Test
    fun `name extracts the last segment`() {
        assertEquals("", OpenListPathCodec.name("/"))
        assertEquals("b", OpenListPathCodec.name("/a/b"))
        assertEquals("文件 名.txt", OpenListPathCodec.name("/中文 目录/文件 名.txt"))
    }

    @Test
    fun `child joins a name onto a parent directory`() {
        assertEquals("/文件.txt", OpenListPathCodec.child("/", "文件.txt"))
        assertEquals("/a/b", OpenListPathCodec.child("/a", "b"))
        assertEquals("/a/b", OpenListPathCodec.child("/a", "/b/")) // stray slashes on the child are trimmed
    }

    @Test
    fun `segments splits into breadcrumb parts excluding root`() {
        assertEquals(emptyList<String>(), OpenListPathCodec.segments("/"))
        assertEquals(listOf("a", "b"), OpenListPathCodec.segments("/a/b"))
        assertEquals(listOf("中文", "子目录"), OpenListPathCodec.segments("/中文/子目录"))
    }

    @Test
    fun `pathForSegment rebuilds a truncated path for breadcrumb taps`() {
        assertEquals("/", OpenListPathCodec.pathForSegment("/a/b/c", count = 0))
        assertEquals("/a", OpenListPathCodec.pathForSegment("/a/b/c", count = 1))
        assertEquals("/a/b", OpenListPathCodec.pathForSegment("/a/b/c", count = 2))
        // Beyond the real segment count coerces to the full path rather than throwing.
        assertEquals("/a/b/c", OpenListPathCodec.pathForSegment("/a/b/c", count = 99))
    }

    @Test
    fun `buildDownloadUrl percent-encodes chinese and space segments`() {
        val url = OpenListPathCodec.buildDownloadUrl(
            baseUrl = "https://example.com",
            path = "/中文 目录/文件 名.txt",
            sign = "abc123",
        )
        assertNotNull(url)
        val parsed = url!!.toHttpUrl()
        assertEquals(listOf("d", "中文 目录", "文件 名.txt"), parsed.pathSegments)
        assertEquals("abc123", parsed.queryParameter("sign"))
    }

    @Test
    fun `buildDownloadUrl appends after an existing sub-path deployment`() {
        val url = OpenListPathCodec.buildDownloadUrl(
            baseUrl = "https://example.com/openlist",
            path = "/a/b.txt",
            sign = "",
        )
        assertNotNull(url)
        val parsed = url!!.toHttpUrl()
        assertEquals(listOf("openlist", "d", "a", "b.txt"), parsed.pathSegments)
        assertNull(parsed.queryParameter("sign")) // empty sign is omitted, not sent as ""
    }

    @Test
    fun `buildDownloadUrl returns null for an invalid base url`() {
        assertNull(OpenListPathCodec.buildDownloadUrl("not a url", "/a", ""))
    }
}
