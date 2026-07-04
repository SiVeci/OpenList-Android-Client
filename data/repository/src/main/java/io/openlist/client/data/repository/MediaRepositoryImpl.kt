package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.auth.TokenProvider
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.MediaRepository
import io.openlist.client.core.model.MediaSource
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.buildScopedHttpHeaders
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 5 (v0.4_EXECUTION_PLAN.md §11 S5-T1): real implementation,
 * following the exact instance-lookup / OpenListApi / 401-invalidation
 * pattern used by [PreviewRepositoryImpl.resolvePreview] and
 * [ExternalOpenRepositoryImpl.resolveExternalOpen] — a deliberately separate
 * `api.fsGet` call rather than delegating to either of those repositories
 * (same "no cross-feature-repository dependency" precedent).
 *
 * [resolveMedia] and [refreshMediaSource] share one private implementation:
 * "re-resolve fresh" is exactly what a signed-URL refresh means here (V-401 —
 * every visit/refresh re-fetches fs/get, no locally cached resolved URL is
 * ever reused), so there is no meaningful difference between "first resolve"
 * and "refresh" beyond the caller's intent.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val mimeTypeResolver: MimeTypeResolver,
    private val tokenProvider: TokenProvider,
) : MediaRepository {

    override suspend fun resolveMedia(instanceId: String, path: String): ApiResult<MediaSource> =
        resolveMediaInternal(instanceId, path)

    override suspend fun refreshMediaSource(instanceId: String, path: String): ApiResult<MediaSource> =
        resolveMediaInternal(instanceId, path)

    private suspend fun resolveMediaInternal(instanceId: String, path: String): ApiResult<MediaSource> {
        val normalizedPath = OpenListPathCodec.normalize(path)
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
                    // No playable url at all -- treat as "this file can't be
                    // played", not a generic/network failure.
                    ApiResult.Failure(DomainError.MediaUnsupported)
                } else {
                    // V-402: /d/ and /p/ urls carry their own `sign` query
                    // parameter for auth, no Authorization header is required
                    // -- fixed false until a concrete counter-example surfaces
                    // (same conclusion as PreviewRepositoryImpl.toPreviewTarget).
                    val headersRequired = false
                    // S5-T1's header-limitation mechanism (PRD §10.4): computed
                    // here, not in the UI layer, so `:feature:preview` never
                    // needs its own path to TokenProvider/instance base URLs
                    // (architecture rule: feature:preview -> core:{domain,
                    // designsystem,model,common} only). Only ever non-empty
                    // when headersRequired is true AND the resolved url's host
                    // matches this instance's own host.
                    val headers = if (headersRequired) {
                        buildScopedHttpHeaders(
                            requestUrl = resolvedUrl,
                            instanceBaseUrl = instance.baseUrl,
                            token = tokenProvider.blockingTokenFor(instanceId),
                        )
                    } else {
                        emptyMap()
                    }
                    ApiResult.Success(
                        MediaSource(
                            instanceId = instanceId,
                            path = normalizedPath,
                            title = resp.name,
                            mimeType = guessMimeType(resp.name),
                            url = resolvedUrl,
                            headersRequired = headersRequired,
                            // Provisional per V-401: we don't know the real
                            // expiry of a `sign` value, so this is left
                            // unset rather than guessed -- S5-T4's refresh
                            // flow reacts to an actual 4xx from the player,
                            // it does not rely on a predicted expiry time.
                            expiresAt = null,
                            // S6 scope (SubtitleRepository) -- fixed empty
                            // here per this Sprint's boundary.
                            subtitles = emptyList(),
                            headers = headers,
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

    /** Same best-effort MIME guess as [ExternalOpenRepositoryImpl.guessMimeType]
     * -- kept as a separate private copy rather than a shared helper since
     * both call sites are tiny and this repository must not depend on
     * ExternalOpenRepositoryImpl. */
    private fun guessMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return mimeTypeResolver.guessMimeType(extension)
    }
}
