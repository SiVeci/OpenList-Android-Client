package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept as a plain String column, same precedent as [UploadTaskStatus]. Status
 * transitions beyond ENQUEUED are now wired via P9's DownloadStatusTracker
 * (v0.3_EXECUTION_PLAN.md §16.4) — TransferRepository.refreshDownloadStatus
 * maps DownloadManager's own status ints onto these. */
object DownloadTaskStatus {
    const val ENQUEUED = "ENQUEUED"
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

/**
 * Local record of a download handed off to the system DownloadManager
 * (v0.1_PRD §8.4.4). [downloadManagerId] is DownloadManager's own enqueue id,
 * used by P9's status refresh to query DownloadManager for live status.
 */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val path: String,
    val fileName: String,
    val url: String?,
    val localUri: String?,
    val downloadManagerId: Long?,
    val status: String,
    val progress: Int?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
