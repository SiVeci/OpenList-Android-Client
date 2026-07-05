package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.AdminUserRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminUserPage
import io.openlist.client.core.model.AdminUserSummary
import io.openlist.client.core.model.Session
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminUserDto
import io.openlist.client.core.network.safeApiCall
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S3 implementation (v0.5_EXECUTION_PLAN.md §11 S3-T1), replacing the S1
 * stub. Follows the standard "instance lookup -> instanceContext.set ->
 * clientFactory.apiFor -> safeApiCall -> 401 sessionManager.invalidate"
 * pattern used by every other Repository (see FilesRepositoryImpl.getFile /
 * AuthRepositoryImpl.refreshCurrentUser).
 *
 * [getUsers] reads through `admin_cache` (scope="users", cacheKey="page:$page")
 * with a 1-minute TTL (PRD §13.1); [forceRefresh] (pull-to-refresh) always
 * bypasses the cache and re-caches on success. [getUser] deliberately does
 * NOT cache (the interface has no forceRefresh flag for it and a single-user
 * lookup is cheap/rare enough that a stale cached row isn't worth the
 * complexity — see IMPLEMENTATION_LOG decision note).
 *
 * [AdminUserDto.toDomain] is the one and only place `password` is dropped:
 * it is read off the DTO but never copied into [AdminUserSummary] (P-507/§8.5).
 */
@Singleton
class AdminUserRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val adminCacheDao: AdminCacheDao,
    private val json: Json,
) : AdminUserRepository {

    override suspend fun getUsers(instanceId: String, page: Int, forceRefresh: Boolean): ApiResult<AdminUserPage> {
        val cacheKey = "page:$page"
        if (!forceRefresh) {
            val cached = adminCacheDao.get(instanceId, SCOPE_USERS, cacheKey)
            if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MILLIS) {
                val decoded = runCatching { json.decodeFromString<AdminUserPage>(cached.rawJson) }.getOrNull()
                if (decoded != null) return ApiResult.Success(decoded)
            }
        }

        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.adminUserList(page = page, perPage = PAGE_SIZE) }) {
            is ApiResult.Success -> {
                val domain = AdminUserPage(
                    users = result.data.content.map { it.toDomain() },
                    total = result.data.total,
                )
                adminCacheDao.upsert(
                    AdminCacheEntity(
                        id = "$instanceId:$SCOPE_USERS:$cacheKey",
                        instanceId = instanceId,
                        scope = SCOPE_USERS,
                        cacheKey = cacheKey,
                        rawJson = json.encodeToString(domain),
                        cachedAt = System.currentTimeMillis(),
                    ),
                )
                ApiResult.Success(domain)
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    override suspend fun getUser(instanceId: String, id: Int): ApiResult<AdminUserSummary> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.adminUserGet(id) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val SCOPE_USERS = "users"
        const val CACHE_TTL_MILLIS = 60 * 1000L
    }
}

/**
 * DTO -> domain mapping. [AdminUserDto.password] is read off the source
 * object but never referenced here — that omission (not a redaction step) is
 * what keeps it out of [AdminUserSummary] (P-507/§8.5). [AdminUserDto.otp] is
 * not surfaced as `otpEnabled` either: no backend field for 2FA-enabled status
 * was confirmed in the source checkout (S1-T3/T4 KDoc), so this stays `null`
 * ("unknown") rather than guessing `false`, until a real field is verified
 * against a live instance.
 */
internal fun AdminUserDto.toDomain(): AdminUserSummary = AdminUserSummary(
    id = id,
    username = username,
    role = role,
    roleLabel = roleLabel(role),
    disabled = disabled,
    basePath = basePath.ifBlank { null },
    permission = permission,
    otpEnabled = null,
)

private fun roleLabel(role: Int): String = when (role) {
    Session.ROLE_ADMIN -> "管理员"
    Session.ROLE_GUEST -> "访客"
    Session.ROLE_GENERAL -> "普通用户"
    else -> "未知角色($role)"
}
