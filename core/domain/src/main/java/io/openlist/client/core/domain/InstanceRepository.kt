package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.Instance
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for instance CRUD, current-instance selection, and
 * connectivity checks (v0.1_PRD §5.1). UI/ViewModels depend on this interface
 * only — see [io.openlist.client.data.repository.InstanceRepositoryImpl] for
 * the Room + network backed implementation.
 */
interface InstanceRepository {
    fun observeAll(): Flow<List<Instance>>
    suspend fun getById(id: String): Instance?
    suspend fun getCurrent(): Instance?

    /** Validates, normalizes and de-duplicates [rawUrl] before persisting. */
    suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance>

    /** Marks [id] as the current instance and bumps its lastUsedAt. */
    suspend fun setCurrent(id: String)

    /** Deletes the instance row and all instanceId-scoped local data: session
     * (Token), file cache, download task records (v0.1_PRD §5.1.3). */
    suspend fun delete(id: String)

    /** Tries GET /ping, falling back to GET /api/public/settings (v0.1_PRD §5.1.1). */
    suspend fun testConnection(baseUrl: String): ApiResult<Unit>
}
