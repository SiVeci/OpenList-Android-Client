package io.openlist.client.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.network.toDomainError
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
                    status = "ENQUEUED",
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
}
