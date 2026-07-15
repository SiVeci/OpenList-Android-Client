package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.domain.SystemDocumentWriteHandle
import io.openlist.client.core.domain.SystemDocumentFailureNotifier
import io.openlist.client.core.model.SystemDocumentOpenMode
import io.openlist.client.core.model.SystemWriteFailureStage
import io.openlist.client.core.model.SystemWriteTransactionState
import io.openlist.client.core.model.canTransitionTo
import io.openlist.client.core.network.OpenListPathCodec
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/** P4-only fake submit seam. It intentionally performs no remote operation. */
interface SystemDocumentLocalCommitter {
    suspend fun submitLocalReady(transactionId: String, generation: Long): ApiResult<Unit>
    suspend fun finalizeCommitted(transactionId: String, generation: Long): ApiResult<Unit> = ApiResult.Success(Unit)
}

@Singleton
class NoopSystemDocumentLocalCommitter @Inject constructor() : SystemDocumentLocalCommitter {
    override suspend fun submitLocalReady(transactionId: String, generation: Long): ApiResult<Unit> = ApiResult.Success(Unit)
}

/** P5 strong replacement: a local fsync is successful only after remote facts verify. */
@Singleton
class StrongSystemDocumentLocalCommitter @Inject constructor(
    private val transactionDao: SystemWriteTransactionDao,
    private val spaceManager: SystemDocumentSpaceManager,
    private val gateway: SystemDocumentRemoteGateway,
) : SystemDocumentLocalCommitter {
    override suspend fun submitLocalReady(transactionId: String, generation: Long): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (transaction.dirtyGeneration != generation || transaction.state != SystemWriteTransactionState.LOCAL_READY.name) {
            return ApiResult.Failure(DomainError.OpenListError(null, "本地写入代次已变化"))
        }
        val draft = spaceManager.draftFile(transactionId)
        if (!draft.isFile) return ApiResult.Failure(DomainError.NotFound)
        val names = gateway.namesFor(transaction.targetPath, transactionId) ?: return ApiResult.Failure(DomainError.PathEncodeError)
        val hash = gateway.localSha256(draft)
        if (transactionDao.beginRemoteCommit(
                transactionId, SystemWriteTransactionState.LOCAL_READY.name, SystemWriteTransactionState.REMOTE_STAGING.name,
                names.stagePath, names.backupPath, names.stageName, names.backupName, hash, System.currentTimeMillis(),
            ) != 1
        ) return ApiResult.Failure(DomainError.OpenListError(null, "提交状态已变化"))
        return try {
            when (val staged = gateway.uploadAndVerifyStage(transaction.instanceId, names.stagePath, draft)) {
                is ApiResult.Failure -> return recover(transactionId, SystemWriteTransactionState.REMOTE_STAGING, SystemWriteFailureStage.STAGE_UPLOAD, staged.error)
                is ApiResult.Success -> Unit
            }
            transition(transactionId, SystemWriteTransactionState.REMOTE_STAGING, SystemWriteTransactionState.REMOTE_STAGED)
            val existing = when (val found = gateway.findObject(transaction.instanceId, transaction.targetPath)) {
                is ApiResult.Failure -> return recover(transactionId, SystemWriteTransactionState.REMOTE_STAGED, SystemWriteFailureStage.ORIGINAL_BACKUP, found.error)
                is ApiResult.Success -> found.data
            }
            var state = SystemWriteTransactionState.REMOTE_STAGED
            if (existing != null) {
                when (val backedUp = gateway.renameAndVerify(transaction.instanceId, transaction.targetPath, names.backupName)) {
                    is ApiResult.Failure -> return recover(transactionId, state, SystemWriteFailureStage.ORIGINAL_BACKUP, backedUp.error)
                    is ApiResult.Success -> Unit
                }
                // The journal only claims a backup after source absence and
                // backup presence were read back by renameAndVerify.
                transition(transactionId, state, SystemWriteTransactionState.ORIGINAL_BACKED_UP)
                state = SystemWriteTransactionState.ORIGINAL_BACKED_UP
            }
            when (val promoted = gateway.renameAndVerify(transaction.instanceId, names.stagePath, OpenListPathCodec.name(transaction.targetPath))) {
                is ApiResult.Failure -> return recover(transactionId, state, SystemWriteFailureStage.TARGET_PROMOTION, promoted.error)
                is ApiResult.Success -> Unit
            }
            // Likewise, TARGET_PROMOTED is a fact, not a request acceptance.
            transition(transactionId, state, SystemWriteTransactionState.TARGET_PROMOTED)
            state = SystemWriteTransactionState.TARGET_PROMOTED
            when (val verified = gateway.verifyObject(transaction.instanceId, transaction.targetPath, draft.length(), hash)) {
                is ApiResult.Failure -> return recover(transactionId, state, SystemWriteFailureStage.TARGET_VERIFICATION, verified.error)
                is ApiResult.Success -> Unit
            }
            transition(transactionId, state, SystemWriteTransactionState.TARGET_VERIFIED)
            if (existing != null) {
                transition(transactionId, SystemWriteTransactionState.TARGET_VERIFIED, SystemWriteTransactionState.CLEANUP_PENDING)
                when (val removed = gateway.removeAndVerifyAbsent(transaction.instanceId, names.backupPath)) {
                    is ApiResult.Failure -> return recover(transactionId, SystemWriteTransactionState.CLEANUP_PENDING, SystemWriteFailureStage.BACKUP_CLEANUP, removed.error)
                    is ApiResult.Success -> Unit
                }
                transition(transactionId, SystemWriteTransactionState.CLEANUP_PENDING, SystemWriteTransactionState.CONTENT_COMMITTED)
            } else {
                transition(transactionId, SystemWriteTransactionState.TARGET_VERIFIED, SystemWriteTransactionState.CONTENT_COMMITTED)
            }
            transactionDao.markCommittedGeneration(transactionId, generation, System.currentTimeMillis())
            ApiResult.Success(Unit)
        } catch (error: Throwable) {
            recover(transactionId, null, SystemWriteFailureStage.LOCAL_WRITE, DomainError.Unknown(error))
        }
    }

    override suspend fun finalizeCommitted(transactionId: String, generation: Long): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Success(Unit)
        if (transaction.state != SystemWriteTransactionState.CONTENT_COMMITTED.name || transaction.dirtyGeneration != generation || transaction.committedGeneration != generation) {
            return ApiResult.Failure(DomainError.OpenListError(null, "最终代次尚未完成远端确认"))
        }
        if (transactionDao.compareAndSetState(transactionId, SystemWriteTransactionState.CONTENT_COMMITTED.name, SystemWriteTransactionState.CLEANED.name, System.currentTimeMillis()) != 1) {
            return ApiResult.Failure(DomainError.OpenListError(null, "最终清理状态已变化"))
        }
        spaceManager.deleteDraftFile(transactionId)
        spaceManager.releaseDraftReservation(transactionId)
        transactionDao.delete(transactionId)
        return ApiResult.Success(Unit)
    }

    private suspend fun transition(id: String, from: SystemWriteTransactionState, to: SystemWriteTransactionState) {
        check(transactionDao.compareAndSetState(id, from.name, to.name, System.currentTimeMillis()) == 1) { "远端提交状态已变化" }
    }

    private suspend fun recover(
        id: String,
        expected: SystemWriteTransactionState?,
        failureStage: SystemWriteFailureStage,
        error: DomainError,
    ): ApiResult<Unit> {
        if (expected != null) transactionDao.markRecoveryRequired(
            id, expected.name, failureStage.name, System.currentTimeMillis(),
            error::class.simpleName, error.toString().take(256),
        )
        return ApiResult.Failure(error)
    }
}

