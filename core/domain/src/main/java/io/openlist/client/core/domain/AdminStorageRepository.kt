package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminStoragePage
import io.openlist.client.core.model.AdminStorageSummary

/**
 * Storage viewing + the 3 lightweight operations v0.5 supports: enable/
 * disable/reload-all (PRD §10.4). Does not support create/update/delete or
 * any dynamic per-driver configuration form (out of scope, PRD §9.2/§18.2).
 */
interface AdminStorageRepository {
    /** [forceRefresh] bypasses the `admin_cache` TTL (30s, PRD §13.1). */
    suspend fun getStorages(instanceId: String, forceRefresh: Boolean = false): ApiResult<AdminStoragePage>

    suspend fun getStorage(instanceId: String, id: Int): ApiResult<AdminStorageSummary>

    /** `op.GetDriverInfoMap()` — driver name -> metadata tree, read-only
     * display only (PRD §9.3 "结构复杂时只展示驱动名+关键摘要"). */
    suspend fun getDrivers(instanceId: String): ApiResult<Map<String, Any?>>

    suspend fun getDriverNames(instanceId: String): ApiResult<List<String>>

    suspend fun getDriverInfo(instanceId: String, driver: String): ApiResult<Map<String, Any?>>

    /** Requires a two-step confirm in UI (PRD §8.5). On success, the caller
     * (Impl) must invalidate the storage list cache and trigger the file/
     * preview cache linkage for the affected mount path (§10.3, S4 scope —
     * this S1 stub does neither yet). */
    suspend fun enableStorage(instanceId: String, id: Int): ApiResult<Unit>

    /** Dangerous-style confirm required in UI (PRD §8.5/§15.2). */
    suspend fun disableStorage(instanceId: String, id: Int): ApiResult<Unit>

    /** Asynchronous on the backend (server responds immediately, reload
     * continues in the background) — caller must present this as "reload
     * submitted", not "reload completed" (PRD §13.2.2). Requires a two-step
     * confirm in UI (PRD §8.5). */
    suspend fun reloadAllStorages(instanceId: String): ApiResult<Unit>
}
