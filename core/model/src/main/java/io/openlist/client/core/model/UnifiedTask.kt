package io.openlist.client.core.model

/** Where a `UnifiedTask` originates (v0.3_EXECUTION_PLAN.md §11, task center Tab
 * = "全部/上传/下载/远程" per source). */
enum class TaskSource { LOCAL_UPLOAD, LOCAL_DOWNLOAD, REMOTE }

/** Kept 1:1 with the 7 backend task-type path segments plus the 2 local
 * transfer kinds; v0.3 only polls 4 remote types (P7). */
enum class TaskType { UPLOAD, DOWNLOAD, OFFLINE_DOWNLOAD, COPY, MOVE, INDEX, EXTRACT, UNKNOWN }

/** Normalizes local (`UploadTaskStatus`/`DownloadTaskEntity.status`) and
 * remote (`tache.State` via `TaskStateMapper`) statuses into one enum so the
 * task center never branches on task source. */
enum class UnifiedTaskStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, UNKNOWN }

/**
 * One row in the task center, unified across local upload/download and
 * remote backend tasks (v0.3_EXECUTION_PLAN.md §11.1, PRD §12.4). [path] is
 * the task's target/working directory (upload targetDir, download source
 * path, or remote task's targetPath); [localUri] is only set for downloads
 * that completed to a local `content://`/file URI.
 */
data class UnifiedTask(
    val id: String,
    val instanceId: String,
    val source: TaskSource,
    val type: TaskType,
    val title: String,
    val status: UnifiedTaskStatus,
    val progress: Int?,
    val path: String?,
    val localUri: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