@Singleton
class SystemDocumentWriteCoordinator @Inject constructor(
    private val transactionDao: SystemWriteTransactionDao,
    private val spaceManager: SystemDocumentSpaceManager,
    private val pathLock: SystemDocumentPathLock,
    private val committer: SystemDocumentLocalCommitter,
    private val failureNotifier: SystemDocumentFailureNotifier,
    private val writeInvalidator: SystemDocumentWriteInvalidator,
) {
    fun draftFileForExport(transactionId: String): File = spaceManager.draftFile(transactionId)

    /**
     * Re-enters the normal strong-commit path only after a caller has done
     * remote permission/target preflight. This method is never called by a
     * Worker, so a FAILED_DRAFT can never be uploaded automatically.
     */
    suspend fun retryFailedDraft(transactionId: String): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (transaction.state != SystemWriteTransactionState.FAILED_DRAFT.name) {
            return ApiResult.Failure(DomainError.OpenListError(null, "Only failed drafts may be retried"))
        }
        if (!spaceManager.draftFile(transactionId).isFile) return ApiResult.Failure(DomainError.NotFound)
        if (!spaceManager.canKeepExistingDraft()) {
            return ApiResult.Failure(DomainError.OpenListError(null, "Insufficient private storage for retry"))
        }
        return pathLock.withLock(transaction.instanceId, transaction.targetPath) {
            if (transactionDao.prepareManualRetry(transactionId, System.currentTimeMillis()) != 1) {
                return@withLock ApiResult.Failure(DomainError.OpenListError(null, "Draft state changed before retry"))
            }
            committer.submitLocalReady(transactionId, transaction.dirtyGeneration)
        }
    }

    /** Deletes user content only for a failed draft, never an uncertain remote transaction. */
    suspend fun deleteFailedDraft(transactionId: String): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (transaction.state != SystemWriteTransactionState.FAILED_DRAFT.name) {
            return ApiResult.Failure(DomainError.OpenListError(null, "Only failed drafts may be deleted"))
        }
        return pathLock.withLock(transaction.instanceId, transaction.targetPath) {
            val current = transactionDao.getById(transactionId) ?: return@withLock ApiResult.Failure(DomainError.NotFound)
            if (current.state != SystemWriteTransactionState.FAILED_DRAFT.name) {
                return@withLock ApiResult.Failure(DomainError.OpenListError(null, "Draft state changed before deletion"))
            }
            spaceManager.deleteDraftFile(transactionId)
            spaceManager.releaseDraftReservation(transactionId)
            transactionDao.delete(transactionId)
            ApiResult.Success(Unit)
        }
    }

    suspend fun open(
        instanceId: String,
        documentId: String?,
        targetPath: String,
        displayName: String,
        mode: SystemDocumentOpenMode,
        initialFile: File? = null,
        initialSizeBytes: Long = initialFile?.length() ?: 0L,
        initialLoader: (suspend (File) -> ApiResult<Unit>)? = null,
    ): ApiResult<SystemDocumentWriteHandle> {
        if (mode == SystemDocumentOpenMode.READ || !OpenListPathCodec.isSafeNormalizedPath(targetPath)) {
            return ApiResult.Failure(DomainError.PathEncodeError)
        }
        if ((mode == SystemDocumentOpenMode.READ_WRITE || mode == SystemDocumentOpenMode.WRITE_APPEND) && initialFile == null && initialLoader == null) {
            // P4 never guesses that an unmaterialized remote file is empty.
            return ApiResult.Failure(DomainError.OpenListError(null, "写入前必须安全物化现有内容"))
        }
        return pathLock.withLock(instanceId, targetPath) {
            if (initialSizeBytes < 0) return@withLock ApiResult.Failure(DomainError.PathEncodeError)
            val initialSize = initialSizeBytes
            val id = UUID.randomUUID().toString()
            if (!spaceManager.reserveDraft(id, initialSize)) {
                return@withLock ApiResult.Failure(DomainError.OpenListError(null, "本地空间不足"))
            }
            val now = System.currentTimeMillis()
            val entity = SystemWriteTransactionEntity(
                transactionId = id,
                instanceId = instanceId,
                documentId = documentId,
                targetPath = targetPath,
                displayName = displayName,
                localRelativePath = "system-documents-drafts/$id.draft",
                remoteTempPath = null,
                remoteBackupPath = null,
                remoteStageName = null,
                remoteBackupName = null,
                state = SystemWriteTransactionState.LOCAL_WRITING.name,
                dirtyGeneration = 0L,
                committedGeneration = 0L,
                reservedBytes = initialSize,
                expectedSize = initialSize,
                expectedHash = null,
                baseFingerprint = null,
                failureStage = null,
                errorCode = null,
                errorMessage = null,
                attemptCount = 0,
                lastAttemptAt = null,
                cleanupAfter = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            )
            try {
                // The journal is durable before the draft file can exist.
                transactionDao.insert(entity)
                spaceManager.confirmDraftReservation(id)
                val draft = spaceManager.draftFile(id)
                if (initialFile != null) initialFile.inputStream().use { input ->
                    draft.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
                } else if (initialLoader != null) {
                    when (val load = initialLoader.invoke(draft)) {
                        is ApiResult.Success -> Unit
                        is ApiResult.Failure -> throw IOException("无法安全物化现有文件")
                    }
                } else {
                    draft.outputStream().use { }
                }
                ApiResult.Success(
                    SystemDocumentDraftHandle(
                        entity = entity,
                        file = draft,
                        transactionDao = transactionDao,
                        spaceManager = spaceManager,
                        pathLock = pathLock,
                        committer = committer,
                        failureNotifier = failureNotifier,
                        writeInvalidator = writeInvalidator,
                        appendOnly = mode == SystemDocumentOpenMode.WRITE_APPEND,
                    ),
                )
            } catch (error: Throwable) {
                spaceManager.deleteDraftFile(id)
                spaceManager.releaseDraftReservation(id)
                transactionDao.delete(id)
                ApiResult.Failure(DomainError.Unknown(error))
            }
        }
    }

    private companion object { const val COPY_BUFFER_BYTES = 32 * 1024 }
}

