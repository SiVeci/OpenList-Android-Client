package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DispatcherProvider
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.entity.SystemDocumentEntity
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.domain.SystemDocumentReadHandle
import io.openlist.client.core.domain.SystemDocumentWriteHandle
import io.openlist.client.core.domain.SystemDocumentsRepository
import io.openlist.client.core.model.SystemDocument
import io.openlist.client.core.model.SystemDocumentCapability
import io.openlist.client.core.model.Session
import io.openlist.client.core.model.SystemDocumentLifecycle
import io.openlist.client.core.model.SystemDocumentOpenMode
import io.openlist.client.core.model.SystemDocumentRoot
import io.openlist.client.core.model.SystemWriteFailureStage
import io.openlist.client.core.model.SystemWriteTransaction
import io.openlist.client.core.model.SystemWriteTransactionState
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Provider-facing repository; UI write flags remain closed until all P5/P6 gates pass. */
@Singleton
class SystemDocumentsRepositoryImpl @Inject constructor(
    private val instanceDao: InstanceDao,
    private val documentDao: SystemDocumentDao,
    private val transactionDao: SystemWriteTransactionDao,
    private val mappingStore: SystemDocumentMappingStore,
    private val filesRepository: FilesRepository,
    private val authRepository: AuthRepository,
    private val notifier: SystemDocumentNotifier,
    private val remoteGateway: SystemDocumentRemoteGateway,
    private val readCoordinator: SystemDocumentReadCoordinator,
    private val writeCoordinator: SystemDocumentWriteCoordinator,
    private val recoveryCoordinator: SystemDocumentRecoveryCoordinator,
    private val fileCacheDao: FileCacheDao,
    private val previewRepository: PreviewRepository,
    private val draftExporter: SystemDocumentDraftExporter,
    dispatcherProvider: DispatcherProvider,
) : SystemDocumentsRepository {
    private val refreshScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    init {
        // A 401 invalidates the existing session in FilesRepository.  Keeping
        // the root mapping and notifying this URI lets DocumentsUI re-query it
        // as an authentication-required root instead of making it disappear.
        authRepository.observeAllSessions()
            .onEach { notifier.notifyRootsChanged() }
            .launchIn(refreshScope)
    }

    override fun observeRoots(): Flow<List<SystemDocumentRoot>> = combine(
        instanceDao.observeAll(),
        authRepository.observeAllSessions(),
    ) { instances, sessions -> instances to sessions.mapTo(mutableSetOf()) { it.instanceId } }
        .map { (instances, authenticatedIds) ->
            instances.map { instance ->
                val root = mappingStore.ensureRoot(instance)
                SystemDocumentRoot(
                    rootId = root.documentId,
                    instanceId = instance.id,
                    title = "OpenList · ${root.displayName}",
                    documentId = root.documentId,
                    isAuthenticated = instance.id in authenticatedIds,
                )
            }
        }

    override suspend fun getDocument(documentId: String): ApiResult<SystemDocument> =
        documentDao.getById(documentId)
            ?.takeIf { it.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE }
            ?.let { ApiResult.Success(it.toDomain(capabilityFor(it))) }
            ?: ApiResult.Failure(DomainError.NotFound)

    override suspend fun listChildren(parentDocumentId: String): ApiResult<List<SystemDocument>> {
        val parent = documentDao.getById(parentDocumentId)
            ?.takeIf { it.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE && it.isDirectory }
            ?: return ApiResult.Failure(DomainError.NotFound)
        val path = parent.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        if (!OpenListPathCodec.isSafeNormalizedPath(path)) return ApiResult.Failure(DomainError.PathEncodeError)

        return when (val result = filesRepository.listDirectory(parent.instanceId, path, forceRefresh = true).first()) {
            is FileListResult.Cached -> {
                refreshInBackground(parent, path)
                ApiResult.Success(
                    documentDao.getActiveChildren(parent.instanceId, parent.documentId).map { it.toDomain() },
                )
            }
            is FileListResult.Fresh -> {
                ApiResult.Success(reconcileFreshChildren(parent, result).map { it.toDomain(capabilityFor(it)) })
            }
            is FileListResult.Error -> ApiResult.Failure(result.error)
        }
    }

    override suspend fun createDocument(parentDocumentId: String, displayName: String, mimeType: String): ApiResult<SystemDocument> {
        val parent = activeDirectory(parentDocumentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (!canWriteDirectory(parent.instanceId, requireNotNull(parent.currentPath))) return ApiResult.Failure(DomainError.Forbidden)
        val target = OpenListPathCodec.safeChild(parent.currentPath ?: return ApiResult.Failure(DomainError.NotFound), displayName)
            ?: return ApiResult.Failure(DomainError.PathEncodeError)
        if (documentDao.getActiveByPath(parent.instanceId, target) != null) return ApiResult.Failure(DomainError.OpenListError(409, "目标名称已存在"))
        val opened = writeCoordinator.open(parent.instanceId, null, target, displayName, SystemDocumentOpenMode.WRITE_TRUNCATE)
        val handle = when (opened) { is ApiResult.Success -> opened.data; is ApiResult.Failure -> return opened }
        when (val committed = handle.fsync()) {
            is ApiResult.Failure -> return committed
            is ApiResult.Success -> handle.close()
        }
        val remote = when (val found = remoteGateway.findObject(parent.instanceId, target)) {
            is ApiResult.Success -> found.data?.takeIf { !it.isDirectory } ?: return ApiResult.Failure(DomainError.OpenListError(null, "新文件未被后端确认"))
            is ApiResult.Failure -> return found
        }
        val mapped = mappingStore.mapCreatedChild(parent, displayName, false, mimeType.ifBlank { inferMimeType(displayName) }, remote.sizeBytes)
            ?: return ApiResult.Failure(DomainError.OpenListError(null, "新文件映射失败"))
        invalidateAndNotify(parent.instanceId, OpenListPathCodec.parent(target), target, parent.documentId)
        return ApiResult.Success(mapped.toDomain())
    }

    override suspend fun createDirectory(parentDocumentId: String, displayName: String): ApiResult<SystemDocument> {
        val parent = activeDirectory(parentDocumentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (!canWriteDirectory(parent.instanceId, requireNotNull(parent.currentPath))) return ApiResult.Failure(DomainError.Forbidden)
        val target = OpenListPathCodec.safeChild(parent.currentPath ?: return ApiResult.Failure(DomainError.NotFound), displayName)
            ?: return ApiResult.Failure(DomainError.PathEncodeError)
        when (val created = remoteGateway.mkdirAndVerify(parent.instanceId, target)) {
            is ApiResult.Failure -> return created
            is ApiResult.Success -> Unit
        }
        val mapped = mappingStore.mapCreatedChild(parent, displayName, true, SystemDocumentMappingStore.DIRECTORY_MIME_TYPE, null)
            ?: return ApiResult.Failure(DomainError.OpenListError(null, "新目录映射失败"))
        invalidateAndNotify(parent.instanceId, OpenListPathCodec.parent(target), target, parent.documentId)
        return ApiResult.Success(mapped.toDomain())
    }

    override suspend fun deleteDocument(documentId: String): ApiResult<Unit> {
        val document = activeDocument(documentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (!canMutate(document, Session.PERM_REMOVE)) return ApiResult.Failure(DomainError.Forbidden)
        val path = document.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        when (val removed = remoteGateway.removeAndVerifyAbsent(document.instanceId, path)) {
            is ApiResult.Failure -> return removed
            is ApiResult.Success -> Unit
        }
        documentDao.tombstonePathPrefix(document.instanceId, path, System.currentTimeMillis())
        invalidateAndNotify(document.instanceId, OpenListPathCodec.parent(path), path, document.parentDocumentId)
        return ApiResult.Success(Unit)
    }

    override suspend fun renameDocument(documentId: String, displayName: String): ApiResult<SystemDocument> {
        if (!OpenListPathCodec.isSafeDocumentName(displayName)) return ApiResult.Failure(DomainError.PathEncodeError)
        val document = activeDocument(documentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (!canMutate(document, Session.PERM_RENAME)) return ApiResult.Failure(DomainError.Forbidden)
        val oldPath = document.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        val renamed = when (val result = remoteGateway.renameAndVerify(document.instanceId, oldPath, displayName)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        documentDao.moveMappedDocument(document.documentId, document.instanceId, document.parentDocumentId, oldPath, renamed.path, displayName, System.currentTimeMillis())
        invalidateAndNotify(document.instanceId, OpenListPathCodec.parent(oldPath), oldPath, document.parentDocumentId)
        notifier.notifyDocumentChanged(document.documentId)
        return getDocument(documentId)
    }

    override suspend fun moveDocument(documentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): ApiResult<SystemDocument> {
        val document = activeDocument(documentId) ?: return ApiResult.Failure(DomainError.NotFound)
        val source = activeDirectory(sourceParentDocumentId) ?: return ApiResult.Failure(DomainError.NotFound)
        val target = activeDirectory(targetParentDocumentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (document.parentDocumentId != source.documentId || source.instanceId != document.instanceId || target.instanceId != document.instanceId) {
            return ApiResult.Failure(DomainError.Forbidden)
        }
        if (!canMutate(document, Session.PERM_MOVE) || !canWriteDirectory(target.instanceId, requireNotNull(target.currentPath))) return ApiResult.Failure(DomainError.Forbidden)
        val oldPath = document.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        val targetPath = target.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        val moved = when (val result = remoteGateway.moveOrCopyAndVerify(document.instanceId, oldPath, targetPath, copy = false)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        documentDao.moveMappedDocument(document.documentId, document.instanceId, target.documentId, oldPath, moved.path, document.displayName, System.currentTimeMillis())
        invalidateAndNotify(document.instanceId, source.currentPath ?: "/", oldPath, source.documentId)
        invalidateAndNotify(document.instanceId, targetPath, moved.path, target.documentId)
        return getDocument(documentId)
    }

    override suspend fun copyDocument(documentId: String, targetParentDocumentId: String): ApiResult<SystemDocument> {
        val document = activeDocument(documentId) ?: return ApiResult.Failure(DomainError.NotFound)
        val target = activeDirectory(targetParentDocumentId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (target.instanceId != document.instanceId) return ApiResult.Failure(DomainError.Forbidden)
        if (!canMutate(document, Session.PERM_COPY) || !canWriteDirectory(target.instanceId, requireNotNull(target.currentPath))) return ApiResult.Failure(DomainError.Forbidden)
        val oldPath = document.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        val targetPath = target.currentPath ?: return ApiResult.Failure(DomainError.NotFound)
        val copyPath = OpenListPathCodec.child(targetPath, document.displayName)
        if (documentDao.getActiveByPath(document.instanceId, copyPath) != null) return ApiResult.Failure(DomainError.OpenListError(409, "目标名称已存在"))
        val copied = when (val result = remoteGateway.moveOrCopyAndVerify(document.instanceId, oldPath, targetPath, copy = true)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        val mapped = mappingStore.mapCreatedChild(target, document.displayName, copied.isDirectory, document.mimeType, copied.sizeBytes)
            ?: return ApiResult.Failure(DomainError.OpenListError(null, "复制映射失败"))
        invalidateAndNotify(document.instanceId, targetPath, copied.path, target.documentId)
        return ApiResult.Success(mapped.toDomain())
    }

    override suspend fun isChildDocument(parentDocumentId: String, childDocumentId: String): Boolean {
        val parent = documentDao.getById(parentDocumentId) ?: return false
        val child = documentDao.getById(childDocumentId) ?: return false
        val parentPath = parent.currentPath ?: return false
        val childPath = child.currentPath ?: return false
        return parent.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE &&
            child.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE &&
            parent.instanceId == child.instanceId &&
            OpenListPathCodec.isWithin(parentPath, childPath)
    }

    override suspend fun openRead(documentId: String): ApiResult<SystemDocumentReadHandle> {
        val entity = documentDao.getById(documentId)
            ?.takeIf { it.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE && !it.isDirectory }
            ?: return ApiResult.Failure(DomainError.NotFound)
        val path = entity.currentPath
            ?.takeIf(OpenListPathCodec::isSafeNormalizedPath)
            ?: return ApiResult.Failure(DomainError.PathEncodeError)
        if (!canMutate(entity, Session.PERM_WRITE)) return ApiResult.Failure(DomainError.Forbidden)
        return when (val source = remoteGateway.resolveReadSource(entity.toDomain(), path)) {
            is ApiResult.Failure -> source
            is ApiResult.Success -> ApiResult.Success(
                SystemDocumentReadHandleImpl(readCoordinator, readCoordinator.open(source.data)),
            )
        }
    }
    override suspend fun openWrite(documentId: String, mode: SystemDocumentOpenMode): ApiResult<SystemDocumentWriteHandle> {
        if (mode == SystemDocumentOpenMode.READ) return unsupported()
        val entity = documentDao.getById(documentId)
            ?.takeIf { it.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE && !it.isDirectory }
            ?: return ApiResult.Failure(DomainError.NotFound)
        val path = entity.currentPath
            ?.takeIf(OpenListPathCodec::isSafeNormalizedPath)
            ?: return ApiResult.Failure(DomainError.PathEncodeError)
        if (mode != SystemDocumentOpenMode.READ_WRITE && mode != SystemDocumentOpenMode.WRITE_APPEND) {
            return writeCoordinator.open(entity.instanceId, entity.documentId, path, entity.displayName, mode)
        }
        return when (val source = remoteGateway.resolveReadSource(entity.toDomain(), path)) {
            is ApiResult.Failure -> source
            is ApiResult.Success -> {
                val session = readCoordinator.open(source.data)
                writeCoordinator.open(
                    instanceId = entity.instanceId,
                    documentId = entity.documentId,
                    targetPath = path,
                    displayName = entity.displayName,
                    mode = mode,
                    initialSizeBytes = source.data.sizeBytes,
                    initialLoader = { draft -> materializeIntoDraft(session, draft) },
                )
            }
        }
    }

    override fun observeRecoverableTransactions(instanceId: String): Flow<List<SystemWriteTransaction>> =
        transactionDao.observeRecoverableByInstance(instanceId).map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    override suspend fun retrySave(transactionId: String): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (transaction.state != SystemWriteTransactionState.FAILED_DRAFT.name) return unsupported()
        // Read-back is intentional: it refreshes credentials and proves the
        // target can be queried before a manual retry has any write side effect.
        when (val target = remoteGateway.findObject(transaction.instanceId, transaction.targetPath)) {
            is ApiResult.Failure -> return target
            is ApiResult.Success -> Unit
        }
        return writeCoordinator.retryFailedDraft(transactionId)
    }

    override suspend fun deleteDraft(transactionId: String): ApiResult<Unit> =
        writeCoordinator.deleteFailedDraft(transactionId)

    override suspend fun exportDraft(transactionId: String, destinationUri: String): ApiResult<Unit> {
        val transaction = transactionDao.getById(transactionId) ?: return ApiResult.Failure(DomainError.NotFound)
        if (transaction.state != SystemWriteTransactionState.FAILED_DRAFT.name) return unsupported()
        val draft = try {
            writeCoordinator.draftFileForExport(transactionId)
        } catch (_: Throwable) {
            return ApiResult.Failure(DomainError.NotFound)
        }
        if (!draft.isFile) return ApiResult.Failure(DomainError.NotFound)
        return try {
            if (!draftExporter.export(draft, destinationUri)) return ApiResult.Failure(DomainError.NotFound)
            // A successful close is the only confirmation that permits draft deletion.
            writeCoordinator.deleteFailedDraft(transactionId)
        } catch (error: Throwable) {
            ApiResult.Failure(DomainError.Unknown(error))
        }
    }

    override suspend fun runRecovery(instanceId: String?): ApiResult<Unit> {
        return try {
            recoveryCoordinator.recoverLocalDrafts(instanceId)
            ApiResult.Success(Unit)
        } catch (error: Throwable) {
            ApiResult.Failure(DomainError.Unknown(error))
        }
    }

    private suspend fun activeDocument(documentId: String): SystemDocumentEntity? =
        documentDao.getById(documentId)?.takeIf { it.lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE }

    private suspend fun activeDirectory(documentId: String): SystemDocumentEntity? =
        activeDocument(documentId)?.takeIf { it.isDirectory && it.currentPath != null }

    private suspend fun invalidateAndNotify(instanceId: String, parentPath: String, changedPath: String, parentDocumentId: String?) {
        fileCacheDao.clearDirectory(instanceId, parentPath)
        previewRepository.invalidateByPrefix(instanceId, changedPath)
        parentDocumentId?.let(notifier::notifyChildDocumentsChanged)
    }

    private suspend fun capabilityFor(entity: SystemDocumentEntity): SystemDocumentCapability {
        val path = entity.currentPath ?: return SystemDocumentCapability(canRead = true)
        val parentWritable = canWriteDirectory(entity.instanceId, OpenListPathCodec.parent(path))
        val createWritable = entity.isDirectory && canWriteDirectory(entity.instanceId, path)
        val session = authRepository.getSession(entity.instanceId)
        return SystemDocumentCapability(
            canRead = true,
            canWrite = !entity.isDirectory && parentWritable && session?.canDo(Session.PERM_WRITE) == true,
            canCreate = createWritable,
            canDelete = parentWritable && session?.canDo(Session.PERM_REMOVE) == true,
            canRename = parentWritable && session?.canDo(Session.PERM_RENAME) == true,
            canMove = parentWritable && session?.canDo(Session.PERM_MOVE) == true,
            canCopy = parentWritable && session?.canDo(Session.PERM_COPY) == true,
        )
    }

    private suspend fun canMutate(entity: SystemDocumentEntity, permission: Int): Boolean {
        val path = entity.currentPath ?: return false
        return canWriteDirectory(entity.instanceId, OpenListPathCodec.parent(path)) &&
            authRepository.getSession(entity.instanceId)?.canDo(permission) == true
    }

    /** Mutating SAF calls require a fresh server fact; cache is never enough. */
    private suspend fun canWriteDirectory(instanceId: String, path: String): Boolean = when (
        val listing = filesRepository.listDirectory(instanceId, path, forceRefresh = true)
            .first { it is FileListResult.Fresh || it is FileListResult.Error }
    ) {
        is FileListResult.Fresh -> listing.capability.canWrite == true
        is FileListResult.Error -> false
        is FileListResult.Cached -> false
    }

    private fun SystemDocumentEntity.toDomain(capability: SystemDocumentCapability = SystemDocumentCapability(canRead = true)) = SystemDocument(
        documentId = documentId,
        instanceId = instanceId,
        parentDocumentId = parentDocumentId,
        displayName = displayName,
        mimeType = mimeType,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        capability = capability,
        lifecycle = if (lifecycle == SystemDocumentMappingStore.LIFECYCLE_ACTIVE) {
            SystemDocumentLifecycle.ACTIVE
        } else {
            SystemDocumentLifecycle.TOMBSTONED
        },
    )

    private fun io.openlist.client.core.database.entity.SystemWriteTransactionEntity.toDomainOrNull(): SystemWriteTransaction? =
        runCatching {
            SystemWriteTransaction(
                transactionId = transactionId,
                instanceId = instanceId,
                documentId = documentId,
                targetPath = targetPath,
                displayName = displayName,
                state = SystemWriteTransactionState.valueOf(state),
                dirtyGeneration = dirtyGeneration,
                committedGeneration = committedGeneration,
                expiresAt = expiresAt,
                errorMessage = errorMessage,
                failureStage = failureStage?.let(SystemWriteFailureStage::valueOf),
            )
        }.getOrNull()

    private fun inferMimeType(name: String): String =
        android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    private fun <T> unsupported(): ApiResult<T> = ApiResult.Failure(
        DomainError.OpenListError(null, "系统文件写入尚未通过安全门禁"),
    )

    private suspend fun materializeIntoDraft(session: SystemDocumentReadSession, draft: File): ApiResult<Unit> = try {
        FileOutputStream(draft, false).use { output ->
            var offset = 0L
            while (offset < session.source.sizeBytes) {
                val requestSize = minOf(
                    SystemDocumentReadCoordinator.MAX_RANGE_BYTES.toLong(),
                    session.source.sizeBytes - offset,
                ).toInt()
                when (val chunk = readCoordinator.read(session, offset, requestSize)) {
                    is ApiResult.Failure -> return chunk
                    is ApiResult.Success -> {
                        if (chunk.data.isEmpty()) return ApiResult.Failure(DomainError.ServerError)
                        output.write(chunk.data)
                        offset += chunk.data.size
                    }
                }
            }
        }
        ApiResult.Success(Unit)
    } finally {
        readCoordinator.close(session)
    }

    private fun refreshInBackground(parent: SystemDocumentEntity, path: String) {
        refreshScope.launch {
            filesRepository.listDirectory(parent.instanceId, path, forceRefresh = true).collect { result ->
                if (result is FileListResult.Fresh) {
                    reconcileFreshChildren(parent, result)
                    notifier.notifyChildDocumentsChanged(parent.documentId)
                }
            }
        }
    }

    private suspend fun reconcileFreshChildren(
        parent: SystemDocumentEntity,
        result: FileListResult.Fresh,
    ): List<SystemDocumentEntity> = mappingStore.reconcileChildren(
        parent,
        result.nodes.map { node ->
            SystemRemoteDocument(
                displayName = node.name,
                isDirectory = node.isDir,
                mimeType = if (node.isDir) SystemDocumentMappingStore.DIRECTORY_MIME_TYPE else inferMimeType(node.name),
                sizeBytes = node.size,
                modifiedAt = node.modifiedAt,
            )
        },
    )
}

private class SystemDocumentReadHandleImpl(
    private val coordinator: SystemDocumentReadCoordinator,
    private val session: SystemDocumentReadSession,
) : SystemDocumentReadHandle {
    override val sizeBytes: Long = session.source.sizeBytes

    override suspend fun read(offset: Long, size: Int): ByteArray = when (val result = coordinator.read(session, offset, size)) {
        is ApiResult.Success -> result.data
        is ApiResult.Failure -> throw java.io.IOException("系统文件读取失败")
    }

    override fun close() {
        kotlinx.coroutines.runBlocking { coordinator.close(session) }
    }
}
