package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminTaskRepository
import io.openlist.client.core.model.AdminTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real implementation (7-type concurrent undone refresh,
 * in-memory StateFlow, no `remote_tasks` writes per B-503) lands in S5
 * (v0.5_EXECUTION_PLAN.md §11 S5-T1). */
@Singleton
class AdminTaskRepositoryImpl @Inject constructor() : AdminTaskRepository {

    override fun observeAdminTasks(instanceId: String): Flow<List<AdminTask>> = flowOf(emptyList())

    override suspend fun refreshUndone(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun refreshDone(instanceId: String, taskType: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getTaskInfo(instanceId: String, taskType: String, tid: String): ApiResult<AdminTask> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun cancelTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun retryTask(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun deleteTaskRecord(instanceId: String, taskType: String, tid: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))
}
