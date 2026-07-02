package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local record of a download handed off to the system DownloadManager
 * (v0.1_PRD §8.4.4). [downloadManagerId] is DownloadManager's own enqueue id —
 * not in the PRD's original sketch, kept so a future download-history screen
 * can query DownloadManager for live status without re-deriving it.
 * Status transitions beyond "ENQUEUED" (SUCCESSFUL/FAILED) are wired once that
 * screen lands (v0.1 delegates completion/failure feedback to the system
 * notification — v0.1_PRD §5.5.1).
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
