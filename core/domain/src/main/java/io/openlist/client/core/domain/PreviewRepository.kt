package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.MarkdownImageSource
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

    /** [forceRefresh] bypasses the `preview_cache` row, same meaning as
     * [TextPreviewOptions.forceRefresh] — added in S3 (not part of the
     * original S1 signature) so a "retry" action can bypass a stale cached
     * body without a full [TextPreviewOptions]-shaped parameter. */
    suspend fun loadMarkdown(instanceId: String, path: String, forceRefresh: Boolean = false): ApiResult<MarkdownPreviewContent>

    /** Re-resolves a fresh, non-expired URL for a target whose previously
     * resolved [PreviewTarget.source] URL has expired. */
    suspend fun refreshPreviewUrl(instanceId: String, path: String): ApiResult<PreviewUrl>

    /** Drops the cached preview body (both the `preview_cache` row and its
     * on-disk file) for exactly this (instanceId, path), across every kind.
     * Internal cache-maintenance operation (S3-T4) — failures are swallowed,
     * never surfaced as an [ApiResult], since callers invoke this as a
     * best-effort side effect of a write operation succeeding, not as a
     * user-facing action in its own right. */
    suspend fun invalidate(instanceId: String, path: String)

    /** Same as [invalidate] but for every cached preview under [pathPrefix]
     * (the prefix path itself, plus anything nested under it) — used after
     * rename/remove/move/copy on a directory. */
    suspend fun invalidateByPrefix(instanceId: String, pathPrefix: String)

    /**
     * Classifies+resolves one Markdown image reference (v1.0_PRD §7.4).
     * Absolute http(s)/data URLs pass through as [MarkdownImageSource.External]
     * (untouched, never given credentials); a same-instance relative path
     * (resolved against [basePath]) becomes [MarkdownImageSource.Internal] via
     * the same unauthenticated `fs/get` mechanism every other file preview
     * uses (V-608 — reusable signed URL, no extra headers needed).
     */
    suspend fun resolveMarkdownImage(instanceId: String, basePath: String, imageRef: String): MarkdownImageSource

    /**
     * Rewrites every embedded image reference in [content]'s raw markdown to
     * its resolved [MarkdownImageSource.Internal] URL (v1.0 S4). Unresolvable
     * or external refs are left exactly as written — an external URL never
     * needs rewriting, and an unresolvable one degrades to Markwon's default
     * "no image, text continues" behavior rather than a broken placeholder.
     */
    suspend fun resolveMarkdownImages(instanceId: String, content: MarkdownPreviewContent): MarkdownPreviewContent
}
