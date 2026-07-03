package io.openlist.client.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Separate client for `PUT api/fs/put` (v0.2_EXECUTION_PLAN.md §14.2/decision
 * B): no read timeout (a large upload can legitimately take longer than the
 * 30s [OpenListClientFactory] budgets for ordinary JSON calls), and — unlike
 * [OpenListClientFactory]'s per-instance clients — it deliberately does *not*
 * carry [interceptor.AuthInterceptor]. That interceptor reads the shared,
 * mutable [InstanceContext] "current instance" pointer, which a background
 * Worker cannot rely on: the foreground UI may switch instances mid-upload,
 * which would attach the wrong instance's token. Callers must build the
 * `Authorization` header themselves from the *task's own* instanceId instead.
 */
@Singleton
class UploadHttpClient @Inject constructor() {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}
