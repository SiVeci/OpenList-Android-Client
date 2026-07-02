package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Login/session lifecycle for a single instance (v0.1_PRD §5.2). All methods
 * that hit the network resolve the instance's OpenListApi and set
 * [io.openlist.client.core.network.InstanceContext] as a side effect, so
 * AuthInterceptor always attaches the right instance's token.
 */
interface AuthRepository {
    suspend fun getSession(instanceId: String): Session?
    fun observeSession(instanceId: String): Flow<Session?>
    fun observeAllSessions(): Flow<List<Session>>

    suspend fun loginWithPassword(instanceId: String, username: String, password: String): ApiResult<Session>

    /** Probes GET /api/me with no token attached; fails if the instance disables guests. */
    suspend fun loginAsGuest(instanceId: String): ApiResult<Session>

    /** Admin/site API token entered directly by the user (v0.1_PRD §5.2.3). */
    suspend fun loginWithToken(instanceId: String, token: String): ApiResult<Session>

    /**
     * Re-validates an existing session against GET /api/me (needed because
     * OpenList's token-validity cache is in-memory server-side and is cleared on
     * server restart even for otherwise-unexpired JWTs). Invalidates the local
     * session on 401.
     */
    suspend fun refreshCurrentUser(instanceId: String): ApiResult<Session>
}
