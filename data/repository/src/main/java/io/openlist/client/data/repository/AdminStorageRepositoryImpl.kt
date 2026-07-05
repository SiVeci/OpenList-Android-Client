package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminStorageDetails
import io.openlist.client.core.model.AdminStoragePage
import io.openlist.client.core.model.AdminStorageStatus
import io.openlist.client.core.model.AdminStorageSummary
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminStorageDetailsDto
import io.openlist.client.core.network.dto.AdminStorageDto
import io.openlist.client.core.network.safeApiCall
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S3 read-only implementation (v0.5_EXECUTION_PLAN.md §11 S3-T3),
 * replacing the S1 stub for [getStorages]/[getStorage] only — enable/disable/
 * reload-all/driver-info stay S1 stubs (S4 scope, per the S3 brief).
 *
 * [getStorages] reads through `admin_cache` (scope="storages",
 * cacheKey="list") with a 30s TTL (PRD §13.1); [forceRefresh] bypasses it.
 * [mountDetails] decode failures/absence are defensive by construction: the
 * DTO field is nullable and [AdminStorageDetailsDto.toDomain] simply isn't
 * called when it's null, so one storage's missing/slow mount_details never
 * fails the whole list (R-504/V-503).
 */
@Singleton
class AdminStorageRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val adminCacheDao: AdminCacheDao,
    private val json: Json,
) : AdminStorageRepository {

    override suspend fun getStorages(instanceId: String, forceRefresh: Boolean): ApiResult<AdminStoragePage> {
        if (!forceRefresh) {
            val cached = adminCacheDao.get(instanceId, SCOPE_STORAGES, CACHE_KEY_LIST)
            if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MILLIS) {
                val decoded = runCatching { json.decodeFromString<AdminStoragePage>(cached.rawJson) }.getOrNull()
                if (decoded != null) return ApiResult.Success(decoded)
            }
        }

        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        // Page size large enough to cover the vast majority of real-world
        // storage counts in one request — a paginated storage Tab isn't worth
        // the added complexity for v0.5 (storages are typically few, unlike
        // users/files).
        return when (val result = safeApiCall { api.adminStorageList(page = 1, perPage = PAGE_SIZE) }) {
            is ApiResult.Success -> {
                val domain = AdminStoragePage(
                    storages = result.data.content.map { it.toDomain() },
                    total = result.data.total,
                )
                adminCacheDao.upsert(
                    AdminCacheEntity(
                        id = "$instanceId:$SCOPE_STORAGES:$CACHE_KEY_LIST",
                        instanceId = instanceId,
                        scope = SCOPE_STORAGES,
                        cacheKey = CACHE_KEY_LIST,
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

    override suspend fun getStorage(instanceId: String, id: Int): ApiResult<AdminStorageSummary> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.adminStorageGet(id) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    // ---- S4 scope: still S1 stubs this Sprint ----

    override suspend fun getDrivers(instanceId: String): ApiResult<Map<String, Any?>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDriverNames(instanceId: String): ApiResult<List<String>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDriverInfo(instanceId: String, driver: String): ApiResult<Map<String, Any?>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun enableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun disableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun reloadAllStorages(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    private companion object {
        const val PAGE_SIZE = 200
        const val SCOPE_STORAGES = "storages"
        const val CACHE_KEY_LIST = "list"
        const val CACHE_TTL_MILLIS = 30 * 1000L
    }
}

/**
 * DTO -> domain mapping. [AdminStorageDto.mountDetails] being null/absent (or
 * failing to decode entirely — kotlinx.serialization would already have
 * dropped an unparseable nested object to null given [AdminStorageDetailsDto]
 * defaults `ignoreUnknownKeys`/coerced-defaults at the Json config level, see
 * NetworkModule) simply yields `mountDetails = null` here, never a thrown
 * exception (R-504).
 */
internal fun AdminStorageDto.toDomain(): AdminStorageSummary = AdminStorageSummary(
    id = id,
    mountPath = mountPath,
    driver = driver,
    disabled = disabled,
    order = order,
    remark = remark.ifBlank { null },
    status = deriveStatus(disabled, status),
    mountDetails = mountDetails?.toDomain(),
)

private fun AdminStorageDetailsDto.toDomain(): AdminStorageDetails = AdminStorageDetails(
    totalSpace = totalSpace,
    usedSpace = usedSpace,
)

/** [rawStatus] is a free-form backend string (often "work" when healthy, or a
 * driver error message otherwise, per AdminStorageDto's KDoc) — [disabled]
 * takes priority since it's the one strongly-typed signal; a non-"work"
 * status on an enabled storage is treated as [AdminStorageStatus.ERROR]. */
private fun deriveStatus(disabled: Boolean, rawStatus: String): AdminStorageStatus = when {
    disabled -> AdminStorageStatus.DISABLED
    rawStatus.isBlank() -> AdminStorageStatus.UNKNOWN
    rawStatus.equals("work", ignoreCase = true) -> AdminStorageStatus.ENABLED
    else -> AdminStorageStatus.ERROR
}
