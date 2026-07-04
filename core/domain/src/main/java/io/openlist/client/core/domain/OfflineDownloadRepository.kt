package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.RemoteTask

/**
 * `/api/fs/add_offline_download` + `/api/public/offline_download_tools`.
 * Gated by the `CanAddOfflineDownloadTasks` permission bit, not by
 * `AuthNotGuest` (v0.3_EXECUTION_PLAN.md §6.1).
 */
interface OfflineDownloadRepository {
    /** No auth required server-side. */
    suspend fun listTools(instanceId: String): ApiResult<List<String>>

    /** Submitting does not invalidate the target directory's file cache
     * (§18.4) — the files aren't assumed to exist yet. */
    suspend fun addOfflineDownload(instanceId: String, urls: List<String>, targetDir: String, tool: String?): ApiResult<List<RemoteTask>>
}
