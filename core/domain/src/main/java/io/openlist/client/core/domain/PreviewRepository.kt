package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.MarkdownPreviewContent
import io.openlist.client.core.model.PreviewTarget
import io.openlist.client.core.model.PreviewUrl
import io.openlist.client.core.model.TextPreviewContent
import io.openlist.client.core.model.TextPreviewOptions

/**
 * Resolves a file path into a [PreviewTarget] (kind + open mode + fallbacks)
 * and reads text/markdown preview bodies (v0.4_EXECUTION_PLAN.md §11,
 * P-402). Image/video/audio previews use [MediaRepository] instead once
 * resolved to [io.openlist.client.core.model.PreviewOpenMode.IN_APP_VIDEO]/etc
 * — this repository owns classification for every kind, but only the
 * text-like content bodies (S1 scope: interface + Hilt wiring only, real
 * logic lands in S2/S3).
 */
interface PreviewRepository {
    suspend fun resolvePreview(instanceId: String, path: String): ApiResult<PreviewTarget>

    suspend fun loadText(instanceId: String, path: String, options: TextPreviewOptions): ApiResult<TextPreviewContent>

    suspend fun loadMarkdown(instanceId: String, path: String): ApiResult<MarkdownPreviewContent>

    /** Re-resolves a fresh, non-expired URL for a target whose previously
     * resolved [PreviewTarget.source] URL has expired. */
    suspend fun refreshPreviewUrl(instanceId: String, path: String): ApiResult<PreviewUrl>
}
