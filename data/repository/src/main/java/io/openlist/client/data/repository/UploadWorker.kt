package io.openlist.client.data.repository

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.auth.TokenProvider
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.UploadTaskStatus
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.ProgressRequestBody
import io.openlist.client.core.network.UploadHttpClient
import io.openlist.client.core.network.dto.ApiResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Streams one file to `PUT api/fs/put` (v0.2_EXECUTION_PLAN.md §14.2). Runs
 * outside any ViewModel/Activity lifecycle by design (decision B) — all
 * state it needs comes from [UploadTaskDao] via [inputData]'s taskId, and
 * every result (success, failure, cancellation) is written back there so the
 * UI (observing the same DAO as a Flow) reflects it regardless of whether
 * this process is even the one that started the upload.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadTaskDao: UploadTaskDao,
    private val instanceRepository: InstanceRepository,
    private val uploadHttpClient: UploadHttpClient,
    private val tokenProvider: TokenProvider,
    private val fileCacheDao: FileCacheDao,
    private val sessionManager: SessionManager,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = uploadTaskDao.getById(taskId) ?: return Result.failure()
        if (task.status == UploadTaskStatus.CANCELLED) return Result.success()

        val instance = instanceRepository.getById(task.instanceId)
            ?: return fail(taskId, "实例不存在")

        val inputStream = runCatching {
            applicationContext.contentResolver.openInputStream(Uri.parse(task.localUri))
        }.getOrNull() ?: return fail(taskId, "文件无法读取，可能已被移动或删除")

        markRunning(taskId)

        val mimeType = task.mimeType?.takeUnless { it.isBlank() } ?: "application/octet-stream"
        var lastPersistedAt = 0L
        val requestBody = ProgressRequestBody(
            inputStream = inputStream,
            mediaType = mimeType.toMediaTypeOrNull(),
            contentLength = task.totalBytes ?: -1L,
        ) { uploaded ->
            val now = System.currentTimeMillis()
            // Throttled: persisting on every 64KB chunk would turn a large
            // upload into thousands of blocking Room writes on the OkHttp
            // writer thread.
            if (now - lastPersistedAt >= PROGRESS_PERSIST_INTERVAL_MS) {
                lastPersistedAt = now
                persistProgressBlocking(taskId, uploaded)
            }
        }

        val requestBuilder = Request.Builder()
            .url("${instance.baseUrl.trimEnd('/')}/api/fs/put")
            .put(requestBody)
            .header("File-Path", OpenListPathCodec.encodePathForHeader(OpenListPathCodec.child(task.targetDir, task.fileName)))
            .header("As-Task", "false")
            // Default overwrite server-side is true; v0.2 always opts out (P1)
            // so a same-name conflict surfaces as a normal, handleable failure.
            .header("Overwrite", "false")
            .header("Content-Type", mimeType)
        task.totalBytes?.let { requestBuilder.header("X-File-Size", it.toString()) }
        tokenProvider.blockingTokenFor(task.instanceId)?.takeUnless { it.isBlank() }
            ?.let { requestBuilder.header("Authorization", it) }

        val call = uploadHttpClient.client.newCall(requestBuilder.build())
        return try {
            executeCancellable(call).use { response ->
                if (response.isSuccessful) {
                    uploadTaskDao.updateProgress(taskId, UploadTaskStatus.SUCCESS, task.totalBytes ?: 0L, null, System.currentTimeMillis())
                    fileCacheDao.clearDirectory(task.instanceId, task.targetDir)
                    Result.success()
                } else {
                    // Matches every other write path (FileOperationRepositoryImpl):
                    // a 401 here means the token expired server-side and must
                    // drop the local session so the next screen visit re-prompts
                    // login, not just fail this one upload (v0.2_EXECUTION_PLAN.md §8.4).
                    if (response.code == 401) {
                        sessionManager.invalidate(task.instanceId)
                        fail(taskId, "登录已失效，请重新登录")
                    } else {
                        fail(taskId, parseErrorMessage(response) ?: "上传失败 (${response.code})")
                    }
                }
            }
        } catch (io: IOException) {
            // isStopped means WorkManager cancelled us (call.cancel() throws
            // an IOException from inside execute()); anything else is a
            // genuine network failure.
            if (isStopped) {
                uploadTaskDao.updateProgress(taskId, UploadTaskStatus.CANCELLED, task.uploadedBytes, null, System.currentTimeMillis())
                Result.success()
            } else {
                fail(taskId, "网络错误，请重试")
            }
        }
    }

    /** The body of a non-2xx response is still the standard `{code,message,data}`
     * envelope (server/common ErrorResp/ErrorStrResp), never plain text. */
    private fun parseErrorMessage(response: Response): String? = runCatching {
        val body = response.body?.string() ?: return null
        json.decodeFromString<ApiResponse<JsonElement?>>(body).message.takeUnless { it.isBlank() }
    }.getOrNull()

    private suspend fun executeCancellable(call: okhttp3.Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        try {
            cont.resume(call.execute())
        } catch (e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    private fun persistProgressBlocking(taskId: String, uploaded: Long) {
        kotlinx.coroutines.runBlocking {
            uploadTaskDao.updateProgress(taskId, UploadTaskStatus.RUNNING, uploaded, null, System.currentTimeMillis())
        }
    }

    private suspend fun markRunning(taskId: String) {
        uploadTaskDao.updateProgress(taskId, UploadTaskStatus.RUNNING, 0L, null, System.currentTimeMillis())
    }

    private suspend fun fail(taskId: String, message: String): Result {
        uploadTaskDao.updateProgress(taskId, UploadTaskStatus.FAILED, 0L, message, System.currentTimeMillis())
        return Result.failure()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        private const val PROGRESS_PERSIST_INTERVAL_MS = 750L
    }
}
