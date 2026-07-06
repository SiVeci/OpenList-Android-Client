package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminTaskRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminTask
import io.openlist.client.core.model.TaskStateMapper
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.TaskInfoDto
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S5 implementation (v0.5_EXECUTION_PLAN.md §11 S5-T1). Backing store is
 * an **in-memory** `MutableStateFlow<Map<String, List<AdminTask>>>` keyed by
 * `"$instanceId:$taskType:$bucket"` (bucket = undone/done) -- never written to
 * `RemoteTaskDao`/any Room table (B-503): this class does not even take a Room
 * DAO as a constructor dependency, which is the structural proof that no task
 * data is ever persisted, not just a runtime check.
 *
 * [refreshUndone] concurrently fetches all 7 backend task types (mirrors
 * [TaskRepositoryImpl]'s `async`/`awaitAll` + per-type resilience pattern: a
 * single type's failure never blanks its previously-cached entries nor fails
 * the whole refresh, and the overall call only reports [ApiResult.Failure]
 * when *every* type failed). [refreshDone] deliberately only ever touches one
 * type at a time (R-505: done lists are pulled on demand, never as part of
 * the periodic undone poll).
 */
@Singleton
class AdminTaskRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
) : AdminTaskRepository {

    private val tasksByKey = MutableStateFlow<Map<String, List<AdminTask>>>(emptyMap())

    override fun observeAdminTasks(instanceId: String): Flow<List<AdminTask>> =
        tasksByKey.map { map ->
            map.entries.filter { it.key.startsWith("$instanceId:") }.flatMap { it.value }
        }

    override suspend fun refreshUndone(instanceId: String): ApiResult<Unit> = coroutineScope {
        val api = apiFor(instanceId) ?: return@coroutineScope ApiResult.Failure(DomainError.InvalidInstance)

        val perType = TASK_TYPES
            .map { type -> async { type to safeApiCall { api.adminTaskUndone(type) } } }
            .map { it.await() }

        perType.forEach { (_, result) -> onUnauthorized(instanceId, result) }

        // A single type's failure leaves that type/bucket's previously-cached
        // entries untouched -- only successful types overwrite their slice of
        // the in-memory map (mirrors TaskRepositoryImpl.refreshRemoteTasks).
        for ((type, result) in perType) {
            if (result is ApiResult.Success) {
                val mapped = result.data.map { it.toAdminTask(instanceId, type, isDone = false) }
                tasksByKey.update { it + (key(instanceId, type, BUCKET_UNDONE) to mapped) }
            }
        }

        if (perType.all { (_, result) -> result is ApiResult.Failure }) {
            perType.first().second as ApiResult.Failure
        } else {
            ApiResult.Success(Unit)
        }
    }

    override suspend fun refreshDone(instanceId: String, taskType: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCall { api.adminTaskDone(taskType) }
        onUnauthorized(instanceId, result)
        return when (result) {
            is ApiResult.Success -> {
                val mapped = result.data.map { it.toAdminTask(instanceId, taskType, isDone = true) }
                tasksByKey.update { it + (key(instanceId, taskType, BUCKET_DONE) to mapped) }
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getTaskInfo(instanceId: String, taskType: String, tid: String): ApiResult<AdminTask> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCall { api.adminTaskInfo(taskType, tid) }
        onUnauthorized(instanceId, result)
        return when (result) {
            is ApiResult.Success -> {
                // Not written into `tasksByKey` (a one-off detail lookup, not
                // a list refresh) -- `admin/task/{type}/info` doesn't say
                // which bucket (undone/done) the task is in, so `isDone` is
                // approximated here from the mapped terminal/non-terminal
                // state, only for this standalone return value.
                val dto = result.data
                val state = TaskStateMapper.map(dto.state)
                ApiResult.Success(dto.toAdminTask(instanceId, taskType, isDone = state in TERMINAL_STATES))
            }
            is ApiResult.Failure -> result
        }
    }

    /**
     * No client-side state gating (V-505: the backend itself doesn't
     * pre-validate task state for cancel/retry/delete either) -- the call
     * always reaches the backend and its response is trusted as-is. On
     * success, both the undone and done buckets for [taskType] are
     * refreshed rather than optimistically mutated locally: a cancel/retry/
     * delete's effect on which bucket a task lands in isn't something this
     * repository can infer from the tid alone (e.g. a FAILED task the UI is
     * retrying may actually still be sitting in the *undone* list per the
     * backend's own Errored/Failing-are-still-undone semantics -- see
     * `TaskStateMapper`'s KDoc), so refreshing both is the correctness-first
     * choice over guessing a single list.
     */
    override suspend fun cancelTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskCancel(taskType, tid) }

    override suspend fun retryTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskRetry(taskType, tid) }

    override suspend fun deleteTaskRecord(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskDelete(taskType, tid) }

    override suspend fun clearDone(instanceId: String, taskType: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskClearDone(taskType) }

    override suspend fun clearSucceeded(instanceId: String, taskType: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskClearSucceeded(taskType) }

    override suspend fun retryFailed(instanceId: String, taskType: String): ApiResult<Unit> =
        performTaskOperation(instanceId, taskType) { api -> api.adminTaskRetryFailed(taskType) }

    private suspend fun performTaskOperation(
        instanceId: String,
        taskType: String,
        call: suspend (OpenListApi) -> ApiResponse<*>,
    ): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeAdminOperationCall { call(api) }
        onUnauthorized(instanceId, result)
        if (result is ApiResult.Success) {
            refreshUndone(instanceId)
            refreshDone(instanceId, taskType)
        }
        return result
    }

    // `safeAdminOperationCall` (shared with [AdminStorageRepositoryImpl]/S4,
    // [AdminIndexRepositoryImpl]/S6 -- extracted to `AdminOperationSupport.kt`
    // in S6 once a third call site made "small private duplicate" no longer
    // the simpler option; see that file's KDoc for the full rationale).

    private suspend fun apiFor(instanceId: String) = instanceRepository.getById(instanceId)?.let { instance ->
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        clientFactory.apiFor(instance.baseUrl)
    }

    private suspend fun onUnauthorized(instanceId: String, result: ApiResult<*>) {
        if (result is ApiResult.Failure && result.error == DomainError.Unauthorized) {
            sessionManager.invalidate(instanceId)
        }
    }

    private fun key(instanceId: String, taskType: String, bucket: String) = "$instanceId:$taskType:$bucket"

    private companion object {
        // All 7 backend task types (V-505, confirmed against
        // openlist-ref/server/handles/task.go SetupTaskRoute call sites).
        val TASK_TYPES = listOf(
            "upload",
            "copy",
            "move",
            "offline_download",
            "offline_download_transfer",
            "decompress",
            "decompress_upload",
        )
        const val BUCKET_UNDONE = "undone"
        const val BUCKET_DONE = "done"

        // Mirrors the backend's own undone/done split (V-505/server/handles/task.go):
        // Succeeded/Canceled/Failed are `done`; everything else (including the
        // still-in-flight Errored/Failing states) is `undone`.
        val TERMINAL_STATES = setOf(
            UnifiedTaskStatus.SUCCESS,
            UnifiedTaskStatus.CANCELLED,
            UnifiedTaskStatus.FAILED,
        )
    }
}

