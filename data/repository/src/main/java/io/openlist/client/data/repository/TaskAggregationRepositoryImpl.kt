package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.database.entity.UploadTaskEntity
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.domain.SystemDocumentsRepository
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.model.SystemDocumentRecoveryAction
import io.openlist.client.core.model.SystemWriteTransactionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val systemWriteTransactionDao: SystemWriteTransactionDao,
    private val systemDocumentsRepository: SystemDocumentsRepository,
    private val instanceDao: InstanceDao,
) : TaskAggregationRepository {

    override fun observeAllTasks(instanceId: String): Flow<List<UnifiedTask>> = combine(
        uploadTaskDao.observeByInstance(instanceId),
        downloadTaskDao.observeByInstance(instanceId),
        remoteTaskDao.observeByInstance(instanceId),
        systemWriteTransactionDao.observeRecoverableByInstance(instanceId),
        instanceDao.observeAll(),
    ) { uploads, downloads, remotes, systemWrites, instances ->
        val instanceName = instances.firstOrNull { it.id == instanceId }?.name
        val tasks = uploads.map { it.toUnifiedTask() } +
            downloads.map { it.toUnifiedTask() } +
            remotes.map { it.toUnifiedTask() } +
            systemWrites.mapNotNull { it.toSystemSaveTaskOrNull(instanceName) }
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
        // P1 only freezes the enum. P6 wires this source to the system-document
        // recovery repository; until then it must fail explicitly, never route
        // a system draft into the ordinary upload pipeline.
        TaskSource.SYSTEM_DOCUMENT -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "系统保存恢复尚未启用"))
    }

    override suspend fun retryTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> {
        if (source == TaskSource.SYSTEM_DOCUMENT) return systemDocumentsRepository.retrySave(taskId)
        return when (source) {
        TaskSource.LOCAL_UPLOAD -> uploadRepository.retryUpload(taskId)
        TaskSource.LOCAL_DOWNLOAD -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "暂不支持重试下载任务"))
        TaskSource.REMOTE -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "暂不支持重试远程任务"))
        TaskSource.SYSTEM_DOCUMENT -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "系统保存恢复尚未启用"))
        }
    }

    override suspend fun clearFinishedTasks(instanceId: String, source: TaskSource): ApiResult<Unit> = when (source) {
        TaskSource.LOCAL_UPLOAD -> uploadRepository.clearFinished(instanceId)
        TaskSource.LOCAL_DOWNLOAD -> transferRepository.clearFinished(instanceId)
        TaskSource.REMOTE -> {
            remoteTaskDao.deleteFinishedByInstanceId(instanceId)
            ApiResult.Success(Unit)
        }
        TaskSource.SYSTEM_DOCUMENT -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "系统保存恢复尚未启用"))
    }

    override suspend fun clearFailedTasks(instanceId: String, source: TaskSource?): ApiResult<Unit> {
        if (source == TaskSource.SYSTEM_DOCUMENT) return deleteFailedSystemDrafts(instanceId)
        return when (source) {
            TaskSource.LOCAL_UPLOAD -> uploadRepository.clearFailed(instanceId)
            TaskSource.LOCAL_DOWNLOAD -> transferRepository.clearFailed(instanceId)
            TaskSource.REMOTE -> {
                remoteTaskDao.deleteFailedByInstanceId(instanceId)
                ApiResult.Success(Unit)
            }
            TaskSource.SYSTEM_DOCUMENT -> ApiResult.Failure(DomainError.OpenListError(code = null, message = "系统保存恢复尚未启用"))
            null -> {
                uploadRepository.clearFailed(instanceId)
                transferRepository.clearFailed(instanceId)
                remoteTaskDao.deleteFailedByInstanceId(instanceId)
                ApiResult.Success(Unit)
            }
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

    private suspend fun deleteFailedSystemDrafts(instanceId: String): ApiResult<Unit> {
        val failed = systemWriteTransactionDao.observeRecoverableByInstance(instanceId).first()
            .filter { it.state == SystemWriteTransactionState.FAILED_DRAFT.name }
        var failure: ApiResult.Failure? = null
        failed.forEach { transaction ->
            when (val result = systemDocumentsRepository.deleteDraft(transaction.transactionId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> if (failure == null) failure = result
            }
        }
        return failure ?: ApiResult.Success(Unit)
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

    private fun io.openlist.client.core.database.entity.SystemWriteTransactionEntity.toSystemSaveTaskOrNull(
        instanceName: String?,
    ): UnifiedTask? {
        val transactionState = runCatching { SystemWriteTransactionState.valueOf(state) }.getOrNull() ?: return null
        val status = when (transactionState) {
            SystemWriteTransactionState.FAILED_DRAFT,
            SystemWriteTransactionState.RECOVERY_REQUIRED -> UnifiedTaskStatus.FAILED
            SystemWriteTransactionState.CLEANUP_PENDING -> UnifiedTaskStatus.RUNNING
            else -> return null
        }
        return UnifiedTask(
            id = transactionId,
            instanceId = instanceId,
            source = TaskSource.SYSTEM_DOCUMENT,
            type = TaskType.SYSTEM_SAVE,
            title = "系统保存：$displayName",
            status = status,
            progress = null,
            path = io.openlist.client.core.network.OpenListPathCodec.parent(targetPath),
            localUri = null,
            errorMessage = when (transactionState) {
                SystemWriteTransactionState.FAILED_DRAFT -> "保存失败，本地草稿将保留至到期或由你处理"
                SystemWriteTransactionState.RECOVERY_REQUIRED -> "正在确认远端保存结果"
                SystemWriteTransactionState.CLEANUP_PENDING -> "正在清理已验证的临时文件"
                else -> null
            },
            createdAt = createdAt,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
            recoveryActions = if (transactionState == SystemWriteTransactionState.FAILED_DRAFT) {
                setOf(SystemDocumentRecoveryAction.RETRY_SAVE, SystemDocumentRecoveryAction.EXPORT_COPY, SystemDocumentRecoveryAction.DELETE_DRAFT)
            } else emptySet(),
            instanceName = instanceName,
            directorySummary = io.openlist.client.core.network.OpenListPathCodec.parent(targetPath)
                .takeIf { it != "/" }
                ?.let(io.openlist.client.core.network.OpenListPathCodec::name)
                ?.takeIf { it.isNotBlank() }
                ?: "根目录",
        )
    }

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
