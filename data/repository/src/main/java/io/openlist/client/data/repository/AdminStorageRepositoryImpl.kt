package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.AdminStorageDetails
import io.openlist.client.core.model.AdminStoragePage
import io.openlist.client.core.model.AdminStorageStatus
import io.openlist.client.core.model.AdminStorageSummary
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.AdminStorageDetailsDto
import io.openlist.client.core.network.dto.AdminStorageDto
import io.openlist.client.core.network.safeApiCall
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S3 (read-only) + S4 (enable/disable/reload-all/driver-info)
 * implementation (v0.5_EXECUTION_PLAN.md §11 S3-T3/S4-T1..T4).
 *
 * [getStorages] reads through `admin_cache` (scope="storages",
 * cacheKey="list") with a 30s TTL (PRD §13.1); [forceRefresh] bypasses it.
 * [mountDetails] decode failures/absence are defensive by construction: the
 * DTO field is nullable and [AdminStorageDetailsDto.toDomain] simply isn't
 * called when it's null, so one storage's missing/slow mount_details never
 * fails the whole list (R-504/V-503).
 *
 * [fileCacheDao]/[previewRepository] are the same "Repository depends on
 * another Repository/DAO purely for cache-invalidation side effects"
 * exception [FileOperationRepositoryImpl] established in v0.4
 * (v0.4_EXECUTION_PLAN.md §11 S3-T4) — deliberately scoped to the
 * enable/disable/reload-all success paths only (PRD §13.3), not a precedent
 * for coupling this repository's read paths to files/preview elsewhere.
 */
@Singleton
class AdminStorageRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val adminCacheDao: AdminCacheDao,
    private val fileCacheDao: FileCacheDao,
    private val previewRepository: PreviewRepository,
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

    // ---- S4: driver read-only endpoints (no cache — rarely-accessed reference
    // data, plan doesn't mandate one and the added complexity isn't worth it) ----

    override suspend fun getDrivers(instanceId: String): ApiResult<Map<String, Any?>> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val result = safeApiCall { api.adminDriverList() }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> {
                onUnauthorized(instanceId, result)
                result
            }
        }
    }

    override suspend fun getDriverNames(instanceId: String): ApiResult<List<String>> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCall { api.adminDriverNames() }
        onUnauthorized(instanceId, result)
        return result
    }

    override suspend fun getDriverInfo(instanceId: String, driver: String): ApiResult<Map<String, Any?>> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val result = safeApiCall { api.adminDriverInfo(driver) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> {
                onUnauthorized(instanceId, result)
                result
            }
        }
    }

    // ---- S4: enable/disable/reload-all (PRD §13.2/§13.3) ----

    override suspend fun enableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        setStorageEnabled(instanceId, id, enable = true)

    override suspend fun disableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        setStorageEnabled(instanceId, id, enable = false)

    /**
     * Shared enable/disable path: on success, invalidates the `admin_cache`
     * storages scope (so the next [getStorages]/[getStorage] call misses the
     * cache and hits the network — chosen over a `cachedAt` rewrite as the
     * simpler of the two equivalent options the brief allowed) and performs
     * the precise file/preview cache linkage for this storage's own
     * [AdminStorageSummary.mountPath] (§10.3, S4-T2). On failure, nothing is
     * touched at all — no cache invalidation, no re-fetch — so the storage's
     * previously-displayed state is left exactly as it was (PRD §13.2.5).
     * The mount path is looked up via [getStorage] (not passed in by the
     * caller) so this holds even if the UI only has a stale/cached summary in
     * hand when the user taps enable/disable.
     */
    private suspend fun setStorageEnabled(instanceId: String, id: Int, enable: Boolean): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { if (enable) api.adminStorageEnable(id) else api.adminStorageDisable(id) }
        if (result is ApiResult.Success) {
            adminCacheDao.deleteByScope(instanceId, SCOPE_STORAGES)
            val mountPath = (safeApiCall { api.adminStorageGet(id) } as? ApiResult.Success)?.data?.mountPath
            if (mountPath != null) {
                val normalized = OpenListPathCodec.normalize(mountPath)
                fileCacheDao.deleteByPathPrefix(instanceId, normalized)
                previewRepository.invalidateByPrefix(instanceId, normalized)
            }
        } else {
            onUnauthorized(instanceId, result)
        }
        return result
    }

    /**
     * Asynchronous on the backend (§V-503/PRD §13.2.2) — success here only
     * means "reload submitted", never "reload completed"; the UI copy for
     * that distinction is a [AdminStorageTab]/S4-T3 concern, not this
     * Repository's. Since the affected storage set can't be precisely
     * determined (any/all storages may have been reloaded), invalidation is
     * the conservative broad form: the whole storages cache scope, every
     * cached file listing for this instance, and every cached preview under
     * "/" (the broadest prefix, per [PreviewRepository.invalidateByPrefix]'s
     * contract of "this path plus everything nested under it") — no
     * `invalidateAll(instanceId)`-shaped method exists on [PreviewRepository]
     * to prefer instead.
     */
    override suspend fun reloadAllStorages(instanceId: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { api.adminStorageLoadAll() }
        if (result is ApiResult.Success) {
            adminCacheDao.deleteByScope(instanceId, SCOPE_STORAGES)
            fileCacheDao.deleteByInstanceId(instanceId)
            previewRepository.invalidateByPrefix(instanceId, "/")
        } else {
            onUnauthorized(instanceId, result)
        }
        return result
    }

    // `safeAdminOperationCall` (shared with [AdminTaskRepositoryImpl]/S5,
    // [AdminIndexRepositoryImpl]/S6 -- see `AdminOperationSupport.kt`'s KDoc
    // for why write endpoints need `OpenListError` message passthrough
    // instead of the shared [safeApiCallUnit] bucketing) is scoped to these
    // 3 write endpoints only — [getStorages]/[getStorage]/driver reads keep
    // using the shared [safeApiCall] bucketing, since PRD §14.2's "show
    // backend message verbatim" requirement is specific to admin
    // *operations*, not read paths.

    private suspend fun apiFor(instanceId: String) = instanceRepository.getById(instanceId)?.let { instance ->
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        clientFactory.apiFor(instance.baseUrl)
    }

    private suspend fun onUnauthorized(instanceId: String, result: ApiResult<*>) {
        if (result is ApiResult.Failure && result.error == DomainError.Unauthorized) {
            sessionManager.invalidate(instanceId)
        }
    }

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
