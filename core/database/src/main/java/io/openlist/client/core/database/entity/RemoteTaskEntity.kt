package io.openlist.client.core.database.entity

import androidx.room.Entity

/**
 * Local cache of one backend `/api/task/{type}/...` task (v0.3_EXECUTION_PLAN.md
 * §11). [taskType] is one of the 7 backend type strings (only 4 are polled in
 * v0.3 — offline_download / offline_download_transfer / copy / move, see P7).
 * [stateRaw] is the backend's numeric `tache.State`; [status] is that value
 * already mapped through `TaskStateMapper` into `UnifiedTaskStatus.name`, kept
 * alongside the raw number so a mapper fix doesn't require a migration.
 * [rawJson] is the decoded TaskInfo response, which carries no token/secret.
 */
@Entity(tableName = "remote_tasks", primaryKeys = ["id", "instanceId"])
data class RemoteTaskEntity(
    val id: String,
    val instanceId: String,
    val taskType: String,
    val title: String,
    val stateRaw: Int,
    val status: String,
    val progress: Int?,
    val targetPath: String?,
    val errorMessage: String?,
    val totalBytes: Long?,
    val rawJson: String,
    val startTime: Long?,
    val endTime: Long?,
    val cachedAt: Long,
)