private class SystemDocumentDraftHandle(
    private val entity: SystemWriteTransactionEntity,
    private val file: File,
    private val transactionDao: SystemWriteTransactionDao,
    private val spaceManager: SystemDocumentSpaceManager,
    private val pathLock: SystemDocumentPathLock,
    private val committer: SystemDocumentLocalCommitter,
    private val failureNotifier: SystemDocumentFailureNotifier,
    private val writeInvalidator: SystemDocumentWriteInvalidator,
    private val appendOnly: Boolean,
) : SystemDocumentWriteHandle {
    private var dirtyGeneration = entity.dirtyGeneration
    private var committedGeneration = entity.committedGeneration
    private var reservedBytes = entity.reservedBytes
    private var state = SystemWriteTransactionState.LOCAL_WRITING
    private var closed = false
    private var failureNotified = false

    override val sizeBytes: Long get() = file.length()

    override suspend fun read(offset: Long, size: Int): ByteArray {
        if (closed || offset < 0 || size < 0) throw IOException("草稿句柄不可读")
        return RandomAccessFile(file, "r").use { input ->
            if (offset >= input.length()) return@use ByteArray(0)
            input.seek(offset)
            ByteArray(minOf(size.toLong(), input.length() - offset).toInt()).also(input::readFully)
        }
    }

    override suspend fun write(offset: Long, bytes: ByteArray): Int {
        if (closed || offset < 0) throw IOException("草稿句柄不可写")
        return pathLock.withLock(entity.instanceId, entity.targetPath) {
            ensureWriting()
            val actualOffset = if (appendOnly) file.length() else offset
            val resultingSize = maxOf(file.length(), actualOffset + bytes.size)
            reserveFor(resultingSize)
            RandomAccessFile(file, "rw").use { output ->
                output.seek(actualOffset)
                output.write(bytes)
            }
            dirtyGeneration += 1
            persistProgress(resultingSize)
            bytes.size
        }
    }

    override suspend fun truncate(size: Long) {
        if (closed || size < 0) throw IOException("非法截断长度")
        pathLock.withLock(entity.instanceId, entity.targetPath) {
            ensureWriting()
            reserveFor(size)
            RandomAccessFile(file, "rw").use { it.setLength(size) }
            dirtyGeneration += 1
            persistProgress(size)
        }
    }

    override suspend fun fsync(): ApiResult<Unit> = try {
        pathLock.withLock(entity.instanceId, entity.targetPath) {
            if (closed) throw IOException("草稿句柄已关闭")
            if (state == SystemWriteTransactionState.CONTENT_COMMITTED && committedGeneration == dirtyGeneration) {
                return@withLock ApiResult.Success(Unit)
            }
            RandomAccessFile(file, "rw").use { it.fd.sync() }
            transition(SystemWriteTransactionState.LOCAL_READY)
            when (val committed = committer.submitLocalReady(entity.transactionId, dirtyGeneration)) {
                is ApiResult.Success -> {
                    if (committer is StrongSystemDocumentLocalCommitter) {
                        state = SystemWriteTransactionState.CONTENT_COMMITTED
                        committedGeneration = dirtyGeneration
                        // Cache/URI notifications are maintenance only. A
                        // verified remote save must never be reclassified as
                        // failed because an observer is temporarily unavailable.
                        try {
                            writeInvalidator.onCommitted(entity.instanceId, entity.documentId, entity.targetPath)
                        } catch (_: Throwable) {
                            // Deliberately best effort; a later browse refresh rehydrates these caches.
                        }
                    }
                    committed
                }
                is ApiResult.Failure -> {
                    notifyFailureOnce()
                    committed
                }
            }
        }
    } catch (error: Throwable) {
        markFailed(error)
        ApiResult.Failure(DomainError.Unknown(error))
    }

    override fun close() {
        if (closed) return
        runBlocking {
            if (fsync() is ApiResult.Success && committer is StrongSystemDocumentLocalCommitter) {
                committer.finalizeCommitted(entity.transactionId, dirtyGeneration)
            }
        }
        closed = true
    }

    private suspend fun ensureWriting() {
        if (state == SystemWriteTransactionState.LOCAL_READY || state == SystemWriteTransactionState.CONTENT_COMMITTED) {
            transition(SystemWriteTransactionState.LOCAL_WRITING)
        }
        if (state != SystemWriteTransactionState.LOCAL_WRITING) throw IOException("草稿状态不允许写入")
    }

    private suspend fun reserveFor(size: Long) {
        val additional = (size - reservedBytes).coerceAtLeast(0L)
        if (additional > 0 && !spaceManager.growDraftReservation(entity.transactionId, additional)) {
            throw IOException("ENOSPC: 私有草稿空间不足")
        }
        reservedBytes = size
    }

    private suspend fun persistProgress(size: Long) {
        transactionDao.updateLocalProgress(entity.transactionId, dirtyGeneration, size, reservedBytes, System.currentTimeMillis())
        spaceManager.confirmDraftReservation(entity.transactionId)
    }

    private suspend fun transition(next: SystemWriteTransactionState) {
        if (state == next) return
        check(state.canTransitionTo(next)) { "非法事务状态迁移: $state -> $next" }
        if (transactionDao.compareAndSetState(
                entity.transactionId,
                state.name,
                next.name,
                System.currentTimeMillis(),
            ) != 1
        ) throw IOException("事务状态已被其他恢复流程修改")
        state = next
    }

    private suspend fun markFailed(error: Throwable) {
        if (state.canTransitionTo(SystemWriteTransactionState.FAILED_DRAFT)) {
            transactionDao.markFailedDraft(
                entity.transactionId,
                state.name,
                System.currentTimeMillis() + DRAFT_TTL_MILLIS,
                System.currentTimeMillis(),
                "LOCAL_DRAFT",
                error.message?.take(256),
            )
            state = SystemWriteTransactionState.FAILED_DRAFT
            notifyFailureOnce()
        }
    }

    private fun notifyFailureOnce() {
        if (!failureNotified) {
            failureNotifier.notifySaveNeedsAttention(entity.instanceId)
            failureNotified = true
        }
    }
}

private const val DRAFT_TTL_MILLIS = 24L * 60L * 60L * 1000L
