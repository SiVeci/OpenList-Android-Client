package io.openlist.client.core.network

import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.Protocol
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Raw-url reader and synchronous stage uploader for DocumentsProvider. It is
 * intentionally isolated from the normal AuthInterceptor: direct/signed URLs
 * may be on another origin, where forwarding Authorization is unsafe.
 */
@Singleton
class SystemDocumentHttpClient private constructor(
    val client: OkHttpClient,
) {
    @javax.inject.Inject
    constructor() : this(defaultClient())

    constructor(testClient: OkHttpClient, testOnly: Unit) : this(testClient)
    fun newRangeCall(
        url: String,
        offset: Long,
        length: Int,
        instanceBaseUrl: String,
        token: String?,
    ): Call {
        require(offset >= 0 && length > 0) { "invalid range" }
        val endInclusive = offset + length - 1L
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$offset-$endInclusive")
            .apply { buildScopedHttpHeaders(url, instanceBaseUrl, token).forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request)
    }

    /** Opens a raw content stream while applying the same origin-scoped header rule as Range reads. */
    fun newContentCall(
        url: String,
        instanceBaseUrl: String,
        token: String?,
    ): Call {
        val request = Request.Builder()
            .url(url)
            .apply { buildScopedHttpHeaders(url, instanceBaseUrl, token).forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request)
    }

    /** A response is trustworthy only when its status and Content-Range match exactly. */
    fun isTrustedRangeResponse(response: Response, requestedOffset: Long, requestedLength: Int, expectedTotalSize: Long?): Boolean {
        if (response.code != 206 || requestedOffset < 0 || requestedLength <= 0) return false
        val header = response.header("Content-Range") ?: return false
        val match = CONTENT_RANGE.matchEntire(header.trim()) ?: return false
        val start = match.groupValues[1].toLongOrNull() ?: return false
        val end = match.groupValues[2].toLongOrNull() ?: return false
        val total = match.groupValues[3].toLongOrNull() ?: return false
        val requestedEnd = requestedOffset + requestedLength - 1L
        return start == requestedOffset && end == requestedEnd && end >= start &&
            (expectedTotalSize == null || total == expectedTotalSize)
    }

    /** Uploads a stage object only with the non-destructive OpenList contract. */
    fun uploadStage(
        url: String,
        encodedRemotePath: String,
        input: InputStream,
        contentLength: Long,
        mimeType: String?,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        require(contentLength >= 0) { "contentLength must be known for a stage upload" }
        val body = InputStreamRequestBody(input, contentLength, mimeType)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("File-Path", encodedRemotePath)
            .header("As-Task", "false")
            .header("Overwrite", "false")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute()
    }

    private class InputStreamRequestBody(
        private val input: InputStream,
        private val size: Long,
        mimeType: String?,
    ) : RequestBody() {
        private val mediaType = mimeType?.toMediaTypeOrNull()
        override fun contentType(): MediaType? = mediaType
        override fun contentLength(): Long = size
        override fun writeTo(sink: BufferedSink) {
            input.use { source -> sink.writeAll(source.source()) }
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Some OpenList reverse-proxy deployments accept ordinary JSON
            // traffic over HTTP/2 but reset streaming PUT bodies after h2
            // negotiation. System-document reads and commits must share the
            // same transport profile so a stage verified by fs/get is also
            // readable through the proxy FD. This narrows only this isolated
            // client to HTTP/1.1; TLS validation and scoped headers remain
            // unchanged.
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val CONTENT_RANGE = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    }
}
