package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.BatchOperationResult

/**
 * Write operations on files/directories (v0.2_EXECUTION_PLAN.md §12.1).
 * `remove`/`move`/`copy` batch client-side — one backend call per name,
 * results aggregated into [BatchOperationResult] — because the backend
 * itself aborts a batch request on the first per-item failure and returns
 * no per-item result (decision C). Callers do not need to invalidate cache
 * themselves: successful operations invalidate the affected directories'
 * cached listings as part of the call (v0.2_EXECUTION_PLAN.md §18).
 */
interface FileOperationRepository {
    /** Creates `parentPath/name`. Refreshes [parentPath]'s cached listing on success. */
    suspend fun mkdir(instanceId: String, parentPath: String, name: String): ApiResult<Unit>

    /** Renames the entry at [path] to [newName] (its parent directory unchanged). */
    suspend fun rename(instanceId: String, path: String, newName: String): ApiResult<Unit>

    /** Deletes each of [names] (bare names, not full paths) from [dir]. */
    suspend fun remove(instanceId: String, dir: String, names: List<String>): ApiResult<BatchOperationResult>

    /** Moves each of [names] (bare names, not full paths) from [srcDir] into [targetDir]. */
    suspend fun move(
        instanceId: String,
        srcDir: String,
        targetDir: String,
        names: List<String>,
    ): ApiResult<BatchOperationResult>

    /** Copies each of [names] (bare names, not full paths) from [srcDir] into [targetDir]. */
    suspend fun copy(
        instanceId: String,
        srcDir: String,
        targetDir: String,
        names: List<String>,
    ): ApiResult<BatchOperationResult>
}
