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

    /**
     * DEC-504 (S6 resolution): [paths] defaults to `["/"]` (whole tree) so the
     * UI doesn't need a directory-picker for v0.5 (PRD's picker is an optional
     * "时间允许再做" enhancement, explicitly skipped). [maxDepth] defaults to
     * `-1` -- `openlist-ref/internal/fs/walk.go WalkFS` only stops recursing
     * when `depth == 0`, so `0` (the backend struct's own Go zero value, and
     * `AdminIndexUpdateReq`'s pre-S6 default) means "this path only, do not
     * recurse into subdirectories", which is almost never what "update index
     * under /" should mean. `-1` decrements indefinitely (`-1, -2, -3, ...`)
     * and therefore never trips that `depth == 0` short-circuit, i.e. it
     * behaves as "no depth limit" for any real (finite) directory tree --
     * matching the intent of a full re-index under [paths] without hardcoding
     * the server's own configurable `BuildIndex` default (`setting.GetInt(
     * conf.MaxIndexDepth, 20)`, only used by `admin/index/build`, not
     * `update`). This is a documented best-effort choice (V-506), not a
     * confirmed backend contract for what "no limit" should be.
     */
    suspend fun updateIndex(instanceId: String, paths: List<String> = listOf("/"), maxDepth: Int = -1): ApiResult<Unit>

    /** Requires two-step confirm in UI (PRD §8.5). */
    suspend fun stopIndex(instanceId: String): ApiResult<Unit>

    /** Dangerous-style confirm required in UI (PRD §8.5/§15.2). */
    suspend fun clearIndex(instanceId: String): ApiResult<Unit>
}
