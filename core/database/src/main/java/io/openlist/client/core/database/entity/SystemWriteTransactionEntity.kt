package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable transaction journal. localRelativePath is always relative to the
 * no-backup system-documents root; absolute file paths are never persisted.
 */
@Entity(
    tableName = "system_write_transactions",
    indices = [
        Index(value = ["instanceId", "state", "updatedAt"]),
        Index(value = ["instanceId", "documentId", "state"]),
        Index(value = ["expiresAt", "state"]),
        Index(value = ["localRelativePath"], unique = true),
    ],
)
data class SystemWriteTransactionEntity(
    @PrimaryKey val transactionId: String,
    val instanceId: String,
    val documentId: String?,
    val targetPath: String,
    val displayName: String,
    val localRelativePath: String,
    val remoteTempPath: String?,
    val remoteBackupPath: String?,
    val remoteStageName: String?,
    val remoteBackupName: String?,
    val state: String,
    val dirtyGeneration: Long,
    val committedGeneration: Long,
    val reservedBytes: Long,
    val expectedSize: Long?,
    val expectedHash: String?,
    val baseFingerprint: String?,
    val failureStage: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val cleanupAfter: Long?,
    val expiresAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
