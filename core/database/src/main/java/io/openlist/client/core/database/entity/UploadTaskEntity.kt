package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mirrors [UploadTaskEntity.status]. Kept as a plain String column (not a
 * Room enum converter) so future statuses don't need a migration. */
object UploadTaskStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

/**
 * One upload (v0.2_EXECUTION_PLAN.md §11.1, Migration 5→6). [localUri] is a
 * persisted SAF `content://` URI string — never a real filesystem path — and
 * [totalBytes]/[uploadedBytes] are pre-wired for the byte-range resume v0.2
 * doesn't implement yet. No token or other secret is ever stored here.
 */
@Entity(tableName = "upload_tasks")
data class UploadTaskEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val targetDir: String,
    val localUri: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val uploadedBytes: Long,
    val status: String,
    val errorMessage: String?,
    val workRequestId: String?,
    val overwrite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
