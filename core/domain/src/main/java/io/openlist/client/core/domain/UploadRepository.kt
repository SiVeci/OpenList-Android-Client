package io.openlist.client.core.domain

import android.net.Uri
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.UploadTask
import kotlinx.coroutines.flow.Flow

/**
 * Upload orchestration via WorkManager (v0.2_EXECUTION_PLAN.md §12.2, decision
 * B). [localUris] must already be `ACTION_OPEN_DOCUMENT` results with a
 * persisted permission grant (P6) — a Worker runs later, possibly after the
 * picking Activity is long gone.
 */
interface UploadRepository {
    /** Creates one task per URI and enqueues its Worker; returns the created task ids. */
    suspend fun enqueueUpload(instanceId: String, targetDir: String, localUris: List<Uri>): ApiResult<List<String>>

    fun observeUploadTasks(instanceId: String): Flow<List<UploadTask>>

    suspend fun cancelUpload(taskId: String): ApiResult<Unit>

    /**
     * Re-enqueues a FAILED upload from the beginning (v1.0_PRD §4.2.C.1). No
     * byte-range resume — [UploadWorker] always restarts `uploadedBytes` at 0
     * regardless, so retry mirrors that. Fails with
     * [io.openlist.client.core.common.DomainError.UploadRetryUnavailable] if
     * the task isn't FAILED, or its SAF grant can no longer be opened
     * (v1.0_EXECUTION_PLAN.md V-605).
     */
    suspend fun retryUpload(taskId: String): ApiResult<Unit>
}
