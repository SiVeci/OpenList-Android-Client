package io.openlist.client.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Covers [readCapped] in isolation from OkHttp (S3-T1 DoD: "截断逻辑" and
 * "未截断" both need a unit test; this is that test, exercised against a
 * plain [ByteArrayInputStream] rather than a live/mocked network response —
 * [PreviewRepositoryImpl.streamReadCapped] itself just wires this pure
 * function's result into an [io.openlist.client.core.common.ApiResult],
 * which is exercised indirectly by [PreviewRepositoryImplTest]'s loadText
 * cases instead of re-testing the byte-counting logic a second time here).
 */
class ReadCappedTest {

    @Test
    fun `stream shorter than the cap is not truncated and returns every byte`() {
        val data = "hello world".toByteArray(Charsets.UTF_8)

        val (bytes, truncated) = readCapped(ByteArrayInputStream(data), capBytes = 1024)

        assertArrayEquals(data, bytes)
        assertFalse(truncated)
    }

    @Test
    fun `stream exactly the size of the cap is not truncated`() {
        val data = ByteArray(100) { it.toByte() }

        val (bytes, truncated) = readCapped(ByteArrayInputStream(data), capBytes = 100)

        assertEquals(100, bytes.size)
        assertFalse(truncated)
    }

    @Test
    fun `stream longer than the cap stops at the cap and is marked truncated`() {
        val data = ByteArray(2000) { (it % 256).toByte() }

        val (bytes, truncated) = readCapped(ByteArrayInputStream(data), capBytes = 512)

        assertEquals(512, bytes.size)
        assertArrayEquals(data.copyOfRange(0, 512), bytes)
        assertTrue(truncated)
    }

    @Test
    fun `cap spanning multiple 8KB chunks reads the correct total`() {
        // READ_CHUNK_SIZE is 8KB; this exercises the multi-chunk loop path
        // rather than a single read() call satisfying the whole cap.
        val capBytes = 20 * 1024L
        val data = ByteArray(30 * 1024) { (it % 256).toByte() }

        val (bytes, truncated) = readCapped(ByteArrayInputStream(data), capBytes)

        assertEquals(capBytes.toInt(), bytes.size)
        assertArrayEquals(data.copyOfRange(0, capBytes.toInt()), bytes)
        assertTrue(truncated)
    }

    @Test
    fun `empty stream returns empty bytes and is not truncated`() {
        val (bytes, truncated) = readCapped(ByteArrayInputStream(ByteArray(0)), capBytes = 512)

        assertEquals(0, bytes.size)
        assertFalse(truncated)
    }

    // ---- decodeUtf8StrippingBom (S3-T1, P-409) ----

    @Test
    fun `UTF-8 BOM is stripped before decoding`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "hello".toByteArray(Charsets.UTF_8)

        val text = decodeUtf8StrippingBom(bytes)

        assertEquals("hello", text)
        assertFalse(text.contains('﻿'))
    }

    @Test
    fun `text without a BOM decodes unchanged`() {
        val bytes = "no bom here".toByteArray(Charsets.UTF_8)

        val text = decodeUtf8StrippingBom(bytes)

        assertEquals("no bom here", text)
    }

    @Test
    fun `a byte sequence shorter than 3 bytes does not throw and is decoded as-is`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte())

        // Must not throw (e.g. an off-by-one range check on the BOM prefix
        // check) when the input is too short to even contain a full BOM.
        val text = decodeUtf8StrippingBom(bytes)

        assertEquals(String(bytes, Charsets.UTF_8), text)
    }
}
