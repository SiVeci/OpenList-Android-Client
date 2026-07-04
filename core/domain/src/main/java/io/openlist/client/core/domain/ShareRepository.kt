package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareWriteRequest
import kotlinx.coroutines.flow.Flow

/**
 * Share CRUD + enable/disable, backed by the `/api/share` endpoints (`AuthNotGuest` —
 * guests never see this repository's data, v0.3_EXECUTION_PLAN.md §6.1/P10).
 * Write operations refresh the local cache from the response rather than
 * optimistically updating it (§18.1).
 */
interface ShareRepository {
    /** Cache-only, instant (§18.1: "进入列表先显缓存"). */
    fun observeShares(instanceId: String): Flow<List<Share>>

    /** Fetches from the network and overwrites the cache (pull-to-refresh). */
    suspend fun listShares(instanceId: String): ApiResult<List<Share>>

    suspend fun getShare(instanceId: String, id: String): ApiResult<Share>

    suspend fun createShare(instanceId: String, request: ShareWriteRequest): ApiResult<Share>

    suspend fun updateShare(instanceId: String, id: String, request: ShareWriteRequest): ApiResult<Share>

    suspend fun enableShare(instanceId: String, id: String): ApiResult<Unit>

    suspend fun disableShare(instanceId: String, id: String): ApiResult<Unit>

    /** Single-id only — the backend does not support batch delete. */
    suspend fun deleteShare(instanceId: String, id: String): ApiResult<Unit>

    /** Never hardcodes an instance address (§23.2) — always derived from
     * [instanceBaseUrl]. Format pending real-device verification (V-01). */
    fun buildShareUrl(instanceBaseUrl: String, id: String): String
}
