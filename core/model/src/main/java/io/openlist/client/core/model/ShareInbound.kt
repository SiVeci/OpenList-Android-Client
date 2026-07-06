package io.openlist.client.core.model

/**
 * A parsed, instance-matched inbound share link (v1.0_PRD §9.2 Share
 * Inbound.1). In-memory only — never persisted, never logged (the share
 * password that resolves it is a separate, also-never-persisted field on the
 * request side, not stored here at all).
 */
data class ShareInboundTarget(
    val instanceId: String,
    val baseUrl: String,
    val sid: String,
    /** Sub-path within the share; null/blank means the share root. */
    val path: String?,
    val sourceUrl: String,
)

/**
 * Share-authenticated `fs/get` result for one path within a share
 * (v1.0_PRD §9.2 Share Inbound). [rawUrl] is a signed URL (V-608: reusable,
 * no Authorization header needed) — blank when [isDir] is true.
 */
data class ShareInboundInfo(
    val sid: String,
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val rawUrl: String,
)
