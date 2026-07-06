package io.openlist.client.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure parser for an OpenList share web URL (v1.0_EXECUTION_PLAN.md §11 S3-T1;
 * V-607a format `{baseUrl}/@s/{sid}/{path}`, source-confirmed in
 * `server/router.go`/`server/handles/fsread.go`). No network/instance
 * dependency — [io.openlist.client.data.repository.ShareRepositoryImpl]
 * combines this with the configured instance list to resolve a full
 * [io.openlist.client.core.model.ShareInboundTarget].
 *
 * Placed alongside [BaseUrlNormalizer] in `core:network` (both need
 * `okhttp3.HttpUrl`) rather than `core:common` as the execution plan's prose
 * suggested — `core:common` has no OkHttp dependency and BaseUrlNormalizer
 * already established this module as the home for URL-parsing utilities.
 */
object ShareUrlParser {

    data class ParsedShareUrl(
        val scheme: String,
        val host: String,
        val port: Int,
        val sid: String,
        val path: String?,
    )

    /** Finds an `@s` segment anywhere in the path (so sub-path deployments
     * like `https://host/deploy/@s/{sid}` still match) and treats the next
     * segment as the share id; everything after that is the in-share path. */
    fun parse(rawUrl: String): ParsedShareUrl? {
        val httpUrl = rawUrl.trim().toHttpUrlOrNull() ?: return null
        val segments = httpUrl.pathSegments
        val markerIndex = segments.indexOf("@s")
        if (markerIndex == -1 || markerIndex + 1 >= segments.size) return null
        val sid = segments[markerIndex + 1]
        if (sid.isBlank()) return null
        val subPath = segments.drop(markerIndex + 2).filter { it.isNotBlank() }.joinToString("/")
        return ParsedShareUrl(
            scheme = httpUrl.scheme,
            host = httpUrl.host,
            port = httpUrl.port,
            sid = sid,
            path = subPath.ifBlank { null },
        )
    }
}