/**
 * DTO -> domain mapping (reuses [TaskStateMapper], the same `tache.State`
 * mapper [TaskRepositoryImpl]/`RemoteTaskMapping.kt` use for the non-admin
 * task center -- not re-derived here). [AdminTask.progress] is normalized to
 * a `0f..1f` fraction (the backend's `progress` field is `0.0..100.0`) so it
 * matches [io.openlist.client.core.designsystem.components.TaskCard]'s
 * `progress: Float?` contract directly, unlike [io.openlist.client.core.model
 * .RemoteTask.progress] which stores the raw `0..100` Int. [isDone] is
 * supplied by the caller (which endpoint fetched this row), not derived here
 * -- see [AdminTask.isDone]'s KDoc for why state alone can't tell.
 */
internal fun TaskInfoDto.toAdminTask(instanceId: String, taskType: String, isDone: Boolean): AdminTask = AdminTask(
    id = id,
    instanceId = instanceId,
    taskType = taskType,
    name = name,
    creator = creator.ifBlank { null },
    creatorRole = creatorRole,
    state = TaskStateMapper.map(state),
    statusText = status.ifBlank { null },
    progress = (progress / 100.0).toFloat().coerceIn(0f, 1f),
    totalBytes = totalBytes,
    error = error.ifBlank { null },
    startTime = startTime?.let { parseIsoTimestamp(it) },
    endTime = endTime?.let { parseIsoTimestamp(it) },
    isDone = isDone,
)
