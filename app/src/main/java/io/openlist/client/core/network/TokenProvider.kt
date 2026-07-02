package io.openlist.client.core.network

/**
 * Supplies the bearer token for a given instance to [interceptor.AuthInterceptor].
 * Synchronous because OkHttp interceptors are blocking.
 *
 * Bound to [io.openlist.client.core.auth.SessionTokenProvider] (Keystore/Session
 * backed) via `di.AuthModule`.
 */
interface TokenProvider {
    fun blockingTokenFor(instanceId: String): String?
}
