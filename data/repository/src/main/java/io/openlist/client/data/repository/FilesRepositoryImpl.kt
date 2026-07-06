package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.entity.FileCacheEntity
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.DirectoryCapability
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.model.Session
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.FsGetResp
import io.openlist.client.core.network.dto.FsListReq
import io.openlist.client.core.network.dto.ObjResp
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilesRepositoryImpl @Inject constructor(
    private val fileCacheDao: FileCacheDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : FilesRepository {

    override fun listDirectory(instanceId: String, path: String, forceRefresh: Boolean): Flow<FileListResult> = flow {
        val normalizedPath = OpenListPathCodec.normalize(path)
        val cachedEntities = fileCacheDao.getByParent(instanceId, normalizedPath)
        val cachedAt = fileCacheDao.getCachedAt(instanceId, normalizedPath)
        val cachedNodes = cachedEntities.map { it.toDomain() }
        val cacheIsFresh = cachedAt != null && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MILLIS

        if (cachedNodes.isNotEmpty()) {
            emit(FileListResult.Cached(cachedNodes, cachedAt ?: 0L))
            // Cache is within TTL and this isn't an explicit pull-to-refresh:
            // skip the network round-trip entirely.
            if (cacheIsFresh && !forceRefresh) return@flow
        }

        val instance = instanceRepository.getById(instanceId)
        if (instance == null) {
            emit(FileListResult.Error(DomainError.InvalidInstance, cachedNodes.ifEmpty { null }))
            return@flow
        }
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        // `refresh` is always false here — see FilesRepository's kdoc: it's an
        // OpenList server-side rescan gated on write permission, not this app's
        // local cache invalidation.
        when (val result = safeApiCall { api.fsList(FsListReq(path = normalizedPath, refresh = false)) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val nodes = result.data.content.map { it.toDomainNode(normalizedPath) }
                fileCacheDao.replaceDirectory(
                    instanceId,
                    normalizedPath,
                    nodes.map { it.toEntity(instanceId, normalizedPath, now) },
                )
                emit(FileListResult.Fresh(nodes, resolveCapability(instanceId, result.data.write)))
            }
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                emit(FileListResult.Error(result.error, cachedNodes.ifEmpty { null }))
            }
        }
    }

    override suspend fun getFile(instanceId: String, path: String): ApiResult<FileDetail> {
        val normalizedPath = OpenListPathCodec.normalize(path)
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        return when (val result = safeApiCall { api.fsGet(FsGetReq(path = normalizedPath)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomainDetail(normalizedPath, instance.baseUrl))
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
    }

    override suspend fun clearAllCache() {
        fileCacheDao.clearAll()
    }

    /**
     * v1.0 V-604: `fs/list`'s `write` field alone only reflects a server-side
     * meta ACL whitelist, not the user's own upload permission bit — the
     * backend's actual upload gate requires both. A session-less lookup
     * (shouldn't normally happen while browsing) is treated as no write
     * capability rather than unknown, matching the "no confirmed grant ⇒ no"
     * default for a definite (non-cache) response.
     */
    private suspend fun resolveCapability(instanceId: String, writeField: Boolean): DirectoryCapability {
        val canWriteContent = authRepository.getSession(instanceId)?.canDo(Session.PERM_WRITE) ?: false
        return DirectoryCapability(canWrite = writeField && canWriteContent)
    }

    private fun FsGetResp.toDomainDetail(path: String, instanceBaseUrl: String): FileDetail {
        // raw_url already accounts for proxy/direct/signing server-side; only
        // fall back to building a signed /d/ URL if the server sent none.
        val resolvedUrl = rawUrl.ifBlank {
            OpenListPathCodec.buildDownloadUrl(instanceBaseUrl, path, sign).orEmpty()
        }
        return FileDetail(
            name = name,
            path = path,
            isDir = isDir,
            size = size,
            modifiedAt = parseTimestamp(modified),
            type = type,
            sign = sign,
            rawUrl = resolvedUrl,
            provider = provider,
        )
    }

    private fun ObjResp.toDomainNode(parentPath: String) = FileNode(
        name = name,
        path = OpenListPathCodec.child(parentPath, name),
        isDir = isDir,
        size = size,
        modifiedAt = parseTimestamp(modified),
        sign = sign,
        thumb = thumb,
        type = type,
    )

    private fun FileNode.toEntity(instanceId: String, parentPath: String, cachedAt: Long) = FileCacheEntity(
        instanceId = instanceId,
        path = path,
        parentPath = parentPath,
        name = name,
        isDir = isDir,
        size = size,
        modifiedAt = modifiedAt,
        sign = sign,
        thumb = thumb,
        type = type,
        cachedAt = cachedAt,
    )

    private fun FileCacheEntity.toDomain() = FileNode(
        name = name,
        path = path,
        isDir = isDir,
        size = size,
        modifiedAt = modifiedAt,
        sign = sign,
        thumb = thumb,
        type = type,
    )

    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}
