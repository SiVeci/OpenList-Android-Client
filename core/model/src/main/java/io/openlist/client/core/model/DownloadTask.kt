package io.openlist.client.core.model

enum class DownloadStatus { ENQUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }

/** Domain projection of `DownloadTaskEntity` (v0.3_EXECUTION_PLAN.md §16.4, P9). */
data class DownloadTask(
    val id: String,
    val instanceId: String,
    val path: String,
    val fileName: String,
    val localUri: String?,
    val status: DownloadStatus,
    val progress: Int?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
