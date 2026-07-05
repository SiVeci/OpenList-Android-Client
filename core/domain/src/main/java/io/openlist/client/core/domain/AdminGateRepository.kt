package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminAccessState
import kotlinx.coroutines.flow.Flow

/**
 * Front gate for the entire v0.5 admin console (PRD §10.2, v0.5_EXECUTION_PLAN.md
 * §7.1). Every other Admin* repository call must happen only after this
 * reports [AdminAccessState.ALLOWED] for the current instance — but that is a
 * client-side pre-check only; a 401/403 from any admin API endpoint is
 * still authoritative and must be handled even if this repository previously
 * said ALLOWED (local admin-role judgment can be stale/wrong — PRD §17.4.5).
 */
interface AdminGateRepository {
    /** Forces a fresh `/api/me` check (via `AuthRepository.refreshCurrentUser`)
     * and derives the current [AdminAccessState] for [instanceId]. */
    suspend fun checkAccess(instanceId: String): ApiResult<AdminAccessState>

    /** Reactive view of the last known access state for [instanceId], for UI
     * that needs to react to a session becoming invalid while the admin
     * console is open (e.g. a 401 from any Admin* repository call). */
    fun observeAccess(instanceId: String): Flow<AdminAccessState>
}
