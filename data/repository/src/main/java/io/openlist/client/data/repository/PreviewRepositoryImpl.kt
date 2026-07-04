package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.MarkdownPreviewContent
import io.openlist.client.core.model.PreviewTarget
import io.openlist.client.core.model.PreviewUrl
import io.openlist.client.core.model.TextPreviewContent
import io.openlist.client.core.model.TextPreviewOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 1 placeholder (v0.4_EXECUTION_PLAN.md §11 S1-T4): every method
 * is a stub returning [DomainError.Unknown] so the module graph, Hilt
 * binding and call sites all compile against the real interface ahead of
 * time. Real classification/reading logic (instance lookup, OpenListApi
 * calls, `preview_cache` reads/writes) lands in S2 (image/resolve) and S3
 * (text/markdown). No constructor dependencies are declared yet — S2/S3
 * will add `InstanceRepository`/`OpenListClientFactory`/`PreviewCacheDao`/etc
 * as each method's real implementation needs them, following the same
 * constructor-injection pattern used by every other `*RepositoryImpl` in
 * this module (see e.g. OfflineDownloadRepositoryImpl).
 */
@Singleton
class PreviewRepositoryImpl @Inject constructor() : PreviewRepository {

    override suspend fun resolvePreview(instanceId: String, path: String): ApiResult<PreviewTarget> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun loadText(instanceId: String, path: String, options: TextPreviewOptions): ApiResult<TextPreviewContent> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun loadMarkdown(instanceId: String, path: String): ApiResult<MarkdownPreviewContent> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun refreshPreviewUrl(instanceId: String, path: String): ApiResult<PreviewUrl> =
        ApiResult.Failure(DomainError.Unknown(null))
}
