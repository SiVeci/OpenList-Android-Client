package io.openlist.client.documents

import android.os.ProxyFileDescriptorCallback
import io.openlist.client.core.domain.SystemDocumentReadHandle
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Bridges framework Proxy FD reads to the repository's bounded remote reader. */
internal class SystemDocumentProxyFileCallback(
    private val handle: SystemDocumentReadHandle,
) : ProxyFileDescriptorCallback() {
    private val released = AtomicBoolean(false)

    override fun onGetSize(): Long = handle.sizeBytes

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (released.get() || size <= 0 || offset < 0) return 0
        val requested = minOf(size, data.size)
        val bytes = try {
            runBlocking { handle.read(offset, requested) }
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("Unable to read system document", error)
        }
        bytes.copyInto(data, endIndex = minOf(bytes.size, data.size))
        return minOf(bytes.size, data.size)
    }

    override fun onRelease() {
        if (released.compareAndSet(false, true)) handle.close()
    }

    fun releaseOnCancellation() = onRelease()
}
