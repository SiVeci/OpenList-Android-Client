package io.openlist.client.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.UploadTaskEntity
import io.openlist.client.core.database.entity.UploadTaskStatus
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.model.UploadStatus
import io.openlist.client.core.model.UploadTask
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadTaskDao: UploadTaskDao,
    private val workManager: WorkManager,
) : UploadRepository {

    override suspend fun enqueueUpload(instanceId: String, targetDir: String, localUris: List<Uri>): ApiResult<List<String>> {
        if (localUris.isEmpty()) return ApiResult.Success(emptyList())
        val normalizedTargetDir = OpenListPathCodec.normalize(targetDir)
        val now = System.currentTimeMillis()
        val ids = mutableListOf<String>()
        for (uri in localUris) {
            // Persist read access before this Activity/picker goes away — a
            // Worker may run long after both are gone (P6).
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val (displayName, size, mimeType) = queryMetadata(uri)
            val id = UUID.randomUUID().toString()
            val entity = UploadTaskEntity(
                id = id,
                instanceId = instanceId,
                targetDir = normalizedTargetDir,
                localUri = uri.toString(),
                fileName = displayName ?: id,
                mimeType = mimeType,
                totalBytes = size,
                uploadedBytes = 0L,
                status = UploadTaskStatus.PENDING,
                errorMessage = null,
                workRequestId = null,
                overwrite = false,
                createdAt = now,
                updatedAt = now,
            )
            uploadTaskDao.upsert(entity)
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(UploadWorker.KEY_TASK_ID to id))
                .build()
            workManager.enqueueUniqueWork(uniqueWorkName(id), ExistingWorkPolicy.KEEP, request)
            uploadTaskDao.setWorkRequestId(id, request.id.toString())
            ids += id
        }
        return ApiResult.Success(ids)
    }

    override fun observeUploadTasks(instanceId: String): Flow<List<UploadTask>> =
        uploadTaskDao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override suspend fun cancelUpload(taskId: String): ApiResult<Unit> {
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
        val task = uploadTaskDao.getById(taskId) ?: return ApiResult.Failure(DomainError.NotFound)
        uploadTaskDao.updateProgress(taskId, UploadTaskStatus.CANCELLED, task.uploadedBytes, null, System.currentTimeMillis())
        return ApiResult.Success(Unit)
    }

    override suspend fun retryUpload(taskId: String): ApiResult<Unit> {
        val task = uploadTaskDao.getById(taskId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (task.status != UploadTaskStatus.FAILED) return ApiResult.Failure(DomainError.UploadRetryUnavailable)
        val readable = runCatching {
            context.contentResolver.openInputStream(Uri.parse(task.localUri))?.use { }
        }.isSuccess
        if (!readable) return ApiResult.Failure(DomainError.UploadRetryUnavailable)

        val now = System.currentTimeMillis()
        uploadTaskDao.updateProgress(taskId, UploadTaskStatus.PENDING, 0L, null, now)
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_TASK_ID to taskId))
            .build()
        // REPLACE, not KEEP: the previous attempt's unique work is already
        // terminal (FAILED), so there's nothing to preserve by keeping it.
        workManager.enqueueUniqueWork(uniqueWorkName(taskId), ExistingWorkPolicy.REPLACE, request)
        uploadTaskDao.setWorkRequestId(taskId, request.id.toString())
        return ApiResult.Success(Unit)
    }

    override suspend fun clearFinished(instanceId: String): ApiResult<Unit> {
        uploadTaskDao.deleteFinishedByInstanceId(instanceId)
        return ApiResult.Success(Unit)
    }

    override suspend fun clearFailed(instanceId: String): ApiResult<Unit> {
        uploadTaskDao.deleteFailedByInstanceId(instanceId)
        return ApiResult.Success(Unit)
    }

    private fun queryMetadata(uri: Uri): Triple<String?, Long?, String?> {
        var displayName: String? = null
        var size: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = context.contentResolver.getType(uri)
        return Triple(displayName, size, mimeType)
    }

    private fun uniqueWorkName(taskId: String) = "upload_$taskId"

    private fun UploadTaskEntity.toDomain() = UploadTask(
        id = id,
        instanceId = instanceId,
        targetDir = targetDir,
        fileName = fileName,
        totalBytes = totalBytes,
        uploadedBytes = uploadedBytes,
        status = UploadStatus.valueOf(status),
        errorMessage = errorMessage,
    )
}
