package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.network.SystemDocumentHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/** Strict 206 reader with streamed 200 fallback; never loads a full file into JVM memory. */
@Singleton
class SystemDocumentReadCoordinator @Inject constructor(
    private val httpClient: SystemDocumentHttpClient,
    private val spaceManager: SystemDocumentSpaceManager,
) {
    fun open(source: SystemDocumentReadSource): SystemDocumentReadSession = SystemDocumentReadSession(source)

    suspend fun read(session: SystemDocumentReadSession, offset: Long, requestedBytes: Int): ApiResult<ByteArray> =
        withContext(Dispatchers.IO) {
            if (offset < 0 || requestedBytes <= 0 || requestedBytes > MAX_RANGE_BYTES) {
                return@withContext ApiResult.Failure(DomainError.PathEncodeError)
            }
            if (offset >= session.source.sizeBytes) {
                return@withContext ApiResult.Success(ByteArray(0))
            }
            val boundedBytes = minOf(
                requestedBytes.toLong(),
                session.source.sizeBytes - offset,
            ).toInt()
            session.materializedFile?.let { return@withContext readCached(it, offset, requestedBytes) }
            val call = httpClient.newRangeCall(
                session.source.rawUrl,
                offset,
                boundedBytes,
                session.source.instanceBaseUrl,
                session.source.token,
            )
            session.activeCall = call
            try {
                call.execute().use { response ->
                    if (httpClient.isTrustedRangeResponse(response, offset, boundedBytes, session.source.sizeBytes)) {
                        val bytes = response.body?.source()?.readByteArray() ?: ByteArray(0)
                        return@withContext if (bytes.size == boundedBytes) ApiResult.Success(bytes)
                        else ApiResult.Failure(DomainError.ServerError)
                    }
                    if (response.code != 200) return@withContext ApiResult.Failure(DomainError.ServerError)
                    materializeAndRead(session, response.body?.byteStream(), offset, boundedBytes)
                }
            } catch (t: Throwable) {
                ApiResult.Failure(DomainError.Unknown(t))
            } finally {
                session.activeCall = null
            }
        }

    suspend fun close(session: SystemDocumentReadSession) {
        session.activeCall?.cancel()
        session.materializedFile?.delete()
        session.reservation?.let { spaceManager.releaseReadReservation(it) }
        session.materializedFile = null
        session.reservation = null
    }

    private suspend fun materializeAndRead(
        session: SystemDocumentReadSession,
        input: java.io.InputStream?,
        offset: Long,
        requestedBytes: Int,
    ): ApiResult<ByteArray> {
        input ?: return ApiResult.Failure(DomainError.ServerError)
        val reservation = spaceManager.reserveRead(session.source.sizeBytes)
            ?: return ApiResult.Failure(DomainError.Unknown(IllegalStateException("insufficient private storage")))
        val file = spaceManager.newReadCacheFile()
        return try {
            var copied = 0L
            input.use { source ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > session.source.sizeBytes) return ApiResult.Failure(DomainError.ServerError)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (copied != session.source.sizeBytes) return ApiResult.Failure(DomainError.ServerError)
            session.materializedFile = file
            session.reservation = reservation
            readCached(file, offset, requestedBytes)
        } finally {
            if (session.materializedFile == null) {
                file.delete()
                spaceManager.releaseReadReservation(reservation)
            }
        }
    }

    private fun readCached(file: File, offset: Long, requestedBytes: Int): ApiResult<ByteArray> = runCatching {
        RandomAccessFile(file, "r").use { input ->
            if (offset >= input.length()) return ApiResult.Success(ByteArray(0))
            input.seek(offset)
            val result = ByteArray(minOf(requestedBytes.toLong(), input.length() - offset).toInt())
            input.readFully(result)
            ApiResult.Success(result)
        }
    }.getOrElse { ApiResult.Failure(DomainError.Unknown(it)) }

    companion object {
        const val MAX_RANGE_BYTES = 256 * 1024
        private const val COPY_BUFFER_BYTES = 32 * 1024
    }
}

class SystemDocumentReadSession internal constructor(
    internal val source: SystemDocumentReadSource,
) {
    internal var materializedFile: File? = null
    internal var reservation: SystemDocumentSpaceReservation? = null
    @Volatile internal var activeCall: Call? = null
}
