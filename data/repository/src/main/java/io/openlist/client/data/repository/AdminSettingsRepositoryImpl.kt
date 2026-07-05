package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.AdminSettingsRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminSettingItem
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminSettingDto
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.safeApiCall
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S7 implementation (v0.5_EXECUTION_PLAN.md §11 S7-T1), replacing the S1
 * stub. Follows the same "instance lookup -> instanceContext.set ->
 * clientFactory.apiFor -> safeApiCall -> 401 sessionManager.invalidate"
 * pattern as every other Admin* repository (see [AdminUserRepositoryImpl]).
 *
 * [getSettings] reads through `admin_cache` (scope="settings",
 * cacheKey="group:$group" or "all" when [group] is null) with a **5-minute**
 * TTL (PRD §13.1.5). [getDefaultSettings] deliberately shares the exact same
 * cache scope/TTL machinery under a distinct `scope="settings_default"` --
 * brief allows either "no cache" or "same TTL/scope pattern for simplicity";
 * the latter was picked since it costs nothing extra (same private helper)
 * and avoids a surprising asymmetry where one read path is cached and the
 * sibling isn't for no principled reason.
 *
 * **Critical (P-507/§8.5):** [AdminSettingDto.toDomain] blanks [AdminSettingItem
 * .value] to `null` whenever [AdminSettingItem.isPrivate] is true *before*
 * constructing the domain object -- so by the time a list of [AdminSettingItem]
 * reaches the `json.encodeToString` call that produces `admin_cache.rawJson`,
 * private values are already gone from the object graph. There is no separate
 * "redact before caching" step because there is nothing left to redact by
 * then; see [AdminSettingsRepositoryImplTest] for the assertion that the
 * cached JSON string never contains a known secret substring.
 */
@Singleton
class AdminSettingsRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val adminCacheDao: AdminCacheDao,
    private val json: Json,
) : AdminSettingsRepository {

    override suspend fun getSettings(instanceId: String, group: Int?, forceRefresh: Boolean): ApiResult<List<AdminSettingItem>> =
        loadSettings(
            instanceId = instanceId,
            group = group,
            forceRefresh = forceRefresh,
            scope = SCOPE_SETTINGS,
            call = { api -> api.adminSettingList(group = group) },
        )

    /**
     * No [forceRefresh] parameter on the interface -- defaults to always
     * reading through the same 5-minute cache as [getSettings] (see class
     * KDoc for why a cache was chosen here at all).
     */
    override suspend fun getDefaultSettings(instanceId: String, group: Int?): ApiResult<List<AdminSettingItem>> =
        loadSettings(
            instanceId = instanceId,
            group = group,
            forceRefresh = false,
            scope = SCOPE_SETTINGS_DEFAULT,
            call = { api -> api.adminSettingDefault(group = group) },
        )

    private suspend fun loadSettings(
        instanceId: String,
        group: Int?,
        forceRefresh: Boolean,
        scope: String,
        call: suspend (OpenListApi) -> ApiResponse<List<AdminSettingDto>>,
    ): ApiResult<List<AdminSettingItem>> {
        val cacheKey = group?.let { "group:$it" } ?: "all"
        if (!forceRefresh) {
            val cached = adminCacheDao.get(instanceId, scope, cacheKey)
            if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MILLIS) {
                val decoded = runCatching { json.decodeFromString<List<AdminSettingItem>>(cached.rawJson) }.getOrNull()
                if (decoded != null) return ApiResult.Success(decoded)
            }
        }

        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { call(api) }) {
            is ApiResult.Success -> {
                // Mapping to AdminSettingItem happens BEFORE any encodeToString
                // call -- toDomain() already blanks `value` for private items,
                // so the object about to be serialized never carries the raw
                // secret (P-507).
                val items = result.data.map { it.toDomain() }
                adminCacheDao.upsert(
                    AdminCacheEntity(
                        id = "$instanceId:$scope:$cacheKey",
                        instanceId = instanceId,
                        scope = scope,
                        cacheKey = cacheKey,
                        rawJson = json.encodeToString(items),
                        cachedAt = System.currentTimeMillis(),
                    ),
                )
                ApiResult.Success(items)
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    private companion object {
        const val SCOPE_SETTINGS = "settings"
        const val SCOPE_SETTINGS_DEFAULT = "settings_default"
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * DTO -> domain mapping (P-508). [AdminSettingItem.isPrivate] = `flag == 1`
 * (PRIVATE, V-507) OR the key case-insensitively contains one of
 * token/secret/password/key -- defense in depth in case the backend ever
 * under-flags a sensitive setting. When [AdminSettingItem.isPrivate] is true,
 * [AdminSettingItem.value] is set to `null` right here, before the caller ever
 * gets a chance to serialize/display/log it.
 */
internal fun AdminSettingDto.toDomain(): AdminSettingItem {
    val private = flag == PRIVATE_FLAG || PRIVATE_KEY_KEYWORDS.any { key.contains(it, ignoreCase = true) }
    return AdminSettingItem(
        key = key,
        value = if (private) null else value,
        type = type.ifBlank { null },
        group = group,
        flag = flag,
        isPrivate = private,
    )
}

private const val PRIVATE_FLAG = 1
private val PRIVATE_KEY_KEYWORDS = listOf("token", "secret", "password", "key")
