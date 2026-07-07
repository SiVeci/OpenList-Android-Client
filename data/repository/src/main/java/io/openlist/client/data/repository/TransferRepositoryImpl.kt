package io.openlist.client.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.DownloadTaskStatus
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.DownloadStatus
import io.openlist.client.core.model.DownloadTask
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.network.toDomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadTaskDao: DownloadTaskDao,
) : TransferRepository {

    override suspend fun enqueueDownload(instanceId: String, file: FileDetail): ApiResult<Long> {
        if (file.rawUrl.isBlank()) {
            return ApiResult.Failure(DomainError.OpenListError(code = null, message = "未获取到下载链接"))
        }
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(file.rawUrl))
                .setTitle(file.name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, file.name)
                .setAllowedOverMetered(true)
            val downloadManagerId = downloadManager.enqueue(request)

            val now = System.currentTimeMillis()
            downloadTaskDao.upsert(
                DownloadTaskEntity(
                    id = UUID.randomUUID().toString(),
                    instanceId = instanceId,
                    path = file.path,
                    fileName = file.name,
                    url = file.rawUrl,
                    localUri = null,
                    downloadManagerId = downloadManagerId,
                    status = DownloadTaskStatus.ENQUEUED,
                    progress = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            ApiResult.Success(downloadManagerId)
        } catch (t: Throwable) {
            ApiResult.Failure(t.toDomainError())
        }
    }

    override fun observeDownloadTasks(instanceId: String): Flow<List<DownloadTask>> =
        downloadTaskDao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    /** P9: DownloadManager.query() over every active row's own enqueue id —
     * the other half (ACTION_DOWNLOAD_COMPLETE broadcast) is a UI-lifecycle
     * concern registered by the task center screen itself, not here. */
    override suspend fun refreshDownloadStatus(instanceId: String) {
        val active = downloadTaskDao.getActiveByInstance(instanceId)
        if (active.isEmpty()) return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val now = System.currentTimeMillis()
        for (task in active) {
            val managerId = task.downloadManagerId ?: continue
            val query = DownloadManager.Query().setFilterById(managerId)
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val (status, progress, errorMessage, localUri) = cursor.toStatus()
                downloadTaskDao.updateStatus(task.id, status, progress, errorMessage, localUri, now)
            }
        }
    }

    override suspend fun cancelDownload(taskId: String): ApiResult<Unit> {
        val task = downloadTaskDao.getById(taskId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (task.status != DownloadTaskStatus.ENQUEUED && task.status != DownloadTaskStatus.RUNNING) {
            return ApiResult.Failure(DomainError.DownloadCancelUnavailable)
        }
        task.downloadManagerId?.let { managerId ->
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(managerId)
        }
        downloadTaskDao.updateStatus(taskId, DownloadTaskStatus.CANCELLED, task.progress, null, null, System.currentTimeMillis())
        return ApiResult.Success(Unit)
    }

    override suspend fun clearFinished(instanceId: String): ApiResult<Unit> {
        downloadTaskDao.deleteFinishedByInstanceId(instanceId)
        return ApiResult.Success(Unit)
    }

    private data class CursorStatus(val status: String, val progress: Int?, val errorMessage: String?, val localUri: String?)

    private fun Cursor.toStatus(): CursorStatus {
        val statusInt = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val bytesTotal = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val bytesDownloaded = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100) else null
        return when (statusInt) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val uriIndex = getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = if (uriIndex >= 0) getString(uriIndex) else null
                CursorStatus(DownloadTaskStatus.SUCCESS, 100, null, localUri)
            }
            DownloadManager.STATUS_FAILED -> {
                val reasonIndex = getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                CursorStatus(DownloadTaskStatus.FAILED, progress, "下载失败（错误码 ${getInt(reasonIndex)}）", null)
            }
            DownloadManager.STATUS_RUNNING -> CursorStatus(DownloadTaskStatus.RUNNING, progress, null, null)
            DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING ->
                CursorStatus(DownloadTaskStatus.ENQUEUED, progress, null, null)
            else -> CursorStatus(DownloadTaskStatus.ENQUEUED, progress, null, null)
        }
    }

    private fun DownloadTaskEntity.toDomain() = DownloadTask(
        id = id,
        instanceId = instanceId,
        path = path,
        fileName = fileName,
        localUri = localUri,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.ENQUEUED),
        progress = progress,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
