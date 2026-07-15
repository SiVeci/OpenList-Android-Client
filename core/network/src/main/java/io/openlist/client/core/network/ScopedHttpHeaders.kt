package io.openlist.client.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Host-scoped `Authorization` header injection for media playback requests
 * (v0.4_EXECUTION_PLAN.md §11 S5-T1, PRD §10.4). ExoPlayer's `DataSource`
 * follows a resolved [MediaSource][io.openlist.client.core.model.MediaSource]'s
 * `url` directly — that url may or may not still point at the same host as
 * the owning OpenList instance (e.g. a proxied `raw_url` pointing at a
 * third-party storage backend's own domain, a CDN, etc.). Blindly attaching
 * this app's bearer token to every outgoing request for that url would leak
 * it to hosts that never should have seen it.
 *
 * [buildScopedHttpHeaders] is the single choke point that decides whether the
 * token is safe to attach: only when [requestUrl]'s scheme, host and port
 * are byte-for-byte identical to [instanceBaseUrl]'s origin. Any mismatch (different origin,
 * unparsable url, or a null/blank token) yields an empty map — "don't attach
 * anything" is always the safe default.
 *
 * Today [io.openlist.client.core.model.MediaSource.headersRequired] is fixed
 * `false` (V-402: signed `/d//p/` urls carry their own `sign` query parameter,
 * no `Authorization` header is required), so in practice this function's
 * non-empty branch is not yet reachable from production code — it exists so
 * the defense is in place before it's ever needed, not bolted on after an
 * incident.
 */
fun buildScopedHttpHeaders(requestUrl: String, instanceBaseUrl: String, token: String?): Map<String, String> {
    if (token.isNullOrBlank()) return emptyMap()

    val request = requestUrl.toHttpUrlOrNull() ?: return emptyMap()
    val instance = instanceBaseUrl.toHttpUrlOrNull() ?: return emptyMap()

    return if (request.scheme == instance.scheme && request.host == instance.host && request.port == instance.port) {
        mapOf("Authorization" to token)
    } else {
        emptyMap()
    }
}
