package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.FsListReq
import io.openlist.client.core.network.dto.ObjResp
import io.openlist.client.core.network.safeApiCall
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryPickerRepositoryImpl @Inject constructor(
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
) : DirectoryPickerRepository {

    override suspend fun listDirectories(instanceId: String, path: String): ApiResult<List<FileNode>> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        val normalizedPath = OpenListPathCodec.normalize(path)
        val result = safeApiCall { api.fsList(FsListReq(path = normalizedPath, refresh = false)) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.content
                    .filter { it.isDir }
                    .map { it.toDomainNode(normalizedPath) },
            )
            is ApiResult.Failure -> {
                if (result.error == DomainError.Unauthorized) sessionManager.invalidate(instanceId)
                result
            }
        }
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

    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }
}
