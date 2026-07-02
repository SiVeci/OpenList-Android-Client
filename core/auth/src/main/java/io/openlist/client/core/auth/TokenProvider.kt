package io.openlist.client.core.auth

/**
 * Supplies the bearer token for a given instance to
 * `core.network.interceptor.AuthInterceptor`. Synchronous because OkHttp
 * interceptors are blocking.
 *
 * Lives in `core.auth` (not `core.network`) so the network layer depends on
 * this abstraction rather than the auth layer depending on network — bound to
 * [SessionTokenProvider] (Keystore/Session backed) via `di.AuthModule`.
 */
interface TokenProvider {
    fun blockingTokenFor(instanceId: String): String?
}
