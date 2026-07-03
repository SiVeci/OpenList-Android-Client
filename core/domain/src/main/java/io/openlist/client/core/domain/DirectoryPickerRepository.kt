package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.FileNode

/**
 * Directory-only listing for move/copy/upload target selection
 * (v0.2_EXECUTION_PLAN.md §12.3/§16). Reuses fs/list like [FilesRepository]
 * but never touches the browsing cache (FileCacheDao) — picking a target
 * directory is a transient, unrelated read.
 */
interface DirectoryPickerRepository {
    /** Returns only [path]'s child directories (files filtered out). */
    suspend fun listDirectories(instanceId: String, path: String): ApiResult<List<FileNode>>
}
