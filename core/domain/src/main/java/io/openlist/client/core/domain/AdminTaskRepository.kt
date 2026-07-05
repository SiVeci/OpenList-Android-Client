package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminTask
import kotlinx.coroutines.flow.Flow

/**
 * Admin-视角 view of all 7 backend task types (PRD §10.5). Per
 * v0.5_EXECUTION_PLAN.md §7.1/B-503, this is backed by an **in-memory**
 * `StateFlow` in the implementation, not the `remote_tasks` Room table — the
 * ordinary task center's `TaskRepository`/`TaskAggregationRepository` are
 * completely untouched by this interface (zero shared persistence, only
 * shared `core:model`/`TaskStateMapper` code).
 */
interface AdminTaskRepository {
    /** All 7 task types' undone (in-progress-ish) tasks for [instanceId],
     * across every user (admin view). */
    fun observeAdminTasks(instanceId: String): Flow<List<AdminTask>>

    /** Refreshes undone tasks for every one of the 7 backend task types. */
    suspend fun refreshUndone(instanceId: String): ApiResult<Unit>

    /** Refreshes done tasks for one task type (done lists can be large, so
     * unlike undone this is fetched per-type on demand, e.g. when a Tab is
     * opened — PRD §12.5 "undone/done 切换或过滤"). */
    suspend fun refreshDone(instanceId: String, taskType: String): ApiResult<Unit>

    suspend fun getTaskInfo(instanceId: String, taskType: String, tid: String): ApiResult<AdminTask>

    /** Requires two-step confirm in UI (PRD §8.5). */
    suspend fun cancelTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit>

    /** UI must restrict this action to FAILED tasks (server-side behavior for
     * non-failed tasks is unconfirmed — V-505); requires two-step confirm. */
    suspend fun retryTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit>

    /** UI must restrict this action to done-class tasks (server-side behavior
     * for undone tasks is unconfirmed — V-505); requires two-step confirm. */
    suspend fun deleteTaskRecord(instanceId: String, taskType: String, tid: String): ApiResult<Unit>
}
