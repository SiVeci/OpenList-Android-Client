package io.openlist.client.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.PreviewCacheDao
import io.openlist.client.core.database.entity.PreviewCacheEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.MarkdownPreviewContent
import io.openlist.client.core.model.PreviewFallback
import io.openlist.client.core.model.PreviewKind
import io.openlist.client.core.model.PreviewKindResolver
import io.openlist.client.core.model.PreviewOpenMode
import io.openlist.client.core.model.PreviewSource
import io.openlist.client.core.model.PreviewTarget
import io.openlist.client.core.model.PreviewUrl
import io.openlist.client.core.model.TextPreviewContent
import io.openlist.client.core.model.TextPreviewOptions
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.PreviewHttpClient
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.FsGetResp
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v0.4 Sprint 2 (v0.4_EXECUTION_PLAN.md §11 S2-T1): [resolvePreview] is now a
 * real implementation, following the exact instance-lookup / OpenListApi /
 * 401-invalidation pattern used by FilesRepositoryImpl.getFile. Every call
 * re-fetches fs/get fresh — no local cache of the resolved URL is kept here
 * (V-401: signed /d/ /p/ URLs must not be reused across preview visits).
 *
 * v0.4 Sprint 3 (§11 S3-T1/T2/T3/T4): [loadText]/[loadMarkdown] are now real
 * implementations backed by [previewCacheDao] (metadata) plus a file under
 * `context.cacheDir/preview/<instanceId>/` (body) — see [streamReadCapped]
 * for the capped-read/truncation mechanics (P-408) and [invalidate]/
 * [invalidateByPrefix] for cache-maintenance hooks called by
 * FileOperationRepositoryImpl/UploadWorker after a write succeeds.
 * [refreshPreviewUrl] remains an S1 stub; its real body lands in S5.
 */
