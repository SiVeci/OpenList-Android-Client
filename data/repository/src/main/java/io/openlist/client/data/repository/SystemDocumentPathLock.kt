package io.openlist.client.data.repository

import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes local transactions for one remote instance/path pair. */
@Singleton
class SystemDocumentPathLock @Inject constructor() {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(instanceId: String, path: String, block: suspend () -> T): T {
        require(instanceId.isNotBlank()) { "实例标识为空" }
        require(OpenListPathCodec.isSafeNormalizedPath(path)) { "非法远端路径" }
        val key = "$instanceId\u0000$path"
        val mutex = locks.computeIfAbsent(key) { Mutex() }
        return mutex.withLock { block() }
    }
}
