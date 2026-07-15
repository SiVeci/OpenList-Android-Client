package io.openlist.client.documents

import android.os.ProxyFileDescriptorCallback
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.SystemDocumentWriteHandle
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** P4 local-only Proxy FD writer. Remote commit is intentionally not wired here. */
internal class SystemDocumentProxyWriteCallback(
    private val handle: SystemDocumentWriteHandle,
) : ProxyFileDescriptorCallback() {
    private val released = AtomicBoolean(false)

    override fun onGetSize(): Long = handle.sizeBytes

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int = guarded {
        handle.read(offset, minOf(size, data.size)).also { it.copyInto(data) }.size
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int = guarded {
        handle.write(offset, data.copyOf(minOf(size, data.size)))
    }

    override fun onFsync() {
        guarded {
            when (handle.fsync()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> throw IOException("Local draft fsync failed")
            }
        }
    }

    override fun onRelease() {
        if (released.compareAndSet(false, true)) handle.close()
    }

    private fun <T> guarded(block: suspend () -> T): T {
        if (released.get()) throw IOException("Draft is closed")
        return try {
            runBlocking { block() }
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("Local draft I/O failed", error)
        }
    }
}
