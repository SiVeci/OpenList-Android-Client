package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminIndexRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminIndexProgress
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminIndexProgressDto
import io.openlist.client.core.network.dto.AdminIndexUpdateReq
import io.openlist.client.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S6 implementation (v0.5_EXECUTION_PLAN.md §11 S6-T1), replacing the S1
 * stub. [getProgress] is deliberately **not** cached (`admin_cache` or
 * otherwise) -- the plan is explicit that index progress is polled live
 * whenever the Index tab is visible (PRD §13.1.4 "不缓存，页面可见时轮询"), unlike
 * [AdminStorageRepositoryImpl.getStorages]/[AdminUserRepositoryImpl.getUsers]'s
 * 30s/1min TTL caches. `build`/`update`/`stop`/`clear` share the same
 * [safeAdminOperationCall] write-endpoint pattern [AdminStorageRepositoryImpl]
 * (S4) and [AdminTaskRepositoryImpl] (S5) already established -- see
 * `AdminOperationSupport.kt`'s KDoc for why this Sprint is what triggered
 * extracting it out of a 3rd private duplicate.
 *
 * No file/preview cache invalidation is performed here on any operation:
 * v0.5_EXECUTION_PLAN.md §10.3's cache-linkage table only covers storage
 * enable/disable/reload-all, not the search index -- building/clearing the
 * search index doesn't change what files exist or how they're fetched, only
 * how they're *found* via search, so there is nothing in `FileCacheDao`/
 * `PreviewRepository` for these operations to invalidate.
 */
@Singleton
class AdminIndexRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
) : AdminIndexRepository {

    override suspend fun getProgress(instanceId: String): ApiResult<AdminIndexProgress> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCall { api.adminIndexProgress() }
        onUnauthorized(instanceId, result)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun buildIndex(instanceId: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { api.adminIndexBuild() }
        onUnauthorized(instanceId, result)
        return result
    }

    override suspend fun updateIndex(instanceId: String, paths: List<String>, maxDepth: Int): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { api.adminIndexUpdate(AdminIndexUpdateReq(paths = paths, maxDepth = maxDepth)) }
        onUnauthorized(instanceId, result)
        return result
    }

    override suspend fun stopIndex(instanceId: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { api.adminIndexStop() }
        onUnauthorized(instanceId, result)
        return result
    }

    override suspend fun clearIndex(instanceId: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { api.adminIndexClear() }
        onUnauthorized(instanceId, result)
        return result
    }

    private suspend fun apiFor(instanceId: String) = instanceRepository.getById(instanceId)?.let { instance ->
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        clientFactory.apiFor(instance.baseUrl)
    }

    private suspend fun onUnauthorized(instanceId: String, result: ApiResult<*>) {
        if (result is ApiResult.Failure && result.error == DomainError.Unauthorized) {
            sessionManager.invalidate(instanceId)
        }
    }
}

/**
 * DTO -> domain mapping. Defensive by construction, not by a `try/catch`
 * wrapper: every [AdminIndexProgressDto] field already has a decode-time
 * default ([AdminIndexProgressDto.objCount]/[isDone] default to `0`/`false`,
 * `error` defaults to `""`), so a malformed/missing field from the backend
 * never throws here -- it just falls back to that same default, same as
 * every other admin DTO mapper in this module (see [AdminStorageDto.toDomain]
 * `mountDetails`-absent handling).
 *
 * [isRunning] (V-506) is derived as `!isDone && error.isNullOrBlank()` -- i.e.
 * "actively indexing" means "not yet reported done, and no error recorded".
 * This is **client-derived, provisional** (per [AdminIndexProgress.isRunning]'s
 * KDoc): the backend's `search.Running()` boolean (an in-process atomic flag)
 * is never itself serialized onto `IndexProgress`/`GetProgress`'s response.
 * Cross-checked against `openlist-ref/server/handles/index.go`'s three
 * `WriteProgress` call sites (S6, `openlist-ref/internal/search/build.go`):
 * - Normal completion: `IsDone=true, LastDoneTime=now, Error=""`.
 * - Completion with an error: `IsDone=true, LastDoneTime=now, Error=err.Error()`.
 * - Mid-progress ticks (still running): `IsDone=false` (no error field set,
 *   defaults to Go zero value `""`).
 * - `StopIndex` (`server/handles/index.go` `StopIndex`) sends a signal on the
 *   in-memory `quit` channel that `BuildIndex`'s goroutine (`internal/search
 *   /build.go`) is listening on; that goroutine's `quit` case *does* call
 *   `WriteProgress` with `IsDone=true, LastDoneTime=now` (and `Error=""` if
 *   nothing had failed) before exiting -- so yes, a `stop` does leave
 *   `is_done=true` behind, confirming `isRunning` correctly flips to `false`
 *   right after a stop completes. There is a narrow race window between the
 *   `StopIndex` HTTP call returning (fire-and-forget on the channel signal)
 *   and the goroutine actually finishing/writing that final progress record,
 *   during which a `getProgress` poll could still observe `is_done=false` --
 *   this is inherent to the backend's own async design, not something this
 *   derivation can paper over, and is an accepted "briefly stale" reading
 *   (the next poll tick corrects it).
 */
internal fun AdminIndexProgressDto.toDomain(): AdminIndexProgress {
    val normalizedError = error.ifBlank { null }
    return AdminIndexProgress(
        objCount = objCount,
        isDone = isDone,
        lastDoneTime = lastDoneTime?.let { parseIsoTimestamp(it) },
        error = normalizedError,
        isRunning = !isDone && normalizedError == null,
    )
}
