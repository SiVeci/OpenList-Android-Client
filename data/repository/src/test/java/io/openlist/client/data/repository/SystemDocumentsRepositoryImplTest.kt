package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DispatcherProvider
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.entity.SystemDocumentEntity
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.SystemWriteTransactionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.io.File

class SystemDocumentsRepositoryImplTest {
    private val instanceDao = mockk<InstanceDao>(relaxed = true)
    private val documentDao = mockk<SystemDocumentDao>(relaxed = true)
    private val transactionDao = mockk<SystemWriteTransactionDao>(relaxed = true)
    private val filesRepository = mockk<FilesRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val notifier = mockk<SystemDocumentNotifier>(relaxed = true)
    private val remoteGateway = mockk<SystemDocumentRemoteGateway>(relaxed = true)
    private val readCoordinator = mockk<SystemDocumentReadCoordinator>(relaxed = true)
    private val writeCoordinator = mockk<SystemDocumentWriteCoordinator>(relaxed = true)
    private val recoveryCoordinator = mockk<SystemDocumentRecoveryCoordinator>(relaxed = true)
    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val previewRepository = mockk<PreviewRepository>(relaxed = true)
    private val draftExporter = mockk<SystemDocumentDraftExporter>(relaxed = true)
    private val dispatcherProvider = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }

    @Test
    fun `cross instance relationship is rejected without a remote call`() = runTest {
        val parentId = UUID.randomUUID().toString()
        val childId = UUID.randomUUID().toString()
        coEvery { documentDao.getById(parentId) } returns document(parentId, "instance-a", "/")
        coEvery { documentDao.getById(childId) } returns document(childId, "instance-b", "/same-name.txt")
        everySessions()
        val repository = repository()

        assertFalse(repository.isChildDocument(parentId, childId))
        verify(exactly = 0) { filesRepository.listDirectory(any(), any(), any()) }
    }

    @Test
    fun `same instance containment accepts only safe paths`() = runTest {
        val parentId = UUID.randomUUID().toString()
        val childId = UUID.randomUUID().toString()
        coEvery { documentDao.getById(parentId) } returns document(parentId, "instance-a", "/folder")
        coEvery { documentDao.getById(childId) } returns document(childId, "instance-a", "/folder/file.txt")
        everySessions()
        val repository = repository()

        assertTrue(repository.isChildDocument(parentId, childId))
    }

    @Test
    fun `write operation remains rejected before P5 gate`() = runTest {
        everySessions()
        val repository = repository()

        val result = repository.createDocument(UUID.randomUUID().toString(), "new.txt", "text/plain")

        assertTrue(result is ApiResult.Failure)
        verify(exactly = 0) { filesRepository.listDirectory(any(), any(), any()) }
    }

    @Test
    fun `manual retry refreshes target before delegating only failed draft to write coordinator`() = runTest {
        val id = UUID.randomUUID().toString()
        val transaction = failedDraft(id)
        coEvery { transactionDao.getById(id) } returns transaction
        coEvery { remoteGateway.findObject(transaction.instanceId, transaction.targetPath) } returns ApiResult.Success(null)
        coEvery { writeCoordinator.retryFailedDraft(id) } returns ApiResult.Success(Unit)
        everySessions()
        val repository = repository()

        assertTrue(repository.retrySave(id) is ApiResult.Success)

        coVerify(exactly = 1) { remoteGateway.findObject(transaction.instanceId, transaction.targetPath) }
        coVerify(exactly = 1) { writeCoordinator.retryFailedDraft(id) }
    }

    @Test
    fun `recovery invocation remains scoped to the accessed root instance`() = runTest {
        everySessions()
        val repository = repository()

        assertTrue(repository.runRecovery("instance-a") is ApiResult.Success)

        coVerify(exactly = 1) { recoveryCoordinator.recoverLocalDrafts("instance-a", any()) }
    }

    @Test
    fun `successful draft export writes selected document then removes failed draft`() = runTest {
        val id = UUID.randomUUID().toString()
        val draft = File.createTempFile("v14-export", ".draft").apply { writeText("copy") }
        coEvery { transactionDao.getById(id) } returns failedDraft(id)
        every { writeCoordinator.draftFileForExport(id) } returns draft
        every { draftExporter.export(draft, "content://fixture/export") } returns true
        coEvery { writeCoordinator.deleteFailedDraft(id) } returns ApiResult.Success(Unit)
        everySessions()

        val result = repository().exportDraft(id, "content://fixture/export")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { writeCoordinator.deleteFailedDraft(id) }
        draft.delete()
    }

    private fun everySessions() {
        every { authRepository.observeAllSessions() } returns flowOf(emptyList())
    }

    private fun repository() = SystemDocumentsRepositoryImpl(
        instanceDao = instanceDao,
        documentDao = documentDao,
        transactionDao = transactionDao,
        mappingStore = SystemDocumentMappingStore(documentDao),
        filesRepository = filesRepository,
        authRepository = authRepository,
        notifier = notifier,
        remoteGateway = remoteGateway,
        readCoordinator = readCoordinator,
        writeCoordinator = writeCoordinator,
        recoveryCoordinator = recoveryCoordinator,
        fileCacheDao = fileCacheDao,
        previewRepository = previewRepository,
        draftExporter = draftExporter,
        dispatcherProvider = dispatcherProvider,
    )

    private fun document(id: String, instanceId: String, path: String) = SystemDocumentEntity(
        documentId = id,
        instanceId = instanceId,
        parentDocumentId = null,
        currentPath = path,
        lastKnownPath = path,
        displayName = "fixture",
        isDirectory = path == "/" || path == "/folder",
        mimeType = "text/plain",
        sizeBytes = null,
        modifiedAt = null,
        hashInfo = null,
        provider = null,
        lifecycle = SystemDocumentMappingStore.LIFECYCLE_ACTIVE,
        unsupportedCapabilitiesMask = 0L,
        capabilityUpdatedAt = null,
        lastSeenAt = 1L,
        updatedAt = 1L,
    )

    private fun failedDraft(id: String) = SystemWriteTransactionEntity(
        transactionId = id,
        instanceId = "instance-a",
        documentId = null,
        targetPath = "/fixture.txt",
        displayName = "fixture.txt",
        localRelativePath = "system-documents-drafts/$id.draft",
        remoteTempPath = null,
        remoteBackupPath = null,
        remoteStageName = null,
        remoteBackupName = null,
        state = SystemWriteTransactionState.FAILED_DRAFT.name,
        dirtyGeneration = 1,
        committedGeneration = 0,
        reservedBytes = 1,
        expectedSize = 1,
        expectedHash = null,
        baseFingerprint = null,
        failureStage = null,
        errorCode = "NETWORK",
        errorMessage = "offline",
        attemptCount = 1,
        lastAttemptAt = null,
        cleanupAfter = null,
        expiresAt = 2L,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
