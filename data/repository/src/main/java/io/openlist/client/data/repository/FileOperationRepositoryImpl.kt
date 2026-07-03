package io.openlist.client.data.repository

import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.BatchOperationFailure
import io.openlist.client.core.model.BatchOperationResult
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.MkdirReq
import io.openlist.client.core.network.dto.MoveCopyReq
import io.openlist.client.core.network.dto.RemoveReq
import io.openlist.client.core.network.dto.RenameReq
import io.openlist.client.core.network.safeApiCallUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOperationRepositoryImpl @Inject constructor(
    private val fileCacheDao: FileCacheDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val sessionManager: SessionManager,
) : FileOperationRepository {

    override suspend fun mkdir(instanceId: String, parentPath: String, name: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalizedParent = OpenListPathCodec.normalize(parentPath)
        val result = safeApiCallUnit { api.fsMkdir(MkdirReq(path = OpenListPathCodec.child(normalizedParent, name))) }
        onUnauthorized(instanceId, result)
        if (result is ApiResult.Success) {
            fileCacheDao.clearDirectory(instanceId, normalizedParent)
        }
        return result
    }

    override suspend fun rename(instanceId: String, path: String, newName: String): ApiResult<Unit> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalizedPath = OpenListPathCodec.normalize(path)
        val result = safeApiCallUnit { api.fsRename(RenameReq(path = normalizedPath, name = newName)) }
        onUnauthorized(instanceId, result)
        if (result is ApiResult.Success) {
            fileCacheDao.clearDirectory(instanceId, OpenListPathCodec.parent(normalizedPath))
            fileCacheDao.deleteByPathPrefix(instanceId, normalizedPath)
        }
        return result
    }

    override suspend fun remove(instanceId: String, dir: String, names: List<String>): ApiResult<BatchOperationResult> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalizedDir = OpenListPathCodec.normalize(dir)

        val failures = mutableListOf<BatchOperationFailure>()
        var successCount = 0
        for (name in names) {
            val itemPath = OpenListPathCodec.child(normalizedDir, name)
            val result = safeApiCallUnit { api.fsRemove(RemoveReq(dir = normalizedDir, names = listOf(name))) }
            when (result) {
                is ApiResult.Success -> {
                    successCount++
                    fileCacheDao.deleteByPathPrefix(instanceId, itemPath)
                }
                is ApiResult.Failure -> {
                    failures += BatchOperationFailure(itemPath, result.error.toUserMessage())
                    if (result.error == DomainError.Unauthorized) {
                        sessionManager.invalidate(instanceId)
                        failures += names.dropWhile { it != name }.drop(1)
                            .map { BatchOperationFailure(OpenListPathCodec.child(normalizedDir, it), result.error.toUserMessage()) }
                        break
                    }
                }
            }
        }
        if (successCount > 0) fileCacheDao.clearDirectory(instanceId, normalizedDir)
        return ApiResult.Success(
            BatchOperationResult(total = names.size, successCount = successCount, failedCount = failures.size, failedItems = failures),
        )
    }

    override suspend fun move(
        instanceId: String,
        srcDir: String,
        targetDir: String,
        names: List<String>,
    ): ApiResult<BatchOperationResult> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalizedSrc = OpenListPathCodec.normalize(srcDir)
        val normalizedTarget = OpenListPathCodec.normalize(targetDir)

        val failures = mutableListOf<BatchOperationFailure>()
        var successCount = 0
        for (name in names) {
            val itemPath = OpenListPathCodec.child(normalizedSrc, name)
            val result = safeApiCallUnit {
                api.fsMove(MoveCopyReq(srcDir = normalizedSrc, dstDir = normalizedTarget, names = listOf(name)))
            }
            when (result) {
                is ApiResult.Success -> {
                    successCount++
                    fileCacheDao.deleteByPathPrefix(instanceId, itemPath)
                }
                is ApiResult.Failure -> {
                    failures += BatchOperationFailure(itemPath, result.error.toUserMessage())
                    if (result.error == DomainError.Unauthorized) {
                        sessionManager.invalidate(instanceId)
                        failures += names.dropWhile { it != name }.drop(1)
                            .map { BatchOperationFailure(OpenListPathCodec.child(normalizedSrc, it), result.error.toUserMessage()) }
                        break
                    }
                }
            }
        }
        if (successCount > 0) {
            fileCacheDao.clearDirectory(instanceId, normalizedSrc)
            fileCacheDao.clearDirectory(instanceId, normalizedTarget)
        }
        return ApiResult.Success(
            BatchOperationResult(total = names.size, successCount = successCount, failedCount = failures.size, failedItems = failures),
        )
    }

    override suspend fun copy(
        instanceId: String,
        srcDir: String,
        targetDir: String,
        names: List<String>,
    ): ApiResult<BatchOperationResult> {
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val normalizedSrc = OpenListPathCodec.normalize(srcDir)
        val normalizedTarget = OpenListPathCodec.normalize(targetDir)

        val failures = mutableListOf<BatchOperationFailure>()
        var successCount = 0
        for (name in names) {
            val itemPath = OpenListPathCodec.child(normalizedSrc, name)
            val result = safeApiCallUnit {
                api.fsCopy(MoveCopyReq(srcDir = normalizedSrc, dstDir = normalizedTarget, names = listOf(name)))
            }
            when (result) {
                is ApiResult.Success -> {
                    successCount++
                    fileCacheDao.deleteByPathPrefix(instanceId, OpenListPathCodec.child(normalizedTarget, name))
                }
                is ApiResult.Failure -> {
                    failures += BatchOperationFailure(itemPath, result.error.toUserMessage())
                    if (result.error == DomainError.Unauthorized) {
                        sessionManager.invalidate(instanceId)
                        failures += names.dropWhile { it != name }.drop(1)
                            .map { BatchOperationFailure(OpenListPathCodec.child(normalizedSrc, it), result.error.toUserMessage()) }
                        break
                    }
                }
            }
        }
        if (successCount > 0) fileCacheDao.clearDirectory(instanceId, normalizedTarget)
        return ApiResult.Success(
            BatchOperationResult(total = names.size, successCount = successCount, failedCount = failures.size, failedItems = failures),
        )
    }

    private suspend fun apiFor(instanceId: String): OpenListApi? {
        val instance = instanceRepository.getById(instanceId) ?: return null
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        return clientFactory.apiFor(instance.baseUrl)
    }

    private suspend fun onUnauthorized(instanceId: String, result: ApiResult<*>) {
        if (result is ApiResult.Failure && result.error == DomainError.Unauthorized) {
            sessionManager.invalidate(instanceId)
        }
    }
}