@Singleton
class PreviewRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val previewCacheDao: PreviewCacheDao,
    private val previewHttpClient: PreviewHttpClient,
    private val json: Json,
    @ApplicationContext private val context: Context,
) : PreviewRepository {

    override suspend fun resolvePreview(instanceId: String, path: String): ApiResult<PreviewTarget> {
        val normalizedPath = OpenListPathCodec.normalize(path)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.toPreviewTarget(instanceId, normalizedPath, instance.baseUrl),
            )
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    override suspend fun loadText(instanceId: String, path: String, options: TextPreviewOptions): ApiResult<TextPreviewContent> {
        val normalizedPath = OpenListPathCodec.normalize(path)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)

        val fresh = when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                return result
            }
        }

        if (fresh.size > TEXT_PREVIEW_HARD_CEILING_BYTES) {
            return ApiResult.Failure(DomainError.PreviewTooLarge)
        }

        if (!options.forceRefresh) {
            val cached = findFreshCacheRow(instanceId, normalizedPath, KIND_TEXT, fresh)
            if (cached != null) {
                val fileBytes = runCatching { File(cached.localFilePath).readBytes() }.getOrNull()
                if (fileBytes != null) {
                    val text = decodeUtf8StrippingBom(fileBytes)
                    return ApiResult.Success(
                        TextPreviewContent(
                            path = normalizedPath,
                            text = text,
                            encoding = "UTF-8",
                            isTruncated = fileBytes.size.toLong() < cached.sizeBytes,
                            totalBytes = fresh.size,
                        ),
                    )
                }
            }
        }

        val url = fresh.rawUrl.ifBlank { OpenListPathCodec.buildDownloadUrl(instance.baseUrl, normalizedPath, fresh.sign).orEmpty() }
        if (url.isBlank()) return ApiResult.Failure(DomainError.Unknown(null))

        val cap = options.maxBytesOverride ?: TEXT_PREVIEW_SOFT_CAP_BYTES
        val streamResult = streamReadCapped(instanceId, url, cap)
        val (bytes, isTruncated) = when (streamResult) {
            is ApiResult.Success -> streamResult.data
            is ApiResult.Failure -> return streamResult
        }

        val text = decodeUtf8StrippingBom(bytes)
        persistCacheBody(instanceId, normalizedPath, KIND_TEXT, bytes, fresh)

        return ApiResult.Success(
            TextPreviewContent(
                path = normalizedPath,
                text = text,
                encoding = "UTF-8",
                isTruncated = isTruncated,
                totalBytes = fresh.size,
            ),
        )
    }

    override suspend fun loadMarkdown(instanceId: String, path: String, forceRefresh: Boolean): ApiResult<MarkdownPreviewContent> {
        val normalizedPath = OpenListPathCodec.normalize(path)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)

        val fresh = when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                return result
            }
        }

        if (fresh.size > TEXT_PREVIEW_HARD_CEILING_BYTES) {
            return ApiResult.Failure(DomainError.PreviewTooLarge)
        }

        val basePath = OpenListPathCodec.parent(normalizedPath)

        if (!forceRefresh) {
            val cached = findFreshCacheRow(instanceId, normalizedPath, KIND_MARKDOWN, fresh)
            if (cached != null) {
                val fileBytes = runCatching { File(cached.localFilePath).readBytes() }.getOrNull()
                if (fileBytes != null) {
                    val text = decodeUtf8StrippingBom(fileBytes)
                    return ApiResult.Success(
                        MarkdownPreviewContent(
                            path = normalizedPath,
                            rawMarkdown = text,
                            basePath = basePath,
                            isTruncated = fileBytes.size.toLong() < cached.sizeBytes,
                        ),
                    )
                }
            }
        }

        val url = fresh.rawUrl.ifBlank { OpenListPathCodec.buildDownloadUrl(instance.baseUrl, normalizedPath, fresh.sign).orEmpty() }
        if (url.isBlank()) return ApiResult.Failure(DomainError.Unknown(null))

        val streamResult = streamReadCapped(instanceId, url, TEXT_PREVIEW_SOFT_CAP_BYTES)
        val (bytes, isTruncated) = when (streamResult) {
            is ApiResult.Success -> streamResult.data
            is ApiResult.Failure -> return streamResult
        }

        val text = decodeUtf8StrippingBom(bytes)
        persistCacheBody(instanceId, normalizedPath, KIND_MARKDOWN, bytes, fresh)

        return ApiResult.Success(
            MarkdownPreviewContent(
                path = normalizedPath,
                rawMarkdown = text,
                basePath = basePath,
                isTruncated = isTruncated,
            ),
        )
    }

    override suspend fun refreshPreviewUrl(instanceId: String, path: String): ApiResult<PreviewUrl> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun invalidate(instanceId: String, path: String) {
        val normalizedPath = OpenListPathCodec.normalize(path)
        runCatching {
            val rows = previewCacheDao.getByInstanceAndPath(instanceId, normalizedPath)
            rows.forEach { row -> runCatching { File(row.localFilePath).delete() } }
            previewCacheDao.deleteByInstanceAndPath(instanceId, normalizedPath)
        }
    }

    override suspend fun invalidateByPrefix(instanceId: String, pathPrefix: String) {
        val normalizedPrefix = OpenListPathCodec.normalize(pathPrefix)
        runCatching {
            val rows = previewCacheDao.getByPathPrefix(instanceId, normalizedPrefix)
            rows.forEach { row -> runCatching { File(row.localFilePath).delete() } }
            previewCacheDao.deleteByPathPrefix(instanceId, normalizedPrefix)
        }
    }

    /** Looks up a not-yet-expired `preview_cache` row of [kind] whose
     * [PreviewCacheEntity.lastModified]/[PreviewCacheEntity.sizeBytes]
     * still match the freshly-fetched fs/get metadata (§10.1: "命中前校验
     * lastModified/size，元信息不一致即失效"). Returns null on any mismatch,
     * meaning "don't trust the cache, re-fetch from network". */
    private suspend fun findFreshCacheRow(
        instanceId: String,
        path: String,
        kind: String,
        fresh: FsGetResp,
    ): PreviewCacheEntity? {
        val now = System.currentTimeMillis()
        val freshModified = parseTimestamp(fresh.modified)
        return previewCacheDao.getByInstanceAndPath(instanceId, path)
            .firstOrNull { row ->
                val expiresAt = row.expiresAt
                row.kind == kind &&
                    row.lastModified == freshModified &&
                    row.sizeBytes == fresh.size &&
                    (expiresAt == null || expiresAt > now)
            }
    }

    /** Writes [bytes] to a stable, filesystem-safe file under
     * `context.cacheDir/preview/<instanceId>/` and upserts the matching
     * `preview_cache` metadata row (S3-T2 step 4). The file name is a
     * SHA-256 hex digest of `"$instanceId:$path:$kind"` — the same string is
     * stored verbatim in [PreviewCacheEntity.cacheKey] so the two stay
     * trivially derivable from each other (the digest exists only because a
     * raw OpenList path contains '/' and can't be a single file name). */
    private suspend fun persistCacheBody(instanceId: String, path: String, kind: String, bytes: ByteArray, fresh: FsGetResp) {
        runCatching {
            val cacheKey = "$instanceId:$path:$kind"
            val fileName = sha256Hex(cacheKey)
            val dir = File(context.cacheDir, "preview/$instanceId").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeBytes(bytes)

            val now = System.currentTimeMillis()
            previewCacheDao.upsert(
                PreviewCacheEntity(
                    id = fileName,
                    instanceId = instanceId,
                    path = path,
                    kind = kind,
                    mimeType = null,
                    lastModified = parseTimestamp(fresh.modified),
                    cacheKey = cacheKey,
                    localFilePath = file.absolutePath,
                    sizeBytes = fresh.size,
                    etag = null,
                    expiresAt = now + PREVIEW_CACHE_TTL_MILLIS,
                    cachedAt = now,
                ),
            )
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Streams [url] via [previewHttpClient], stopping once [capBytes] bytes
     * have been read (S3-T1, P-408). Returns the bytes read plus whether the
     * stream had more data beyond the cap ("truncated" — a *successful*
     * result, not an error).
     *
     * Chunked manual reads (not `InputStream.readNBytes`, a Java 11+ API)
     * into a [ByteArrayOutputStream], same suspendCancellableCoroutine +
     * `call.cancel()`-on-cancellation wrapper as [UploadWorker.executeCancellable]
     * so a cleared ViewModel's coroutine cancellation actually aborts the
     * underlying OkHttp call instead of leaking it.
     */
    private suspend fun streamReadCapped(instanceId: String, url: String, capBytes: Long): ApiResult<Pair<ByteArray, Boolean>> {
        val call = previewHttpClient.client.newCall(Request.Builder().url(url).get().build())
        val response = try {
            executeCancellable(call)
        } catch (io: IOException) {
            return ApiResult.Failure(DomainError.NetworkUnavailable)
        }

        return response.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) sessionManager.invalidate(instanceId)
                return@use ApiResult.Failure(mapHttpError(resp))
            }
            val body = resp.body ?: return@use ApiResult.Success(ByteArray(0) to false)
            try {
                val (bytes, truncated) = readCapped(body.byteStream(), capBytes)
                ApiResult.Success(bytes to truncated)
            } catch (io: IOException) {
                ApiResult.Failure(DomainError.NetworkUnavailable)
            }
        }
    }

    private fun mapHttpError(response: Response): DomainError = when (response.code) {
        401 -> DomainError.Unauthorized
        403 -> DomainError.Forbidden
        404 -> DomainError.NotFound
        in 500..599 -> DomainError.ServerError
        else -> DomainError.OpenListError(code = response.code, message = parseErrorMessage(response) ?: "请求失败")
    }

    /** Same envelope-parsing rule as [UploadWorker.parseErrorMessage]: even a
     * non-2xx response body is still the standard `{code,message,data}`
     * envelope, never plain text. */
    private fun parseErrorMessage(response: Response): String? = runCatching {
        val body = response.body?.string() ?: return null
        json.decodeFromString<ApiResponse<JsonElement?>>(body).message.takeUnless { it.isBlank() }
    }.getOrNull()

    private suspend fun executeCancellable(call: okhttp3.Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        try {
            cont.resume(call.execute())
        } catch (e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    /**
     * Directories should never reach `resolvePreview` — the entry points
     * (file list / file detail) only route previewable *files* here (S2-T4).
     * This branch is a defensive fallback so a stray call never crashes:
     * it resolves to [PreviewOpenMode.UNSUPPORTED] with no source/fallbacks
     * rather than throwing.
     */
    private fun FsGetResp.toPreviewTarget(instanceId: String, path: String, instanceBaseUrl: String): PreviewTarget {
        if (isDir) {
            return PreviewTarget(
                instanceId = instanceId,
                path = path,
                name = name,
                mimeType = null,
                kind = PreviewKind.UNKNOWN,
                openMode = PreviewOpenMode.UNSUPPORTED,
                size = size,
                modifiedAt = parseTimestamp(modified),
                source = null,
                fallbacks = emptyList(),
            )
        }

        val kind = PreviewKindResolver.resolve(name)
        // raw_url already accounts for proxy/direct/signing server-side; only
        // fall back to building a signed /d/ URL if the server sent none (same
        // rule as FilesRepositoryImpl.toDomainDetail).
        val resolvedUrl = rawUrl.ifBlank {
            OpenListPathCodec.buildDownloadUrl(instanceBaseUrl, path, sign).orEmpty()
        }
        // V-402: /d/ and /p/ URLs carry their own `sign` query parameter for
        // auth, they don't need an Authorization header — headersRequired is
        // fixed false until a concrete counter-example surfaces.
        val source = if (resolvedUrl.isNotBlank()) PreviewSource.RemoteUrl(resolvedUrl, headersRequired = false) else null
        val (openMode, fallbacks) = openModeAndFallbacksFor(kind)

        return PreviewTarget(
            instanceId = instanceId,
            path = path,
            name = name,
            mimeType = null,
            kind = kind,
            openMode = openMode,
            size = size,
            modifiedAt = parseTimestamp(modified),
            source = source,
            fallbacks = fallbacks,
        )
    }

    private fun openModeAndFallbacksFor(kind: PreviewKind): Pair<PreviewOpenMode, List<PreviewFallback>> = when (kind) {
        PreviewKind.IMAGE -> PreviewOpenMode.IN_APP_IMAGE to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP)
        PreviewKind.TEXT -> PreviewOpenMode.IN_APP_TEXT to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP)
        PreviewKind.MARKDOWN -> PreviewOpenMode.IN_APP_MARKDOWN to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP)
        PreviewKind.VIDEO -> PreviewOpenMode.IN_APP_VIDEO to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP)
        PreviewKind.AUDIO -> PreviewOpenMode.IN_APP_AUDIO to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP)
        PreviewKind.PDF -> PreviewOpenMode.EXTERNAL_APP to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.WEB)
        PreviewKind.OFFICE -> PreviewOpenMode.EXTERNAL_APP to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.WEB)
        PreviewKind.UNKNOWN -> PreviewOpenMode.UNSUPPORTED to listOf(PreviewFallback.DOWNLOAD, PreviewFallback.EXTERNAL_APP, PreviewFallback.WEB)
    }

    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }

    companion object {
        /** P-408 soft cap: normal in-app text/markdown reads stop here and
         * report `isTruncated = true` as a *successful* result. */
        const val TEXT_PREVIEW_SOFT_CAP_BYTES = 512 * 1024L

        /** P-408 hard ceiling: fs/get's reported file size beyond this means
         * no network read is attempted at all — [DomainError.PreviewTooLarge]
         * short-circuits immediately. */
        const val TEXT_PREVIEW_HARD_CEILING_BYTES = 20 * 1024 * 1024L

        private const val PREVIEW_CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L

        private const val KIND_TEXT = "TEXT"
        private const val KIND_MARKDOWN = "MARKDOWN"
    }
}

