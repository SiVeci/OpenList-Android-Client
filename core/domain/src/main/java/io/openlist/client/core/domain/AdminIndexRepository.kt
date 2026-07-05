package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminIndexProgress

/**
 * Search-index administration (PRD §10.6). Progress is never persisted to
 * `admin_cache` (polled live while the Index tab is foregrounded, PRD §13.1.4).
 */
interface AdminIndexRepository {
    suspend fun getProgress(instanceId: String): ApiResult<AdminIndexProgress>

    /** Full rebuild; requires two-step confirm in UI (PRD §8.5). */
    suspend fun buildIndex(instanceId: String): ApiResult<Unit>

    /** [maxDepth] default/semantics pending DEC-504; see
     * `AdminIndexUpdateReq` (`core:network`) for the current default. */
    suspend fun updateIndex(instanceId: String, paths: List<String>, maxDepth: Int): ApiResult<Unit>

    /** Requires two-step confirm in UI (PRD §8.5). */
    suspend fun stopIndex(instanceId: String): ApiResult<Unit>

    /** Dangerous-style confirm required in UI (PRD §8.5/§15.2). */
    suspend fun clearIndex(instanceId: String): ApiResult<Unit>
}
