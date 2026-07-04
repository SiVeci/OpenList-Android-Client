package io.openlist.client.core.model

/** Result of `PreviewRepository.refreshPreviewUrl` — a freshly-signed/re-resolved
 * playable/fetchable URL for a preview target. [headersRequired] mirrors
 * [MediaSource]'s field of the same name (both describe "does the caller
 * need to attach the instance's auth header to use this URL", the same
 * concern S2/S3's HTTP loading and S4/S5's ExoPlayer data source setup both
 * need to answer identically). */
data class PreviewUrl(
    val url: String,
    val expiresAt: Long?,
    val headersRequired: Boolean = false,
)