/**
 * Pure, network-free chunked reader used by [PreviewRepositoryImpl.streamReadCapped]
 * (S3-T1). Reads [input] in [READ_CHUNK_SIZE]-byte chunks into a
 * [ByteArrayOutputStream], stopping as soon as [capBytes] bytes have been
 * accumulated (closing [input] before returning) rather than reading past
 * it. A top-level `internal` function (not a method on
 * [PreviewRepositoryImpl]) specifically so unit tests can exercise the
 * truncation/BOM-adjacent logic against a plain [java.io.InputStream]/
 * [ByteArray] without touching OkHttp's `Call`/`Response` at all.
 *
 * Returns the bytes read (never more than [capBytes]) paired with whether
 * the stream had more data beyond the cap.
 */
internal fun readCapped(input: java.io.InputStream, capBytes: Long): Pair<ByteArray, Boolean> {
    input.use { stream ->
        val buffer = ByteArray(READ_CHUNK_SIZE)
        val output = ByteArrayOutputStream()
        var truncated = false
        while (true) {
            val remaining = capBytes - output.size()
            if (remaining <= 0) {
                // Only truncated if the stream actually has more to give —
                // a file whose size lands exactly on the cap should not be
                // reported as truncated.
                truncated = stream.read(buffer, 0, 1) != -1
                break
            }
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = stream.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray() to truncated
    }
}

private const val READ_CHUNK_SIZE = 8 * 1024

/** UTF-8 decode with a leading BOM (0xEF 0xBB 0xBF) stripped first (S3-T1,
 * P-409) — no other encoding is auto-detected. Top-level `internal` for the
 * same reason as [readCapped]: directly unit-testable against a plain
 * [ByteArray] without constructing a [PreviewRepositoryImpl]. */
internal fun decodeUtf8StrippingBom(bytes: ByteArray): String {
    val hasBom = bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    val content = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
    return String(content, Charsets.UTF_8)
}
