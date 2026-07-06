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
     * LOCAL_DOWNLOAD → TransferRepository.cancelDownload (v1.0_PRD §4.2.C.2),
     * REMOTE → TaskRepository.cancelRemoteTask. */
    suspend fun cancelTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit>

    /**
     * Dispatches a retry by [source] (v1.0_PRD §4.2.C.1). Only LOCAL_UPLOAD is
     * supported in v1.0 — REMOTE and LOCAL_DOWNLOAD return an explicit
     * unsupported error rather than silently doing nothing, matching how
     * [cancelTask]'s LOCAL_DOWNLOAD branch was handled before v1.0.
     */
    suspend fun retryTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit>
}
