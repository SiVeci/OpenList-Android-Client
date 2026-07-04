package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.SearchHistoryDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.BaseUrlNormalizer
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstanceRepositoryImpl @Inject constructor(
    private val dao: InstanceDao,
    private val sessionDao: SessionDao,
    private val fileCacheDao: FileCacheDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val uploadTaskDao: UploadTaskDao,
    private val shareDao: ShareDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val remoteTaskDao: RemoteTaskDao,
    private val clientFactory: OpenListClientFactory,
) : InstanceRepository {

    override fun observeAll(): Flow<List<Instance>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Instance? = dao.getById(id)?.toDomain()

    override suspend fun getCurrent(): Instance? = dao.getCurrent()?.toDomain()

    override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> {
        val normalized = when (val result = BaseUrlNormalizer.normalize(rawUrl)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        if (dao.getByBaseUrl(normalized.baseUrl) != null) {
            // Client-side validation error (v0.1_PRD §6.3 rule 4), not a server response;
            // OpenListError's free-form message is the only DomainError case built for UI copy.
            return ApiResult.Failure(DomainError.OpenListError(code = null, message = "该实例地址已添加"))
        }
        val now = System.currentTimeMillis()
        val isFirstInstance = dao.count() == 0
        val entity = InstanceEntity(
            id = UUID.randomUUID().toString(),
            name = name?.trim().takeUnless { it.isNullOrBlank() } ?: normalized.displayName,
            baseUrl = normalized.baseUrl,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now,
            isCurrent = isFirstInstance,
            note = note?.trim().takeUnless { it.isNullOrBlank() },
        )
        dao.upsert(entity)
        return ApiResult.Success(entity.toDomain())
    }

    override suspend fun setCurrent(id: String) {
        val entity = dao.getById(id) ?: return
        dao.clearCurrentExcept(id)
        dao.update(entity.copy(isCurrent = true, lastUsedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(id: String) {
        sessionDao.deleteByInstanceId(id)
        fileCacheDao.deleteByInstanceId(id)
        downloadTaskDao.deleteByInstanceId(id)
        // Matches downloadTaskDao's existing precedent: clears the local record,
        // does not reach into WorkManager to cancel an in-flight upload — a
        // deleted instance's worker fails its own instance lookup on its next
        // step and stops there.
        uploadTaskDao.deleteByInstanceId(id)
        shareDao.deleteByInstanceId(id)
        searchHistoryDao.deleteByInstanceId(id)
        remoteTaskDao.deleteByInstanceId(id)
        dao.deleteById(id)
    }

    override suspend fun testConnection(baseUrl: String): ApiResult<Unit> {
        val api = clientFactory.apiFor(baseUrl)
        val pingOk = runCatching { api.ping().isSuccessful }.getOrDefault(false)
        if (pingOk) return ApiResult.Success(Unit)
        return safeApiCall { api.publicSettings() }.let { result ->
            when (result) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                is ApiResult.Failure -> result
            }
        }
    }

    private fun InstanceEntity.toDomain() = Instance(
        id = id,
        name = name,
        baseUrl = baseUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        isCurrent = isCurrent,
        note = note,
    )
}
