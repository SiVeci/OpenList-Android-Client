package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.UnifiedTask
import kotlinx.coroutines.flow.Flow

/**
 * Combines local upload, local download, and remote tasks into one stream
 * for the task center (v0.3_EXECUTION_PLAN.md §12), sorted running > pending
 * > failed > done, most recent first.
 */
interface TaskAggregationRepository {
    fun observeAllTasks(instanceId: String): Flow<List<UnifiedTask>>

    suspend fun refreshRemoteTasks(instanceId: String): ApiResult<Unit>

    /** DownloadManager status pull for [io.openlist.client.core.model.TaskSource.LOCAL_DOWNLOAD]
     * rows (P9) — wired to a real DownloadManager query in Sprint 5 (S5-T5). */
    suspend fun refreshDownloadStatuses(instanceId: String)

    /** Dispatches by [source]: LOCAL_UPLOAD → UploadRepository.cancelUpload,
     * REMOTE → TaskRepository.cancelRemoteTask. LOCAL_DOWNLOAD cancellation
     * is not supported in v0.3 (§16.3). */
    suspend fun cancelTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit>
}
