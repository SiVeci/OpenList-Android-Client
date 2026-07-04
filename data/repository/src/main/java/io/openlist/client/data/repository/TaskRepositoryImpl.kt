package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.model.RemoteTask
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.TaskInfoDto
import io.openlist.client.core.network.safeApiCall
import io.openlist.client.core.network.safeApiCallUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val remoteTaskDao: RemoteTaskDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val json: Json,
) : TaskRepository {

    override fun observeRemoteTasks(instanceId: String): Flow<List<RemoteTask>> =
        remoteTaskDao.observeByInstance(instanceId).map { list -> list.map { it.toRemoteTask() } }

    override suspend fun refreshRemoteTasks(instanceId: String): ApiResult<List<RemoteTask>> = coroutineScope {
        val instance = instanceRepository.getById(instanceId) ?: return@coroutineScope ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)

        val perType = POLLED_TYPES.map { type -> async { type to fetchType(api, type) } }.map { it.await() }

        // Total failure (every polled type errored) surfaces as one Failure and
        // leaves the cache untouched (§18.3); a partial failure keeps the
        // failed type's previously-cached rows and still applies successes.
        if (perType.all { (_, result) -> result is ApiResult.Failure }) {
            return@coroutineScope perType.first().second as ApiResult.Failure
        }

        val now = System.currentTimeMillis()
        val allEntities = mutableListOf<RemoteTaskEntity>()
        for ((type, result) in perType) {
            if (result is ApiResult.Success) {
                val entities = result.data.map { it.toRemoteTaskEntity(instanceId, type, now, json) }
                remoteTaskDao.replaceType(instanceId, type, entities)
                allEntities += entities
            }
        }
        ApiResult.Success(allEntities.map { it.toRemoteTask() })
    }

    override suspend fun cancelRemoteTask(instanceId: String, taskType: String, taskId: String): ApiResult<Unit> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return safeApiCallUnit { api.taskCancel(taskType, taskId) }
    }

    private suspend fun fetchType(api: OpenListApi, type: String): ApiResult<List<TaskInfoDto>> {
        val undone = safeApiCall { api.taskUndone(type) }
        val done = safeApiCall { api.taskDone(type) }
        return when {
            undone is ApiResult.Success && done is ApiResult.Success -> ApiResult.Success(undone.data + done.data)
            undone is ApiResult.Success -> undone
            done is ApiResult.Success -> done
            else -> undone
        }
    }

    private companion object {
        // P7: only the types relevant to client-triggered actions are polled in v0.3.
        val POLLED_TYPES = listOf("offline_download", "offline_download_transfer", "copy", "move")
    }
}
