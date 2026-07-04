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
}
