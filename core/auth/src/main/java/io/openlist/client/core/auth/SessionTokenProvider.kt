package io.openlist.client.core.auth

import io.openlist.client.core.database.dao.SessionDao
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [TokenProvider]: looks up the persisted, Keystore-encrypted token for an
 * instance. Blocking is intentional — OkHttp interceptors already run off the
 * main thread, and [TokenProvider.blockingTokenFor]'s contract requires a
 * synchronous return (see that interface's doc).
 */
@Singleton
class SessionTokenProvider @Inject constructor(
    private val sessionDao: SessionDao,
    private val cryptoManager: CryptoManager,
) : TokenProvider {
    override fun blockingTokenFor(instanceId: String): String? {
        val encrypted = runBlocking { sessionDao.getByInstanceId(instanceId) }?.tokenEncrypted ?: return null
        return runCatching { cryptoManager.decrypt(encrypted) }.getOrNull()
    }
}
