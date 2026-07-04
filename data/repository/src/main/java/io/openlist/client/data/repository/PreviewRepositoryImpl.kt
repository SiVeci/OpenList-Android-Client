package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
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
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.FsGetResp
import io.openlist.client.core.network.safeApiCall
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 2 (v0.4_EXECUTION_PLAN.md §11 S2-T1): [resolvePreview] is now a
 * real implementation, following the exact instance-lookup / OpenListApi /
 * 401-invalidation pattern used by FilesRepositoryImpl.getFile. Every call
 * re-fetches fs/get fresh — no local cache of the resolved URL is kept here
 * (V-401: signed /d/ /p/ URLs must not be reused across preview visits).
 * [loadText]/[loadMarkdown]/[refreshPreviewUrl] remain S1 stubs; their real
 * bodies (with `preview_cache` reads/writes) land in S3/S5.
 */
@Singleton
class PreviewRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
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

    override suspend fun loadText(instanceId: String, path: String, options: TextPreviewOptions): ApiResult<TextPreviewContent> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun loadMarkdown(instanceId: String, path: String): ApiResult<MarkdownPreviewContent> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun refreshPreviewUrl(instanceId: String, path: String): ApiResult<PreviewUrl> =
        ApiResult.Failure(DomainError.Unknown(null))

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
}
