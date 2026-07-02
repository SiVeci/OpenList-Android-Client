package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.FileDetail

/**
 * Hands a download off to the system DownloadManager (v0.1_PRD §5.5, decision
 * D-06). Success here means "handed off", not "completed" — actual progress
 * and completion/failure are surfaced by the system download notification.
 * A local record is kept either way for future richer tracking (v0.2+).
 */
interface TransferRepository {
    suspend fun enqueueDownload(instanceId: String, file: FileDetail): ApiResult<Long>
}
