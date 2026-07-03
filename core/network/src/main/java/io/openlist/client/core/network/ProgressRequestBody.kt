package io.openlist.client.core.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.InputStream

/**
 * Streams [inputStream] straight into the OkHttp write pipe in fixed-size
 * chunks — the whole file is never buffered in memory (v0.2_EXECUTION_PLAN.md
 * §13.3/§14.2) — reporting cumulative bytes written after every chunk.
 * [contentLength] may be `-1` for an unknown-size stream (chunked transfer);
 * the caller is responsible for closing [inputStream] via `use`, this class
 * does not own it beyond the single `writeTo` call OkHttp makes.
 */
class ProgressRequestBody(
    private val inputStream: InputStream,
    private val mediaType: MediaType?,
    private val contentLength: Long,
    private val onProgress: (bytesWritten: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = contentLength

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        inputStream.source().use { source ->
            var uploaded = 0L
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, CHUNK_SIZE)
                if (read == -1L) break
                sink.write(buffer, read)
                uploaded += read
                onProgress(uploaded)
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 64L * 1024
    }
}
