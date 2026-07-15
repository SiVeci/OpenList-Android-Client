package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.model.SystemWriteTransactionState
import io.openlist.client.core.model.SystemWriteFailureStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StrongSystemDocumentLocalCommitterTest {
    @Test
    fun `commit stages verifies backs up promotes verifies and cleans in order`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>()
        val draft = File.createTempFile("v14-strong", ".draft").apply { writeText("new") }
        val tx = transaction()
        val names = SystemDocumentRemoteNames("/.stage", "/.backup", ".stage", ".backup")
        val target = remote("/fixture.txt")
        coEvery { dao.getById(tx.transactionId) } returns tx
        every { space.draftFile(tx.transactionId) } returns draft
        every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
        every { gateway.localSha256(draft) } returns "hash"
        coEvery { dao.beginRemoteCommit(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { gateway.uploadAndVerifyStage(tx.instanceId, names.stagePath, draft) } returns ApiResult.Success(remote(names.stagePath))
        coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { gateway.findObject(tx.instanceId, tx.targetPath) } returns ApiResult.Success(remote(tx.targetPath))
        coEvery { gateway.renameAndVerify(tx.instanceId, tx.targetPath, names.backupName) } returns ApiResult.Success(remote(names.backupPath))
        coEvery { gateway.renameAndVerify(tx.instanceId, names.stagePath, "fixture.txt") } returns ApiResult.Success(target)
        coEvery { gateway.verifyObject(tx.instanceId, tx.targetPath, draft.length(), "hash") } returns ApiResult.Success(target)
        coEvery { gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) } returns ApiResult.Success(Unit)

        val result = StrongSystemDocumentLocalCommitter(dao, space, gateway).submitLocalReady(tx.transactionId, 1)

        assertTrue(result is ApiResult.Success)
        coVerifyOrder {
            gateway.uploadAndVerifyStage(tx.instanceId, names.stagePath, draft)
            gateway.renameAndVerify(tx.instanceId, tx.targetPath, names.backupName)
            gateway.renameAndVerify(tx.instanceId, names.stagePath, "fixture.txt")
            gateway.verifyObject(tx.instanceId, tx.targetPath, draft.length(), "hash")
            gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath)
        }
        coVerify(exactly = 0) { gateway.uploadAndVerifyStage(tx.instanceId, tx.targetPath, any()) }
        coVerify(exactly = 1) { dao.markCommittedGeneration(tx.transactionId, 1, any()) }
        draft.delete()
    }

    @Test
    fun `unknown upload outcome remains recoverable and never reports local success`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>()
        val draft = File.createTempFile("v14-strong", ".draft").apply { writeText("new") }
        val tx = transaction()
        val names = SystemDocumentRemoteNames("/.stage", "/.backup", ".stage", ".backup")
        coEvery { dao.getById(tx.transactionId) } returns tx
        every { space.draftFile(tx.transactionId) } returns draft
        every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
        every { gateway.localSha256(draft) } returns "hash"
        coEvery { dao.beginRemoteCommit(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { gateway.uploadAndVerifyStage(tx.instanceId, names.stagePath, draft) } returns ApiResult.Failure(DomainError.Timeout)
        coEvery { dao.markRecoveryRequired(any(), any(), any(), any(), any(), any()) } returns 1

        val result = StrongSystemDocumentLocalCommitter(dao, space, gateway).submitLocalReady(tx.transactionId, 1)

        assertTrue(result is ApiResult.Failure)
        coVerify(exactly = 1) {
            dao.markRecoveryRequired(tx.transactionId, SystemWriteTransactionState.REMOTE_STAGING.name, SystemWriteFailureStage.STAGE_UPLOAD.name, any(), any(), any())
        }
        coVerify(exactly = 0) { dao.markCommittedGeneration(any(), any(), any()) }
        draft.delete()
    }

    private fun transaction() = SystemWriteTransactionEntity(
        transactionId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        instanceId = "fixture",
        documentId = null,
        targetPath = "/fixture.txt",
        displayName = "fixture.txt",
        localRelativePath = "system-documents-drafts/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11.draft",
        remoteTempPath = null,
        remoteBackupPath = null,
        remoteStageName = null,
        remoteBackupName = null,
        state = SystemWriteTransactionState.LOCAL_READY.name,
        dirtyGeneration = 1,
        committedGeneration = 0,
        reservedBytes = 3,
        expectedSize = 3,
        expectedHash = null,
        baseFingerprint = null,
        failureStage = null,
        errorCode = null,
        errorMessage = null,
        attemptCount = 0,
        lastAttemptAt = null,
        cleanupAfter = null,
        expiresAt = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun remote(path: String) = SystemDocumentRemoteObject(path, 3, false, "https://redacted.invalid/raw", "")
}
