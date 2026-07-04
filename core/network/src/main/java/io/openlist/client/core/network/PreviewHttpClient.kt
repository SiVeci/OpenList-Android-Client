package io.openlist.client.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Separate raw OkHttpClient for streaming text/markdown preview bodies
 * (v0.4_EXECUTION_PLAN.md §11 S3-T1). Modeled on [UploadHttpClient]: it does
 * not carry [interceptor.AuthInterceptor] because preview content is always
 * read from a signed `/d/`/`/p/` URL whose `sign` query parameter is the
 * auth mechanism (V-402, S2's resolvePreview already fixes
 * `headersRequired = false` for these URLs) — no Authorization header is
 * ever needed here, so there's nothing for that interceptor's
 * current-instance lookup to attach, and depending on it would only risk the
 * same cross-instance drift [UploadHttpClient]'s doc comment warns about.
 *
 * Unlike the upload client, preview reads have a known, size-capped payload
 * (P-408: 512KB soft cap / 20MB hard ceiling before any request is even
 * made), so a bounded timeout is safe here where uploads needed an unbounded
 * one.
 */
@Singleton
class PreviewHttpClient @Inject constructor() {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
