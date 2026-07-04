package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.SubtitleRepository
import io.openlist.client.core.model.SubtitleCandidate
import io.openlist.client.core.model.SubtitleSource
import io.openlist.client.core.model.SubtitleSourceType
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.ObjResp
import io.openlist.client.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 6 (v0.4_EXECUTION_PLAN.md §11 S6-T1, P-402/P-414): real
 * implementation, following the exact instance-lookup / OpenListApi /
 * 401-invalidation pattern used by [PreviewRepositoryImpl.resolvePreview] and
 * [MediaRepositoryImpl.resolveMediaInternal].
 *
 * [findCandidates] leans entirely on the backend's own `fs/get` `related`
 * field (`FsGetResp.related`, server-side `filterRelated()` — same-prefix
 * files, extension-agnostic) rather than re-implementing prefix matching on
 * the client: this repository's own job is only to *filter* that list down
 * to subtitle-looking extensions (V-406 fallback: if that heuristic ever
 * proves unreliable, the manual "browse the directory" path in
 * [io.openlist.client.feature.preview.SubtitleSelector] is the deliberate
 * downgrade-to-manual escape hatch, not a change to this method's contract).
 */
@Singleton
class SubtitleRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
) : SubtitleRepository {

    override suspend fun findCandidates(instanceId: String, videoPath: String): ApiResult<List<SubtitleCandidate>> {
        val normalizedPath = OpenListPathCodec.normalize(videoPath)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> {
                val parent = OpenListPathCodec.parent(normalizedPath)
                val candidates = result.data.related
                    .filter { it.subtitleExtension() != null }
                    .map { it.toSubtitleCandidate(parent) }
                ApiResult.Success(candidates)
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    override suspend fun resolveSubtitle(instanceId: String, subtitlePath: String): ApiResult<SubtitleSource> {
        val normalizedPath = OpenListPathCodec.normalize(subtitlePath)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> {
                val resp = result.data
                val resolvedUrl = resp.rawUrl.ifBlank {
                    OpenListPathCodec.buildDownloadUrl(instance.baseUrl, normalizedPath, resp.sign).orEmpty()
                }
                if (resolvedUrl.isBlank()) {
                    ApiResult.Failure(DomainError.Unknown(null))
                } else {
                    ApiResult.Success(
                        SubtitleSource(
                            path = normalizedPath,
                            url = resolvedUrl,
                            format = normalizedPath.subtitleExtensionOrNull(),
                        ),
                    )
                }
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    private fun ObjResp.toSubtitleCandidate(parentDir: String): SubtitleCandidate = SubtitleCandidate(
        path = OpenListPathCodec.child(parentDir, name),
        name = name,
        // No reliable language signal from fs/get's `related` entries (a
        // plain file name/size/modified list) — left null rather than
        // guessed from e.g. a ".zh.srt"-style suffix that OpenList itself
        // makes no guarantee about.
        language = null,
        format = subtitleExtension(),
        source = SubtitleSourceType.AUTO_DISCOVERED,
    )

    /** Case-insensitive extension match against [SUBTITLE_EXTENSIONS], or
     * null if [ObjResp.name] doesn't look like a subtitle file. This is the
     * one piece of client-side filtering this method performs — the prefix
     * matching itself is already done server-side by `related`. */
    private fun ObjResp.subtitleExtension(): String? = name.subtitleExtensionOrNull()

    private fun String.subtitleExtensionOrNull(): String? {
        val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension.takeIf { it in SUBTITLE_EXTENSIONS }
    }

    private companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    }
}
