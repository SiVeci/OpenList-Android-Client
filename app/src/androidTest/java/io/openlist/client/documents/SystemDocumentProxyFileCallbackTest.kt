package io.openlist.client.documents

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.storage.StorageManager
import io.openlist.client.core.domain.SystemDocumentReadHandle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileInputStream
import java.nio.ByteBuffer

class SystemDocumentProxyFileCallbackTest {
    @Test
    fun boundedReadAndReleaseDelegateToHandle() {
        val handle = FakeReadHandle("abcdef".encodeToByteArray())
        val callback = SystemDocumentProxyFileCallback(handle)
        val destination = ByteArray(4)

        assertEquals(6L, callback.onGetSize())
        assertEquals(4, callback.onRead(1, 4, destination))
        assertArrayEquals("bcde".encodeToByteArray(), destination)

        callback.onRelease()
        callback.onRelease()
        assertTrue(handle.closed)
        assertEquals(1, handle.closeCount)
        assertEquals(0, callback.onRead(0, 1, ByteArray(1)))
    }

    @Test
    fun storageManagerProxyFdSupportsSeekReadAndClose() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val handle = FakeReadHandle("abcdef".encodeToByteArray())
        val descriptor = context.getSystemService(StorageManager::class.java).openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            SystemDocumentProxyFileCallback(handle),
            Handler(Looper.getMainLooper()),
        )

        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.position(2)
            val bytes = ByteBuffer.allocate(3)
            assertEquals(3, channel.read(bytes))
            assertArrayEquals("cde".encodeToByteArray(), bytes.array())
        }
        descriptor.close()

        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (!handle.closed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20L)
        assertTrue("onRelease should close the repository handle", handle.closed)
    }

    private class FakeReadHandle(
        private val content: ByteArray,
    ) : SystemDocumentReadHandle {
        var closed = false
        var closeCount = 0

        override val sizeBytes: Long = content.size.toLong()

        override suspend fun read(offset: Long, size: Int): ByteArray {
            if (offset >= content.size) return ByteArray(0)
            return content.copyOfRange(offset.toInt(), minOf(content.size, offset.toInt() + size))
        }

        override fun close() {
            closed = true
            closeCount += 1
        }
    }
}
