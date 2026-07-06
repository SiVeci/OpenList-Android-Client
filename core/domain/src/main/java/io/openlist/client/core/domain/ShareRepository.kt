package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareInboundInfo
import io.openlist.client.core.model.ShareInboundTarget
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
     * [instanceBaseUrl]. Format confirmed v1.0 V-607a: `{baseUrl}/@s/{sid}`. */
    fun buildShareUrl(instanceBaseUrl: String, id: String): String

    /**
     * Parses [url] and matches its host/port/scheme against a configured
     * instance (v1.0_PRD §4.2.D.2/§11.4.1 — only same-instance links resolve
     * automatically). Pure lookup, no network call; returns null if the URL
     * isn't a share link or matches no configured instance.
     */
    suspend fun resolveInboundUrl(url: String): ShareInboundTarget?

    /**
     * Share-authenticated fetch for one path within a share (v1.0_PRD
     * §4.2.D.3). [path] null/blank fetches the share root. [password] is used
     * only for this request — never persisted, never logged. Fails with
     * [io.openlist.client.core.common.DomainError.SharePasswordRequired] if a
     * password is missing/wrong (V-607c).
     */
    suspend fun getInboundShare(
        instanceId: String,
        sid: String,
        path: String?,
        password: String?,
    ): ApiResult<ShareInboundInfo>
}
