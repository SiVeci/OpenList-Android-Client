package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.OfflineDownloadRepository
import io.openlist.client.core.model.RemoteTask
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.AddOfflineDownloadReq
import io.openlist.client.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDownloadRepositoryImpl @Inject constructor(
    private val remoteTaskDao: RemoteTaskDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val json: Json,
) : OfflineDownloadRepository {

    override suspend fun listTools(instanceId: String): ApiResult<List<String>> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val api = clientFactory.apiFor(instance.baseUrl)
        return safeApiCall { api.offlineDownloadTools() }
    }

    override suspend fun addOfflineDownload(instanceId: String, urls: List<String>, targetDir: String, tool: String?): ApiResult<List<RemoteTask>> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        val req = AddOfflineDownloadReq(
            urls = urls,
            path = OpenListPathCodec.normalize(targetDir),
            tool = tool.orEmpty(),
        )
        return when (val result = safeApiCall { api.addOfflineDownload(req) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val entities = result.data.tasks.map { it.toRemoteTaskEntity(instanceId, "offline_download", now, json) }
                if (entities.isNotEmpty()) remoteTaskDao.upsertAll(entities)
                ApiResult.Success(entities.map { it.toRemoteTask() })
            }
            is ApiResult.Failure -> result
        }
    }
}
