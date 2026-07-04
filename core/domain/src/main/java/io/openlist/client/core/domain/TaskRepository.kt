package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.RemoteTask
import kotlinx.coroutines.flow.Flow

/**
 * Backend `/api/task/{type}` tasks (`AuthNotGuest`). v0.3 only polls the 4
 * types relevant to client actions — offline_download /
 * offline_download_transfer / copy / move (P7); upload/decompress/
 * decompress_upload are reserved for a v0.5 admin surface.
 */
interface TaskRepository {
    /** Cache-only. */
    fun observeRemoteTasks(instanceId: String): Flow<List<RemoteTask>>

    /** Concurrently fetches undone+done for every polled type and replaces the cache. */
    suspend fun refreshRemoteTasks(instanceId: String): ApiResult<List<RemoteTask>>

    suspend fun cancelRemoteTask(instanceId: String, taskType: String, taskId: String): ApiResult<Unit>
}
