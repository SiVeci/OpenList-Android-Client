package io.openlist.client.core.network.interceptor

import io.openlist.client.core.auth.TokenProvider
import io.openlist.client.core.network.InstanceContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the current instance's token as the `Authorization` header.
 * Guest mode (no token) sends no header. Token is resolved per-instance via
 * [InstanceContext] so switching instances never leaks another instance's token.
 *
 * 401-driven session invalidation is wired in Sprint 3 (SessionManager); this
 * class deliberately only handles header injection for v0.1 Sprint 2.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val instanceContext: InstanceContext,
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val instanceId = instanceContext.currentInstanceId
        val token = instanceId?.let { tokenProvider.blockingTokenFor(it) }
        val request = if (!token.isNullOrEmpty()) {
            chain.request().newBuilder()
                .header("Authorization", token)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
