package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.model.SystemWriteTransactionState
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fact-checked compensation only. In particular, FAILED_DRAFT is never sent
 * to the server automatically: a user must explicitly retry it later.
 */
@Singleton
class SystemDocumentRecoveryCoordinator @Inject constructor(
    private val transactionDao: SystemWriteTransactionDao,
    private val spaceManager: SystemDocumentSpaceManager,
    private val gateway: SystemDocumentRemoteGateway,
) {
    private val recoveryMutex = Mutex()

    suspend fun recoverLocalDrafts(instanceId: String? = null, now: Long = System.currentTimeMillis()) {
        recoveryMutex.withLock {
            val candidates = if (instanceId == null) {
                transactionDao.getRecoveryCandidates()
            } else {
                transactionDao.getRecoveryCandidatesForInstance(instanceId)
            }
            candidates.forEach { transaction ->
                when (stateOf(transaction)) {
                    SystemWriteTransactionState.LOCAL_WRITING,
                    SystemWriteTransactionState.LOCAL_READY -> failDraft(transaction, now, "PROCESS_INTERRUPTED")
                    SystemWriteTransactionState.FAILED_DRAFT -> Unit
                    SystemWriteTransactionState.CLEANED,
                    SystemWriteTransactionState.EXPIRED,
                    null -> Unit
                    else -> recoverRemoteTransaction(transaction, now)
                }
            }
            val expired = if (instanceId == null) {
                transactionDao.getExpiredDrafts(now)
            } else {
                transactionDao.getExpiredDraftsForInstance(instanceId, now)
            }
            expired.forEach { transaction ->
                spaceManager.deleteDraftFile(transaction.transactionId)
                transactionDao.compareAndSetState(transaction.transactionId, SystemWriteTransactionState.FAILED_DRAFT.name, SystemWriteTransactionState.EXPIRED.name, now)
                spaceManager.releaseDraftReservation(transaction.transactionId)
            }
        }
    }

    private suspend fun recoverRemoteTransaction(tx: SystemWriteTransactionEntity, now: Long) {
        val names = gateway.namesFor(tx.targetPath, tx.transactionId) ?: return keepRecoverable(tx, now, "INVALID_JOURNAL")
        // Reject any journal whose persisted role paths don't match its own deterministic namespace.
        if (tx.remoteTempPath != null && tx.remoteTempPath != names.stagePath || tx.remoteBackupPath != null && tx.remoteBackupPath != names.backupPath) {
            return keepRecoverable(tx, now, "UNPROVEN_REMOTE_OWNERSHIP")
        }
        val expectedSize = tx.expectedSize ?: return keepRecoverable(tx, now, "MISSING_EXPECTED_SIZE")
        val expectedHash = tx.expectedHash ?: return keepRecoverable(tx, now, "MISSING_EXPECTED_HASH")
        val target = gateway.findObject(tx.instanceId, tx.targetPath)
        val stage = gateway.findObject(tx.instanceId, names.stagePath)
        val backup = gateway.findObject(tx.instanceId, names.backupPath)
        if (target is ApiResult.Failure || stage is ApiResult.Failure || backup is ApiResult.Failure) return keepRecoverable(tx, now, "REMOTE_FACT_UNAVAILABLE")
        val targetObject = (target as ApiResult.Success).data
        val stageObject = (stage as ApiResult.Success).data
        val backupObject = (backup as ApiResult.Success).data
        val targetMatches = targetObject != null && gateway.verifyObject(tx.instanceId, tx.targetPath, expectedSize, expectedHash) is ApiResult.Success
        val stageMatches = stageObject != null && gateway.verifyObject(tx.instanceId, names.stagePath, expectedSize, expectedHash) is ApiResult.Success

        when {
            targetMatches -> {
                if (!cleanupOwned(tx, names, stageObject != null, backupObject != null)) return keepRecoverable(tx, now, "CLEANUP_UNCONFIRMED")
                finishSuccessful(tx, now)
            }
            targetObject == null && stageMatches -> {
                // The original was already moved aside (or this is a new file); promotion is deterministic.
                when (gateway.renameAndVerify(tx.instanceId, names.stagePath, OpenListPathCodec.name(tx.targetPath))) {
                    is ApiResult.Failure -> keepRecoverable(tx, now, "PROMOTION_UNCONFIRMED")
                    is ApiResult.Success -> {
                        if (gateway.verifyObject(tx.instanceId, tx.targetPath, expectedSize, expectedHash) !is ApiResult.Success) {
                            keepRecoverable(tx, now, "TARGET_UNCONFIRMED")
                        } else if (!cleanupOwned(tx, names, false, backupObject != null)) {
                            keepRecoverable(tx, now, "CLEANUP_UNCONFIRMED")
                        } else finishSuccessful(tx, now)
                    }
                }
            }
            // Before a backup was made the original still exists. Discard only the proven transaction stage and retain the draft.
            targetObject != null && stageMatches && backupObject == null -> {
                if (gateway.removeAndVerifyAbsent(tx.instanceId, names.stagePath) is ApiResult.Success) failDraft(tx, now, "REMOTE_NOT_PROMOTED")
                else keepRecoverable(tx, now, "STAGE_CLEANUP_UNCONFIRMED")
            }
            // The stage-upload result was interrupted, but a fresh listing
            // proves that neither a transaction object nor the original target
            // moved. The user draft is therefore safe to retain for a manual
            // retry without leaving a needless recovery item behind.
            targetObject != null && stageObject == null && backupObject == null ->
                failDraft(tx, now, "STAGE_ABSENT_ORIGINAL_INTACT")
            // An original backup exists but no verified stage/target does: restore it, preserving the local draft for manual retry.
            targetObject == null && backupObject != null && !stageMatches -> {
                when (gateway.renameAndVerify(tx.instanceId, names.backupPath, OpenListPathCodec.name(tx.targetPath))) {
                    is ApiResult.Success -> failDraft(tx, now, "ORIGINAL_RESTORED")
                    is ApiResult.Failure -> keepRecoverable(tx, now, "RESTORE_UNCONFIRMED")
                }
            }
            else -> keepRecoverable(tx, now, "REMOTE_STATE_AMBIGUOUS")
        }
    }

    private suspend fun cleanupOwned(tx: SystemWriteTransactionEntity, names: SystemDocumentRemoteNames, stageExists: Boolean, backupExists: Boolean): Boolean {
        if (stageExists && gateway.removeAndVerifyAbsent(tx.instanceId, names.stagePath) !is ApiResult.Success) return false
        if (backupExists && gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) !is ApiResult.Success) return false
        return true
    }

    private suspend fun finishSuccessful(tx: SystemWriteTransactionEntity, now: Long) {
        transactionDao.compareAndSetState(tx.transactionId, tx.state, SystemWriteTransactionState.CLEANED.name, now)
        spaceManager.deleteDraftFile(tx.transactionId)
        spaceManager.releaseDraftReservation(tx.transactionId)
        transactionDao.delete(tx.transactionId)
    }

    private suspend fun failDraft(tx: SystemWriteTransactionEntity, now: Long, reason: String) {
        transactionDao.markFailedDraft(tx.transactionId, tx.state, now + DRAFT_TTL_MILLIS, now, reason, null)
    }

    private suspend fun keepRecoverable(tx: SystemWriteTransactionEntity, now: Long, reason: String) {
        if (tx.state != SystemWriteTransactionState.RECOVERY_REQUIRED.name) {
            transactionDao.compareAndSetState(tx.transactionId, tx.state, SystemWriteTransactionState.RECOVERY_REQUIRED.name, now, reason, null)
        }
    }

    private fun stateOf(tx: SystemWriteTransactionEntity) = runCatching { SystemWriteTransactionState.valueOf(tx.state) }.getOrNull()
}

private const val DRAFT_TTL_MILLIS = 24L * 60L * 60L * 1000L
