package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemWriteTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SystemWriteTransactionEntity)

    @Query("SELECT * FROM system_write_transactions WHERE transactionId = :transactionId")
    suspend fun getById(transactionId: String): SystemWriteTransactionEntity?

    @Query("SELECT * FROM system_write_transactions WHERE instanceId = :instanceId")
    suspend fun getAllByInstance(instanceId: String): List<SystemWriteTransactionEntity>

    @Query("SELECT * FROM system_write_transactions WHERE instanceId = :instanceId AND state IN ('FAILED_DRAFT', 'RECOVERY_REQUIRED', 'CLEANUP_PENDING') ORDER BY updatedAt DESC")
    fun observeRecoverableByInstance(instanceId: String): Flow<List<SystemWriteTransactionEntity>>

    @Query("SELECT * FROM system_write_transactions WHERE state IN ('LOCAL_WRITING', 'LOCAL_READY', 'REMOTE_STAGING', 'REMOTE_STAGED', 'ORIGINAL_BACKED_UP', 'TARGET_PROMOTED', 'TARGET_VERIFIED', 'CONTENT_COMMITTED', 'RECOVERY_REQUIRED', 'CLEANUP_PENDING') ORDER BY updatedAt ASC")
    suspend fun getRecoveryCandidates(): List<SystemWriteTransactionEntity>

    @Query("SELECT * FROM system_write_transactions WHERE instanceId = :instanceId AND state IN ('LOCAL_WRITING', 'LOCAL_READY', 'REMOTE_STAGING', 'REMOTE_STAGED', 'ORIGINAL_BACKED_UP', 'TARGET_PROMOTED', 'TARGET_VERIFIED', 'CONTENT_COMMITTED', 'RECOVERY_REQUIRED', 'CLEANUP_PENDING') ORDER BY updatedAt ASC")
    suspend fun getRecoveryCandidatesForInstance(instanceId: String): List<SystemWriteTransactionEntity>

    @Query("SELECT * FROM system_write_transactions WHERE state = 'FAILED_DRAFT' AND expiresAt IS NOT NULL AND expiresAt <= :now ORDER BY expiresAt ASC")
    suspend fun getExpiredDrafts(now: Long): List<SystemWriteTransactionEntity>

    @Query("SELECT * FROM system_write_transactions WHERE instanceId = :instanceId AND state = 'FAILED_DRAFT' AND expiresAt IS NOT NULL AND expiresAt <= :now ORDER BY expiresAt ASC")
    suspend fun getExpiredDraftsForInstance(instanceId: String, now: Long): List<SystemWriteTransactionEntity>

    @Query("SELECT COALESCE(SUM(reservedBytes), 0) FROM system_write_transactions WHERE state NOT IN ('CLEANED', 'EXPIRED')")
    suspend fun totalActiveReservations(): Long

    @Query("UPDATE system_write_transactions SET state = :newState, updatedAt = :updatedAt, errorCode = :errorCode, errorMessage = :errorMessage WHERE transactionId = :transactionId AND state = :expectedState")
    suspend fun compareAndSetState(transactionId: String, expectedState: String, newState: String, updatedAt: Long, errorCode: String? = null, errorMessage: String? = null): Int

    /**
     * Records the exact durable phase that became uncertain.  This is kept
     * separate from [compareAndSetState] so ordinary state transitions never
     * accidentally clear a failure classification needed by recovery UI.
     */
    @Query("UPDATE system_write_transactions SET state = 'RECOVERY_REQUIRED', failureStage = :failureStage, updatedAt = :updatedAt, errorCode = :errorCode, errorMessage = :errorMessage WHERE transactionId = :transactionId AND state = :expectedState")
    suspend fun markRecoveryRequired(
        transactionId: String,
        expectedState: String,
        failureStage: String,
        updatedAt: Long,
        errorCode: String?,
        errorMessage: String?,
    ): Int

    @Query("UPDATE system_write_transactions SET state = 'FAILED_DRAFT', expiresAt = :expiresAt, updatedAt = :updatedAt, errorCode = :errorCode, errorMessage = :errorMessage WHERE transactionId = :transactionId AND state = :expectedState")
    suspend fun markFailedDraft(transactionId: String, expectedState: String, expiresAt: Long, updatedAt: Long, errorCode: String?, errorMessage: String?): Int

    /** This transition is reserved for an explicit user retry, never recovery work. */
    @Query("UPDATE system_write_transactions SET state = 'LOCAL_READY', expiresAt = NULL, updatedAt = :updatedAt, errorCode = NULL, errorMessage = NULL WHERE transactionId = :transactionId AND state = 'FAILED_DRAFT'")
    suspend fun prepareManualRetry(transactionId: String, updatedAt: Long): Int

    @Query("UPDATE system_write_transactions SET dirtyGeneration = :generation, expectedSize = :expectedSize, reservedBytes = :reservedBytes, updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun updateLocalProgress(transactionId: String, generation: Long, expectedSize: Long, reservedBytes: Long, updatedAt: Long)

    @Query("UPDATE system_write_transactions SET state = :newState, remoteTempPath = :remoteTempPath, remoteBackupPath = :remoteBackupPath, remoteStageName = :remoteStageName, remoteBackupName = :remoteBackupName, expectedHash = :expectedHash, lastAttemptAt = :updatedAt, attemptCount = attemptCount + 1, updatedAt = :updatedAt WHERE transactionId = :transactionId AND state = :expectedState")
    suspend fun beginRemoteCommit(
        transactionId: String,
        expectedState: String,
        newState: String,
        remoteTempPath: String,
        remoteBackupPath: String,
        remoteStageName: String,
        remoteBackupName: String,
        expectedHash: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE system_write_transactions SET committedGeneration = :generation, updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun markCommittedGeneration(transactionId: String, generation: Long, updatedAt: Long)

    @Query("DELETE FROM system_write_transactions WHERE transactionId = :transactionId")
    suspend fun delete(transactionId: String)

    @Query("DELETE FROM system_write_transactions WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
