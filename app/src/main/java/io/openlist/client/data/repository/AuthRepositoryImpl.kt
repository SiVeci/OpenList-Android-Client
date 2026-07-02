package io.openlist.client.data.repository

import io.openlist.client.core.auth.CryptoManager
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.entity.SessionEntity
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Session
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.LoginReq
import io.openlist.client.core.network.dto.UserResp
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun getSession(instanceId: String): Session? =
        sessionDao.getByInstanceId(instanceId)?.toDomain()

    override fun observeSession(instanceId: String): Flow<Session?> =
        sessionDao.observeByInstanceId(instanceId).map { it?.toDomain() }

    override fun observeAllSessions(): Flow<List<Session>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun loginWithPassword(instanceId: String, username: String, password: String): ApiResult<Session> {
        val api = enterInstanceScope(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val loginResult = safeApiCall { api.login(LoginReq(username = username, password = password)) }
        val token = when (loginResult) {
            is ApiResult.Success -> loginResult.data.token
            is ApiResult.Failure -> return loginResult
        }
        return bootstrapSession(instanceId, api, token, AuthType.PASSWORD)
    }

    override suspend fun loginAsGuest(instanceId: String): ApiResult<Session> {
        val api = enterInstanceScope(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        // Clear any stale token first so this probe truly reflects guest access,
        // not a previously-logged-in user's still-valid token.
        sessionDao.deleteByInstanceId(instanceId)
        return when (val result = safeApiCall { api.me() }) {
            is ApiResult.Success -> persistSession(instanceId, AuthType.GUEST, result.data, tokenPlain = null, isGuest = true)
            is ApiResult.Failure -> result
        }
    }

    override suspend fun loginWithToken(instanceId: String, token: String): ApiResult<Session> {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return ApiResult.Failure(DomainError.InvalidInstance)
        val api = enterInstanceScope(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return bootstrapSession(instanceId, api, trimmed, AuthType.TOKEN)
    }

    override suspend fun refreshCurrentUser(instanceId: String): ApiResult<Session> {
        val api = enterInstanceScope(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val existing = sessionDao.getByInstanceId(instanceId) ?: return ApiResult.Failure(DomainError.Unauthorized)
        return when (val result = safeApiCall { api.me() }) {
            is ApiResult.Success -> {
                val updated = existing.copy(
                    username = result.data.username.ifBlank { null },
                    role = result.data.role,
                    updatedAt = System.currentTimeMillis(),
                )
                sessionDao.upsert(updated)
                ApiResult.Success(updated.toDomain())
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    /** Validates [token] directly (bypassing AuthInterceptor, which can only see
     * already-persisted tokens) before it is ever written to disk. */
    private suspend fun bootstrapSession(
        instanceId: String,
        api: OpenListApi,
        token: String,
        authType: AuthType,
    ): ApiResult<Session> {
        return when (val result = safeApiCall { api.meWithToken(token) }) {
            is ApiResult.Success -> persistSession(instanceId, authType, result.data, tokenPlain = token, isGuest = false)
            is ApiResult.Failure -> result
        }
    }

    private suspend fun persistSession(
        instanceId: String,
        authType: AuthType,
        user: UserResp,
        tokenPlain: String?,
        isGuest: Boolean,
    ): ApiResult<Session> {
        val now = System.currentTimeMillis()
        val entity = SessionEntity(
            instanceId = instanceId,
            authType = authType.name,
            username = user.username.ifBlank { null },
            tokenEncrypted = tokenPlain?.let(cryptoManager::encrypt),
            role = user.role,
            isGuest = isGuest,
            createdAt = now,
            updatedAt = now,
        )
        sessionDao.upsert(entity)
        instanceRepository.setCurrent(instanceId)
        return ApiResult.Success(entity.toDomain())
    }

    private suspend fun enterInstanceScope(instanceId: String): OpenListApi? {
        val instance = instanceRepository.getById(instanceId) ?: return null
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        return clientFactory.apiFor(instance.baseUrl)
    }

    private fun SessionEntity.toDomain() = Session(
        instanceId = instanceId,
        authType = AuthType.valueOf(authType),
        username = username,
        role = role,
        isGuest = isGuest,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
