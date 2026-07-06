package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.entity.ShareEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareInboundInfo
import io.openlist.client.core.model.ShareInboundTarget
import io.openlist.client.core.model.ShareWriteRequest
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.ShareUrlParser
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.SharingResp
import io.openlist.client.core.network.dto.UpdateSharingReq
import io.openlist.client.core.network.safeApiCall
import io.openlist.client.core.network.safeApiCallUnit
import io.openlist.client.core.network.toApiResult
import io.openlist.client.core.network.toDomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /** `/@s/{sid}` — confirmed v1.0 V-607a against `server/router.go`/
     * `internal/bootstrap/data/setting.go`'s `{{base_url}}/@s/{{id}}` template. */
    override fun buildShareUrl(instanceBaseUrl: String, id: String): String =
        "${instanceBaseUrl.trimEnd('/')}/@s/$id"

    override suspend fun resolveInboundUrl(url: String): ShareInboundTarget? {
        val parsed = ShareUrlParser.parse(url) ?: return null
        val instances = instanceRepository.observeAll().first()
        val match = instances.firstOrNull { instance ->
            val instanceUrl = instance.baseUrl.toHttpUrlOrNull() ?: return@firstOrNull false
            instanceUrl.scheme == parsed.scheme && instanceUrl.host == parsed.host && instanceUrl.port == parsed.port
        } ?: return null
        return ShareInboundTarget(
            instanceId = match.id,
            baseUrl = match.baseUrl,
            sid = parsed.sid,
            path = parsed.path,
            sourceUrl = url,
        )
    }

    override suspend fun getInboundShare(
        instanceId: String,
        sid: String,
        path: String?,
        password: String?,
    ): ApiResult<ShareInboundInfo> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val sharePath = buildSharePath(sid, path)
        return try {
            val response = api.fsGet(FsGetReq(path = sharePath, password = password.orEmpty()))
            when (val result = response.toApiResult()) {
                is ApiResult.Success -> ApiResult.Success(
                    ShareInboundInfo(
                        sid = sid,
                        path = path.orEmpty(),
                        name = result.data.name,
                        isDir = result.data.isDir,
                        size = result.data.size,
                        rawUrl = result.data.rawUrl,
                    ),
                )
                is ApiResult.Failure -> ApiResult.Failure(mapShareError(response.code, response.message, result.error))
            }
        } catch (t: Throwable) {
            ApiResult.Failure(t.toDomainError())
        }
    }

    private fun buildSharePath(sid: String, path: String?): String =
        if (path.isNullOrBlank()) "/@s/$sid" else "/@s/$sid/${path.trimStart('/')}"

    /**
     * V-607c: wrong/missing share password is HTTP 200 + envelope `code=403,
     * message="wrong share code"` — indistinguishable from a real permission
     * error by code alone, so the message is inspected here (not in shared
     * `codeToDomainError`, which is share-agnostic) before falling back to the
     * generic mapping. Expired/disabled shares (envelope `code=500`) and any
     * other share-specific failure keep the backend's own message rather than
     * collapsing to the generic `ServerError` copy, since it's the more
     * specific and actionable text (PRD §12.2). 401 is left to the normal
     * Unauthorized handling — this repository never touches SessionManager
     * itself, matching every other method in this file.
     */
    private fun mapShareError(code: Int, message: String, fallback: DomainError): DomainError = when {
        code == 401 -> fallback
        code == 403 && message.contains("wrong share code", ignoreCase = true) -> DomainError.SharePasswordRequired
        code in 400..599 -> DomainError.OpenListError(code, message)
        else -> fallback
    }

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
