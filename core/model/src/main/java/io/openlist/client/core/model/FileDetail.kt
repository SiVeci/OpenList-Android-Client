package io.openlist.client.core.model

/** v0.1_PRD §5.4 file detail fields. [rawUrl] is the ready-to-use download URL
 * (server's raw_url, already accounting for proxy/direct/signing — v0.1's
 * verified V-02/V-03 answer), empty if the server returned none and no
 * fallback /d/ URL could be constructed. */
data class FileDetail(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedAt: Long?,
    val type: Int,
    val sign: String,
    val rawUrl: String,
    val provider: String,
    /** Backend-provided content hash metadata when the driver exposes it. */
    val hashInfo: String = "",
)
