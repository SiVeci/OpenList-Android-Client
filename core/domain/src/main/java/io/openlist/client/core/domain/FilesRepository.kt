package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.model.DirectoryCapability
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.model.FileNode
import kotlinx.coroutines.flow.Flow

/** Emitted while listing a directory (v0.1_PRD §5.3.4 / §8.5): cache shown
 * instantly if present, then overwritten by the network result once it lands. */
sealed class FileListResult {
    /** [capability] is always [DirectoryCapability.UNKNOWN] here (v1.0
     * V-604) — the cache table stores no capability column, so a cache-only
     * emission can't know it until the network response ([Fresh]) lands. */
    data class Cached(
        val nodes: List<FileNode>,
        val cachedAt: Long,
        val capability: DirectoryCapability = DirectoryCapability.UNKNOWN,
    ) : FileListResult()

    data class Fresh(val nodes: List<FileNode>, val capability: DirectoryCapability) : FileListResult()

    /** [staleCache] carries whatever was cached (or the currently-displayed
     * nodes) so the UI can keep showing them with a "cached data" notice. */
    data class Error(val error: DomainError, val staleCache: List<FileNode>?) : FileListResult()
}

interface FilesRepository {
    /**
     * [forceRefresh] only invalidates *this app's* local cache (pull-to-refresh);
     * it never sets OpenList's own `refresh` request field, since that triggers a
     * storage-backend rescan gated on write permission and would 403 for
     * guest/read-only users on an ordinary refresh gesture.
     */
    fun listDirectory(instanceId: String, path: String, forceRefresh: Boolean = false): Flow<FileListResult>

    /** POST /api/fs/get — file detail plus a ready-to-use download URL. */
    suspend fun getFile(instanceId: String, path: String): ApiResult<FileDetail>

    /** Settings' "清理缓存" — drops every instance's cached directory listings. */
    suspend fun clearAllCache()
}
