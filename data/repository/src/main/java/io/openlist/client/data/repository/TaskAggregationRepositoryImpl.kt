package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.database.entity.UploadTaskEntity
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskAggregationRepositoryImpl @Inject constructor(
    private val uploadTaskDao: UploadTaskDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val remoteTaskDao: RemoteTaskDao,
    private val uploadRepository: UploadRepository,
    private val taskRepository: TaskRepository,
    private val transferRepository: TransferRepository,
) : TaskAggregationRepository {

    override fun observeAllTasks(instanceId: String): Flow<List<UnifiedTask>> = combine(
        uploadTaskDao.observeByInstance(instanceId),
        downloadTaskDao.observeByInstance(instanceId),
        remoteTaskDao.observeByInstance(instanceId),
    ) { uploads, downloads, remotes ->
        val tasks = uploads.map { it.toUnifiedTask() } +
            downloads.map { it.toUnifiedTask() } +
            remotes.map { it.toUnifiedTask() }
        tasks.sortedWith(compareBy<UnifiedTask> { it.status.sortRank() }.thenByDescending { it.updatedAt })
    }

    override suspend fun refreshRemoteTasks(instanceId: String): ApiResult<Unit> =
        when (val result = taskRepository.refreshRemoteTasks(instanceId)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }

    override suspend fun refreshDownloadStatuses(instanceId: String) {
        transferRepository.refreshDownloadStatus(instanceId)
    }

    override suspend fun cancelTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> = when (source) {
        TaskSource.LOCAL_UPLOAD -> uploadRepository.cancelUpload(taskId)
        TaskSource.LOCAL_DOWNLOAD -> transferRepository.cancelDownload(taskId)
        TaskSource.REMOTE -> {
            val cached = remoteTaskDao.getById(taskId, instanceId)
            if (cached == null) {
                ApiResult.Failure(DomainError.NotFound)
            } else {
                taskRepository.cancelRemoteTask(instanceId, cached.taskType, taskId)
            }
        }
    }

    override suspend fun retryTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> = when (source) {
        TaskSource.LOCAL_UPLOAD -> uploadRepository.retryUpload(taskId)
        TaskSource.LOCAL_DOWNLOAD -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "暂不支持重试下载任务"))
        TaskSource.REMOTE -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "暂不支持重试远程任务"))
    }

    override suspend fun clearFinishedTasks(instanceId: String, source: TaskSource): ApiResult<Unit> = when (source) {
        TaskSource.LOCAL_UPLOAD -> uploadRepository.clearFinished(instanceId)
        TaskSource.LOCAL_DOWNLOAD -> transferRepository.clearFinished(instanceId)
        TaskSource.REMOTE -> {
            remoteTaskDao.deleteFinishedByInstanceId(instanceId)
            ApiResult.Success(Unit)
        }
    }

    private fun UnifiedTaskStatus.sortRank(): Int = when (this) {
        UnifiedTaskStatus.RUNNING -> 0
        UnifiedTaskStatus.PENDING -> 1
        UnifiedTaskStatus.FAILED -> 2
        UnifiedTaskStatus.CANCELLED -> 3
        UnifiedTaskStatus.SUCCESS -> 4
        UnifiedTaskStatus.UNKNOWN -> 5
    }

    private fun UploadTaskEntity.toUnifiedTask() = UnifiedTask(
        id = id,
        instanceId = instanceId,
        source = TaskSource.LOCAL_UPLOAD,
        type = TaskType.UPLOAD,
        title = fileName,
        status = runCatching { UnifiedTaskStatus.valueOf(status) }.getOrDefault(UnifiedTaskStatus.UNKNOWN),
        progress = totalBytes?.takeIf { it > 0 }?.let { ((uploadedBytes * 100) / it).toInt().coerceIn(0, 100) },
        path = targetDir,
        localUri = null,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DownloadTaskEntity.toUnifiedTask() = UnifiedTask(
        id = id,
        instanceId = instanceId,
        source = TaskSource.LOCAL_DOWNLOAD,
        type = TaskType.DOWNLOAD,
        title = fileName,
        status = mapDownloadStatus(status),
        progress = progress,
        path = path,
        localUri = localUri,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /** Only "ENQUEUED" is produced before P9 (S5-T5) wires real DownloadManager
     * status strings in; the rest are forward-compatible with that work. */
    private fun mapDownloadStatus(raw: String): UnifiedTaskStatus = when (raw) {
        "ENQUEUED", "PENDING" -> UnifiedTaskStatus.PENDING
        "RUNNING" -> UnifiedTaskStatus.RUNNING
        "SUCCESS", "SUCCESSFUL" -> UnifiedTaskStatus.SUCCESS
        "FAILED" -> UnifiedTaskStatus.FAILED
        "CANCELLED" -> UnifiedTaskStatus.CANCELLED
        else -> UnifiedTaskStatus.UNKNOWN
    }

    private fun RemoteTaskEntity.toUnifiedTask() = UnifiedTask(
        id = id,
        instanceId = instanceId,
        source = TaskSource.REMOTE,
        type = taskType.toTaskType(),
        title = title,
        status = runCatching { UnifiedTaskStatus.valueOf(status) }.getOrDefault(UnifiedTaskStatus.UNKNOWN),
        progress = progress,
        path = targetPath,
        localUri = null,
        errorMessage = errorMessage,
        createdAt = startTime ?: cachedAt,
        updatedAt = endTime ?: cachedAt,
    )

    private fun String.toTaskType(): TaskType = when (this) {
        "offline_download", "offline_download_transfer" -> TaskType.OFFLINE_DOWNLOAD
        "copy" -> TaskType.COPY
        "move" -> TaskType.MOVE
        "upload" -> TaskType.UPLOAD
        "decompress", "decompress_upload" -> TaskType.EXTRACT
        else -> TaskType.UNKNOWN
    }
}
