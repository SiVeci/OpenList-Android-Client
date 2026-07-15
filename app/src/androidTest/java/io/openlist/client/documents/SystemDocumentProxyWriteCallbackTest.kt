package io.openlist.client.documents

import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.test.platform.app.InstrumentationRegistry
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.SystemDocumentWriteHandle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class SystemDocumentProxyWriteCallbackTest {
    @Test
    fun storageManagerProxyFdWritesAtOffsetsAndFsyncsLocalHandle() {
        val handle = FakeWriteHandle("abcdef".encodeToByteArray())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val descriptor = context.getSystemService(StorageManager::class.java).openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_WRITE,
            SystemDocumentProxyWriteCallback(handle),
            Handler(Looper.getMainLooper()),
        )

        FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.position(2)
            assertEquals(2, channel.write(ByteBuffer.wrap("XY".encodeToByteArray())))
            channel.force(true)
        }
        descriptor.close()

        assertArrayEquals("abXYef".encodeToByteArray(), handle.bytes)
        assertEquals(1, handle.fsyncCount)
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (!handle.closed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20L)
        assertTrue(handle.closed)
    }

    private class FakeWriteHandle(initial: ByteArray) : SystemDocumentWriteHandle {
        var bytes = initial
        var fsyncCount = 0
        var closed = false
        override val sizeBytes: Long get() = bytes.size.toLong()

        override suspend fun read(offset: Long, size: Int): ByteArray =
            bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + size))

        override suspend fun write(offset: Long, value: ByteArray): Int {
            val required = (offset + value.size).toInt()
            if (required > bytes.size) bytes = bytes.copyOf(required)
            value.copyInto(bytes, offset.toInt())
            return value.size
        }

        override suspend fun truncate(size: Long) { bytes = bytes.copyOf(size.toInt()) }
        override suspend fun fsync(): ApiResult<Unit> { fsyncCount += 1; return ApiResult.Success(Unit) }
        override fun close() { closed = true }
    }
}
