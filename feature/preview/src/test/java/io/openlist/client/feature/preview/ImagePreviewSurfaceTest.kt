package io.openlist.client.feature.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Locks down [buildPreviewCacheKey]'s composition (v0.4_EXECUTION_PLAN.md
 * §11 S2-T3, R-409): the cache key must vary with instanceId, path and
 * modifiedAt independently — a signed preview URL's `sign` query parameter
 * changes on every re-resolve, so the cache key can't be based on the URL
 * itself, but it must still change whenever the underlying file does.
 */
class ImagePreviewSurfaceTest {

    @Test
    fun `same inputs produce the same key`() {
        val key1 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 1000L)
        val key2 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 1000L)

        assertEquals(key1, key2)
    }

    @Test
    fun `different instanceId produces a different key`() {
        val key1 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 1000L)
        val key2 = buildPreviewCacheKey("inst-2", "/a/photo.jpg", 1000L)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `different path produces a different key`() {
        val key1 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 1000L)
        val key2 = buildPreviewCacheKey("inst-1", "/b/photo.jpg", 1000L)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `different modifiedAt produces a different key`() {
        val key1 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 1000L)
        val key2 = buildPreviewCacheKey("inst-1", "/a/photo.jpg", 2000L)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `null modifiedAt falls back to zero rather than crashing`() {
        val key = buildPreviewCacheKey("inst-1", "/a/photo.jpg", null)

        assertEquals("inst-1:/a/photo.jpg:0", key)
    }
}
