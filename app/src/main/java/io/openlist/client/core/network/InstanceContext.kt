package io.openlist.client.core.network

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** The instance a request belongs to: which base URL to hit and whose token to attach. */
data class InstanceScope(
    val instanceId: String,
    val baseUrl: String,
)

/**
 * Holds the currently-active instance so the client factory and interceptors can
 * resolve base URL + token without threading instanceId through every call site.
 * Set when the user opens/switches an instance.
 */
@Singleton
class InstanceContext @Inject constructor() {
    private val current = AtomicReference<InstanceScope?>(null)

    fun set(scope: InstanceScope) {
        current.set(scope)
    }

    fun clear() {
        current.set(null)
    }

    fun currentOrNull(): InstanceScope? = current.get()

    val currentInstanceId: String?
        get() = current.get()?.instanceId
}
