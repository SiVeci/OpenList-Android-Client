package io.openlist.client.core.model

/** Domain projection of `RemoteTaskEntity` — a backend `/api/task/{type}` task
 * already mapped through `TaskStateMapper` (v0.3_EXECUTION_PLAN.md §11). */
data class RemoteTask(
    val id: String,
    val instanceId: String,
    val taskType: String,
    val title: String,
    val status: UnifiedTaskStatus,
    val progress: Int?,
    val targetPath: String?,
    val errorMessage: String?,
    val totalBytes: Long?,
    val startTime: Long?,
    val endTime: Long?,
)
