package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.entity.ShareEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareWriteRequest
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.SharingResp
import io.openlist.client.core.network.dto.UpdateSharingReq
import io.openlist.client.core.network.safeApiCall
import io.openlist.client.core.network.safeApiCallUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepositoryImpl @Inject constructor(
    private val shareDao: ShareDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val json: Json,
) : ShareRepository {

    override fun observeShares(instanceId: String): Flow<List<Share>> =
        shareDao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override suspend fun listShares(instanceId: String): ApiResult<List<Share>> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val result = safeApiCall { api.shareList(page = 1, perPage = PAGE_SIZE) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val entities = result.data.content.map { it.toEntity(instanceId, now) }
                shareDao.replaceAll(instanceId, entities)
                ApiResult.Success(entities.map { it.toDomain() })
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getShare(instanceId: String, id: String): ApiResult<Share> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val result = safeApiCall { api.shareGet(id) }) {
            is ApiResult.Success -> {
                val entity = result.data.toEntity(instanceId, System.currentTimeMillis())
                shareDao.upsert(entity)
                ApiResult.Success(entity.toDomain())
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun createShare(instanceId: String, request: ShareWriteRequest): ApiResult<Share> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val req = request.toReq(id = "")
        return when (val result = safeApiCall { api.shareCreate(req) }) {
            is ApiResult.Success -> {
                val entity = result.data.toEntity(instanceId, System.currentTimeMillis())
                shareDao.upsert(entity)
                ApiResult.Success(entity.toDomain())
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun updateShare(instanceId: String, id: String, request: ShareWriteRequest): ApiResult<Share> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val req = request.toReq(id = id)
        return when (val result = safeApiCall { api.shareUpdate(req) }) {
            is ApiResult.Success -> {
                val entity = result.data.toEntity(instanceId, System.currentTimeMillis())
                shareDao.upsert(entity)
                ApiResult.Success(entity.toDomain())
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun enableShare(instanceId: String, id: String): ApiResult<Unit> = setEnabled(instanceId, id, enabled = true)

    override suspend fun disableShare(instanceId: String, id: String): ApiResult<Unit> = setEnabled(instanceId, id, enabled = false)

    private suspend fun setEnabled(instanceId: String, id: String, enabled: Boolean): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCallUnit { if (enabled) api.shareEnable(id) else api.shareDisable(id) }
        if (result is ApiResult.Success) {
            val cached = shareDao.getById(id, instanceId)
            if (cached != null) shareDao.upsert(cached.copy(enabled = enabled))
        }
        return result
    }

    override suspend fun deleteShare(instanceId: String, id: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val result = safeApiCallUnit { api.shareDelete(id) }
        if (result is ApiResult.Success) shareDao.deleteById(id, instanceId)
        return result
    }

    /** `/@s/{sid}` matches the backend's `/sd/:sid` download-link family
     * (server/router.go); pending confirmation against a real Web share page (V-01). */
    override fun buildShareUrl(instanceBaseUrl: String, id: String): String =
        "${instanceBaseUrl.trimEnd('/')}/@s/$id"

    private suspend fun apiFor(instanceId: String): OpenListApi? {
        val instance = instanceRepository.getById(instanceId) ?: return null
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        return clientFactory.apiFor(instance.baseUrl)
    }

    private fun ShareWriteRequest.toReq(id: String) = UpdateSharingReq(
        id = id,
        files = paths,
        expires = expiresAt?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC).toString() },
        pwd = password.orEmpty(),
        maxAccessed = maxAccessed,
        disabled = disabled,
        remark = name.orEmpty(),
    )

    private fun SharingResp.toEntity(instanceId: String, cachedAt: Long) = ShareEntity(
        id = id,
        instanceId = instanceId,
        filesJson = json.encodeToString(files),
        primaryPath = files.firstOrNull().orEmpty(),
        name = remark.ifBlank { null },
        shareUrl = null,
        password = pwd.ifBlank { null },
        enabled = !disabled,
        expiresAt = expires?.let { parseTimestamp(it) },
        accessed = accessed,
        maxAccessed = maxAccessed,
        creator = creator.ifBlank { null },
        rawJson = json.encodeToString(this),
        createdAt = null,
        updatedAt = null,
        cachedAt = cachedAt,
    )

    private fun ShareEntity.toDomain() = Share(
        id = id,
        instanceId = instanceId,
        paths = runCatching { json.decodeFromString<List<String>>(filesJson) }.getOrDefault(listOf(primaryPath)),
        name = name,
        shareUrl = shareUrl,
        password = password,
        enabled = enabled,
        expiresAt = expiresAt,
        accessed = accessed,
        maxAccessed = maxAccessed,
        creator = creator,
    )

    private fun parseTimestamp(raw: String): Long? =
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()

    private companion object {
        const val PAGE_SIZE = 100
    }
}
