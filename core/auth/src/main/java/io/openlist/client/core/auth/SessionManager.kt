package io.openlist.client.core.auth

import io.openlist.client.core.database.dao.SessionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles session invalidation (v0.1_PRD §5.2.4): on a 401/expired-password/
 * disabled-user response, the affected instance's session is dropped so the
 * next visit shows the login form again. Only this instance is touched —
 * other instances' sessions are untouched by design (each row is keyed by its
 * own instanceId).
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
) {
    suspend fun invalidate(instanceId: String) {
        sessionDao.deleteByInstanceId(instanceId)
    }
}
