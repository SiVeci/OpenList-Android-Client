package io.openlist.client.core.model

enum class UploadStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }

/** Domain projection of `UploadTaskEntity` (v0.2_EXECUTION_PLAN.md §11.1) —
 * deliberately excludes [localUri]/`workRequestId`, which are implementation
 * details the UI never needs. */
data class UploadTask(
    val id: String,
    val instanceId: String,
    val targetDir: String,
    val fileName: String,
    val totalBytes: Long?,
    val uploadedBytes: Long,
    val status: UploadStatus,
    val errorMessage: String?,
)
