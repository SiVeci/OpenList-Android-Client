package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.ExternalOpenRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.ExternalOpenTarget
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension -> MIME type lookup, extracted behind an interface purely so
 * [ExternalOpenRepositoryImpl] is unit-testable on the plain JVM: the
 * project's unit tests run with neither Robolectric nor
 * `testOptions.unitTests.isReturnDefaultValues`, so a direct call to
 * `android.webkit.MimeTypeMap.getSingleton()` throws in that environment.
 * [AndroidMimeTypeResolver] is the real implementation wired via Hilt;
 * tests substitute a trivial fake instead.
 */
interface MimeTypeResolver {
    /** [extensionLowercase] has no leading dot. Returns null if unknown. */
    fun guessMimeType(extensionLowercase: String): String?
}

@Singleton
class AndroidMimeTypeResolver @Inject constructor() : MimeTypeResolver {
    override fun guessMimeType(extensionLowercase: String): String? =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionLowercase)
}

/**
 * v0.4 Sprint 4 (v0.4_EXECUTION_PLAN.md §11 S4-T1): real implementation,
 * following the same instance-lookup / OpenListApi / 401-invalidation
 * pattern as [PreviewRepositoryImpl.resolvePreview] — deliberately a
 * separate `api.fsGet` call rather than delegating to [PreviewRepository]
 * so this repository has no dependency on another feature-adjacent
 * repository (see PreviewRepositoryImpl's KDoc precedent for why that
 * dependency direction is the exception, not the rule).
 *
 * [ExternalOpenTarget.externalUri]/[ExternalOpenTarget.webUrl] intentionally
 * carry the exact same URL (V-402: `/d/` `/p/` URLs are publicly fetchable
 * given the `sign` query parameter, no `Authorization` header is embedded or
 * required) — the difference between "open externally" and "open in a
 * browser" is purely in how the caller builds the `Intent` (with vs. without
 * a MIME type), not in the URL itself.
 */
@Singleton
class ExternalOpenRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val mimeTypeResolver: MimeTypeResolver,
) : ExternalOpenRepository {

    override suspend fun resolveExternalOpen(instanceId: String, path: String): ApiResult<ExternalOpenTarget> {
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
                    ApiResult.Failure(DomainError.Unknown(null))
                } else {
                    ApiResult.Success(
                        ExternalOpenTarget(
                            externalUri = resolvedUrl,
                            webUrl = resolvedUrl,
                            canDownload = true,
                            mimeType = guessMimeType(resp.name),
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

    /** Best-effort MIME guess from the file extension — no bespoke
     * extension->MIME table is maintained here (that's a different job than
     * [PreviewKindResolver], which classifies into
     * [io.openlist.client.core.model.PreviewKind], not a MIME string).
     * Returns null if there's no extension or the platform table doesn't
     * recognize it. */
    private fun guessMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return mimeTypeResolver.guessMimeType(extension)
    }
}
