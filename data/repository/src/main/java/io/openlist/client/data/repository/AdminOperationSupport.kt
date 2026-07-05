package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.toDomainError

/**
 * Shared write-endpoint error handling for admin "operation" calls (enable/
 * disable/reload-all storage, cancel/retry/delete task, build/update/stop/
 * clear index -- v0.5_EXECUTION_PLAN.md §11 S4-T1/S5-T1/S6-T1). Every
 * non-2xx/non-401 failure is kept as [DomainError.OpenListError] (backend
 * `message` verbatim) instead of the shared [io.openlist.client.core.network
 * .safeApiCallUnit]'s bucketed [DomainError.ServerError]/[DomainError
 * .Forbidden] -- these admin write endpoints fail with a bare HTTP 500/400 +
 * a specific Go error message (e.g. "this storage have enabled", "index is
 * running", V-503/V-505/V-506), and PRD §13.2.5/§14.2 require that exact
 * message to reach the UI, not a generic bucketed copy. Only 401 keeps its
 * normal [DomainError.Unauthorized] mapping (callers still route it through
 * their own `onUnauthorized`/[io.openlist.client.core.auth.SessionManager
 * .invalidate] pattern).
 *
 * [AdminStorageRepositoryImpl] (S4) and [AdminTaskRepositoryImpl] (S5) each
 * originally kept a small private copy of this function ("extraction isn't
 * warranted yet for two call sites", per those Sprints' briefs). S6 adds a
 * third call site ([AdminIndexRepositoryImpl]) for the same shape, which is
 * the point at which three near-identical private copies risk drifting apart
 * -- extracted here instead, and the two existing call sites switched over so
 * there is exactly one canonical version.
 */
internal suspend fun safeAdminOperationCall(block: suspend () -> ApiResponse<*>): ApiResult<Unit> {
    return try {
        val response = block()
        if (response.code in 200..299) {
            ApiResult.Success(Unit)
        } else if (response.code == 401) {
            ApiResult.Failure(DomainError.Unauthorized)
        } else {
            ApiResult.Failure(DomainError.OpenListError(response.code, response.message))
        }
    } catch (t: Throwable) {
        ApiResult.Failure(t.toDomainError())
    }
}
