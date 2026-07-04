package io.openlist.client.core.model

/** Decoded text preview payload (S3 renders this; S1 only defines the
 * shape). [isTruncated]/[totalBytes] let the UI show "showing first N of
 * totalBytes" instead of silently clipping. */
data class TextPreviewContent(
    val path: String,
    val text: String,
    val encoding: String?,
    val isTruncated: Boolean,
    val totalBytes: Long?,
)

/** Decoded markdown preview payload. [basePath] is the directory the
 * markdown file lives in, needed by S3's renderer to resolve the raw
 * document's relative image/link references against the same OpenList
 * instance rather than assuming an absolute URL. */
data class MarkdownPreviewContent(
    val path: String,
    val rawMarkdown: String,
    val basePath: String,
    val isTruncated: Boolean,
)

/** Read options for `PreviewRepository.loadText`/`loadMarkdown`.
 * [forceRefresh] bypasses the `preview_cache` row (S3's "retry"/pull-to-refresh
 * affordance); [maxBytesOverride] lets a caller request more than the
 * repository's default truncation limit for one specific read (e.g. user
 * taps "show full file" past the default cap) without changing that default
 * globally. Both default to "use the repository's normal behavior" so most
 * call sites can pass `TextPreviewOptions()` unchanged. */
data class TextPreviewOptions(
    val forceRefresh: Boolean = false,
    val maxBytesOverride: Long? = null,
)
