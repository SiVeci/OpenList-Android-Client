package io.openlist.client.core.model

/** Persisted state machine for a v1.4 system-document write transaction. */
enum class SystemWriteTransactionState {
    LOCAL_WRITING,
    LOCAL_READY,
    REMOTE_STAGING,
    REMOTE_STAGED,
    ORIGINAL_BACKED_UP,
    TARGET_PROMOTED,
    TARGET_VERIFIED,
    CONTENT_COMMITTED,
    CLEANED,
    FAILED_DRAFT,
    RECOVERY_REQUIRED,
    CLEANUP_PENDING,
    EXPIRED,
}

enum class SystemWriteFailureStage {
    LOCAL_WRITE,
    STAGE_UPLOAD,
    STAGE_VERIFICATION,
    ORIGINAL_BACKUP,
    TARGET_PROMOTION,
    TARGET_VERIFICATION,
    BACKUP_CLEANUP,
}

enum class SystemDocumentRecoveryAction { RETRY_SAVE, EXPORT_COPY, DELETE_DRAFT }

data class SystemWriteTransaction(
    val transactionId: String,
    val instanceId: String,
    val documentId: String?,
    val targetPath: String,
    val displayName: String,
    val state: SystemWriteTransactionState,
    val dirtyGeneration: Long,
    val committedGeneration: Long,
    val expiresAt: Long?,
    val errorMessage: String?,
    val failureStage: SystemWriteFailureStage?,
)

fun SystemWriteTransactionState.canTransitionTo(next: SystemWriteTransactionState): Boolean = when (this) {
    SystemWriteTransactionState.LOCAL_WRITING -> next in setOf(
        SystemWriteTransactionState.LOCAL_READY,
        SystemWriteTransactionState.FAILED_DRAFT,
        SystemWriteTransactionState.EXPIRED,
    )
    SystemWriteTransactionState.LOCAL_READY -> next in setOf(
        SystemWriteTransactionState.LOCAL_WRITING,
        SystemWriteTransactionState.REMOTE_STAGING,
        SystemWriteTransactionState.FAILED_DRAFT,
        SystemWriteTransactionState.EXPIRED,
    )
    SystemWriteTransactionState.REMOTE_STAGING -> next in setOf(
        SystemWriteTransactionState.REMOTE_STAGED,
        SystemWriteTransactionState.RECOVERY_REQUIRED,
        SystemWriteTransactionState.FAILED_DRAFT,
    )
    SystemWriteTransactionState.REMOTE_STAGED -> next in setOf(
        SystemWriteTransactionState.ORIGINAL_BACKED_UP,
        SystemWriteTransactionState.TARGET_PROMOTED,
        SystemWriteTransactionState.RECOVERY_REQUIRED,
        SystemWriteTransactionState.FAILED_DRAFT,
    )
    SystemWriteTransactionState.ORIGINAL_BACKED_UP -> next in setOf(
        SystemWriteTransactionState.TARGET_PROMOTED,
        SystemWriteTransactionState.RECOVERY_REQUIRED,
    )
    SystemWriteTransactionState.TARGET_PROMOTED -> next in setOf(
        SystemWriteTransactionState.TARGET_VERIFIED,
        SystemWriteTransactionState.RECOVERY_REQUIRED,
    )
    SystemWriteTransactionState.TARGET_VERIFIED -> next in setOf(
        SystemWriteTransactionState.CONTENT_COMMITTED,
        SystemWriteTransactionState.CLEANUP_PENDING,
    )
    // A handle can be written again after a successful fsync. The next write
    // starts a later generation and is committed on the next fsync/release.
    SystemWriteTransactionState.CONTENT_COMMITTED -> next in setOf(
        SystemWriteTransactionState.LOCAL_WRITING,
        SystemWriteTransactionState.LOCAL_READY,
        SystemWriteTransactionState.CLEANED,
    )
    SystemWriteTransactionState.CLEANUP_PENDING -> next in setOf(
        SystemWriteTransactionState.CONTENT_COMMITTED,
        SystemWriteTransactionState.CLEANED,
    )
    SystemWriteTransactionState.RECOVERY_REQUIRED -> next in setOf(
        SystemWriteTransactionState.REMOTE_STAGED,
        SystemWriteTransactionState.ORIGINAL_BACKED_UP,
        SystemWriteTransactionState.TARGET_PROMOTED,
        SystemWriteTransactionState.TARGET_VERIFIED,
        SystemWriteTransactionState.CLEANUP_PENDING,
        SystemWriteTransactionState.FAILED_DRAFT,
    )
    // FAILED_DRAFT is terminal for automatic recovery. A transition back to
    // LOCAL_READY is permitted only by the explicit user action path.
    SystemWriteTransactionState.FAILED_DRAFT -> next in setOf(
        SystemWriteTransactionState.LOCAL_READY,
        SystemWriteTransactionState.EXPIRED,
    )
    SystemWriteTransactionState.CLEANED, SystemWriteTransactionState.EXPIRED -> false
}
