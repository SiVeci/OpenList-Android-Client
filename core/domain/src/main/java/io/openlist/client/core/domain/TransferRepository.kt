package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.DownloadTask
import io.openlist.client.core.model.FileDetail
import kotlinx.coroutines.flow.Flow

/**
 * Hands a download off to the system DownloadManager (v0.1_PRD §5.5, decision
 * D-06). Success here means "handed off", not "completed" — actual progress
 * and completion/failure are surfaced by the system download notification,
 * and locally by [refreshDownloadStatus] (P9, v0.3_EXECUTION_PLAN.md §16.4).
 */
interface TransferRepository {
    suspend fun enqueueDownload(instanceId: String, file: FileDetail): ApiResult<Long>

    fun observeDownloadTasks(instanceId: String): Flow<List<DownloadTask>>

    /** Queries DownloadManager for every not-yet-terminal row and updates the
     * local record — the task center's poll/pull-to-refresh entry point,
     * not a background service (no always-on polling, §20). */
    suspend fun refreshDownloadStatus(instanceId: String)

    /**
     * Cancels a local download from the task center (v1.0_PRD §4.2.C.2).
     * `DownloadManager.remove` is idempotent (v1.0_EXECUTION_PLAN.md V-606:
     * a no-longer-existent id is simply a no-op, never throws), so this always
     * proceeds to mark the local row CANCELLED once the task is in a
     * cancellable state. Fails with
     * [io.openlist.client.core.common.DomainError.DownloadCancelUnavailable]
     * if the task is already terminal (SUCCESS/FAILED/CANCELLED).
     */
    suspend fun cancelDownload(taskId: String): ApiResult<Unit>

    suspend fun clearFinished(instanceId: String): ApiResult<Unit>

    suspend fun clearFailed(instanceId: String): ApiResult<Unit>
}
