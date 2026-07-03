package io.openlist.client.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Single source of truth for OpenList path handling (v0.1_PRD §5.3.2, §7.1).
 * UI/ViewModels must never build request paths by hand.
 *
 * Two distinct concerns:
 *  - Logical paths sent in JSON bodies (fs/list, fs/get `path` field) are plain,
 *    NOT URL-encoded — the server joins them against the user's base path.
 *  - Download URLs for the /d/ route need each segment percent-encoded; this is
 *    only a fallback, since fs/get already returns a ready-to-use `raw_url`.
 */
object OpenListPathCodec {

    /** Normalizes to a leading-slash, no-trailing-slash, single-slash path. Root stays "/". */
    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "/"
        val collapsed = ("/" + trimmed).replace(Regex("/+"), "/")
        return if (collapsed.length > 1) collapsed.trimEnd('/').ifEmpty { "/" } else "/"
    }

    /** Parent directory of a path; parent of root is root. */
    fun parent(path: String): String {
        val n = normalize(path)
        if (n == "/") return "/"
        val idx = n.lastIndexOf('/')
        return if (idx <= 0) "/" else n.substring(0, idx)
    }

    /** Last path segment (file/dir name); "" for root. */
    fun name(path: String): String {
        val n = normalize(path)
        if (n == "/") return ""
        return n.substring(n.lastIndexOf('/') + 1)
    }

    /** Joins a child name onto a parent directory. */
    fun child(parent: String, childName: String): String {
        val base = normalize(parent)
        val clean = childName.trim().trim('/')
        if (clean.isEmpty()) return base
        return if (base == "/") "/$clean" else "$base/$clean"
    }

    /** Ordered breadcrumb segments, excluding the implicit root. `/a/b` -> ["a","b"]. */
    fun segments(path: String): List<String> {
        val n = normalize(path)
        if (n == "/") return emptyList()
        return n.trim('/').split('/')
    }

    /** Rebuilds a path from the first [count] breadcrumb segments (for breadcrumb taps). */
    fun pathForSegment(path: String, count: Int): String {
        val segs = segments(path)
        if (count <= 0 || segs.isEmpty()) return "/"
        return "/" + segs.take(count.coerceAtMost(segs.size)).joinToString("/")
    }

    /**
     * Fallback download URL for the /d/ route when fs/get returns no raw_url.
     * Each path segment is percent-encoded via OkHttp; [sign] is appended when non-empty.
     * Returns null if [baseUrl] is not a valid HTTP(S) URL.
     */
    fun buildDownloadUrl(baseUrl: String, path: String, sign: String): String? {
        val base = baseUrl.trim().toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder().addPathSegment("d")
        for (segment in segments(path)) {
            builder.addPathSegment(segment)
        }
        if (sign.isNotEmpty()) builder.addQueryParameter("sign", sign)
        return builder.build().toString()
    }

    /**
     * Percent-encodes [path] for the upload `File-Path` header, which the
     * server unescapes with Go's `url.PathUnescape` (v0.2_EXECUTION_PLAN.md
     * §10.3/§14.3) — that only unescapes `%XX` sequences, it does not treat
     * `/` specially, so a blanket `URLEncoder.encode` would wrongly turn every
     * directory separator into `%2F` and break the path. Each segment is
     * encoded independently (same OkHttp machinery as [buildDownloadUrl]) and
     * rejoined with literal `/`.
     */
    fun encodePathForHeader(path: String): String {
        val builder = "http://x".toHttpUrlOrNull()!!.newBuilder()
        for (segment in segments(path)) {
            builder.addPathSegment(segment)
        }
        return builder.build().encodedPath
    }
}
