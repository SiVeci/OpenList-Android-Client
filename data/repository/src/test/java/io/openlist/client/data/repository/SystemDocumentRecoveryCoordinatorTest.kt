package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.model.SystemWriteTransactionState
import io.openlist.client.core.common.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SystemDocumentRecoveryCoordinatorTest {
    @Test
    fun `interrupted local write becomes expiring draft with no remote seam`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>()
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val transaction = entity(SystemWriteTransactionState.LOCAL_WRITING)
        coEvery { dao.getRecoveryCandidates() } returns listOf(transaction)
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
        coEvery { dao.markFailedDraft(any(), any(), any(), any(), any(), any()) } returns 1
        val now = 1_000L

        SystemDocumentRecoveryCoordinator(dao, space, mockk(relaxed = true)).recoverLocalDrafts(now = now)

        coVerify(exactly = 1) {
            dao.markFailedDraft(transaction.transactionId, "LOCAL_WRITING", now + 24L * 60L * 60L * 1000L, now, "PROCESS_INTERRUPTED", any())
        }
        verify(exactly = 0) { space.deleteDraftFile(any()) }
    }

    @Test
    fun `failed draft is never automatically uploaded or remotely inspected`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>()
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>(relaxed = true)
        coEvery { dao.getRecoveryCandidates() } returns listOf(entity(SystemWriteTransactionState.FAILED_DRAFT))
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()

        SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

        verify(exactly = 0) { gateway.namesFor(any(), any(), any()) }
        coVerify(exactly = 0) { gateway.findObject(any(), any()) }
        verify(exactly = 0) { space.deleteDraftFile(any()) }
    }

    @Test
    fun `remote transaction missing a verifiable hash remains recoverable without mutation`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>()
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>(relaxed = true)
        val transaction = entity(SystemWriteTransactionState.REMOTE_STAGING)
        coEvery { dao.getRecoveryCandidates() } returns listOf(transaction)
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
        every { gateway.namesFor(transaction.targetPath, transaction.transactionId, any()) } returns SystemDocumentRemoteNames(
            stagePath = "/.openlist-android-${transaction.transactionId}-stage",
            backupPath = "/.openlist-android-${transaction.transactionId}-backup",
            stageName = ".openlist-android-${transaction.transactionId}-stage",
            backupName = ".openlist-android-${transaction.transactionId}-backup",
        )
        coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1

        SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

        coVerify(exactly = 1) { dao.compareAndSetState(transaction.transactionId, transaction.state, SystemWriteTransactionState.RECOVERY_REQUIRED.name, 1_000L, "MISSING_EXPECTED_HASH", null) }
        coVerify(exactly = 0) { gateway.findObject(any(), any()) }
        coVerify(exactly = 0) { gateway.removeAndVerifyAbsent(any(), any()) }
    }

    @Test
    fun `F02 through F07 without proof never mutate a remote object`() = runTest {
        val remoteStates = listOf(
            SystemWriteTransactionState.REMOTE_STAGING,
            SystemWriteTransactionState.REMOTE_STAGED,
            SystemWriteTransactionState.ORIGINAL_BACKED_UP,
            SystemWriteTransactionState.TARGET_PROMOTED,
            SystemWriteTransactionState.TARGET_VERIFIED,
            SystemWriteTransactionState.CLEANUP_PENDING,
        )
        remoteStates.forEach { state ->
            val dao = mockk<SystemWriteTransactionDao>()
            val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
            val gateway = mockk<SystemDocumentRemoteGateway>(relaxed = true)
            val tx = entity(state)
            coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
            coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
            every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns SystemDocumentRemoteNames(
                "/.openlist-android-${tx.transactionId}-stage", "/.openlist-android-${tx.transactionId}-backup",
                ".openlist-android-${tx.transactionId}-stage", ".openlist-android-${tx.transactionId}-backup",
            )
            coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1

            SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

            coVerify(exactly = 0) { gateway.findObject(any(), any()) }
            coVerify(exactly = 0) { gateway.renameAndVerify(any(), any(), any()) }
            coVerify(exactly = 0) { gateway.removeAndVerifyAbsent(any(), any()) }
            verify(exactly = 0) { space.deleteDraftFile(any()) }
        }
    }

    @Test
    fun `verified target converges F06 and F07 by removing only owned remnants`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>()
        val tx = entity(SystemWriteTransactionState.TARGET_VERIFIED, expectedHash = "abc")
        val names = namesFor(tx)
        val target = remote(tx.targetPath)
        coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
        every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
        coEvery { gateway.findObject(tx.instanceId, tx.targetPath) } returns ApiResult.Success(target)
        coEvery { gateway.findObject(tx.instanceId, names.stagePath) } returns ApiResult.Success(remote(names.stagePath))
        coEvery { gateway.findObject(tx.instanceId, names.backupPath) } returns ApiResult.Success(remote(names.backupPath))
        coEvery { gateway.verifyObject(tx.instanceId, tx.targetPath, 1, "abc") } returns ApiResult.Success(target)
        coEvery { gateway.verifyObject(tx.instanceId, names.stagePath, 1, "abc") } returns ApiResult.Success(remote(names.stagePath))
        coEvery { gateway.removeAndVerifyAbsent(tx.instanceId, names.stagePath) } returns ApiResult.Success(Unit)
        coEvery { gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) } returns ApiResult.Success(Unit)
        coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1

        SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

        coVerify(exactly = 1) { gateway.removeAndVerifyAbsent(tx.instanceId, names.stagePath) }
        coVerify(exactly = 1) { gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) }
        coVerify(exactly = 1) { dao.delete(tx.transactionId) }
        verify(exactly = 1) { space.deleteDraftFile(tx.transactionId) }
    }

    @Test
    fun `verified stage promotes only after target absence and then cleans owned backup`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>()
        val tx = entity(SystemWriteTransactionState.ORIGINAL_BACKED_UP, expectedHash = "abc")
        val names = namesFor(tx)
        val stage = remote(names.stagePath)
        val backup = remote(names.backupPath)
        val target = remote(tx.targetPath)
        coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
        every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
        coEvery { gateway.findObject(tx.instanceId, tx.targetPath) } returns ApiResult.Success(null)
        coEvery { gateway.findObject(tx.instanceId, names.stagePath) } returns ApiResult.Success(stage)
        coEvery { gateway.findObject(tx.instanceId, names.backupPath) } returns ApiResult.Success(backup)
        coEvery { gateway.verifyObject(tx.instanceId, names.stagePath, 1, "abc") } returns ApiResult.Success(stage)
        coEvery { gateway.renameAndVerify(tx.instanceId, names.stagePath, "fixture.txt") } returns ApiResult.Success(target)
        coEvery { gateway.verifyObject(tx.instanceId, tx.targetPath, 1, "abc") } returns ApiResult.Success(target)
        coEvery { gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) } returns ApiResult.Success(Unit)
        coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1

        SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

        coVerify(exactly = 1) { gateway.renameAndVerify(tx.instanceId, names.stagePath, "fixture.txt") }
        coVerify(exactly = 1) { gateway.removeAndVerifyAbsent(tx.instanceId, names.backupPath) }
        coVerify(exactly = 1) { dao.delete(tx.transactionId) }
    }

    @Test
    fun `unverified stage with sole backup restores original and preserves draft`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        val gateway = mockk<SystemDocumentRemoteGateway>()
        val tx = entity(SystemWriteTransactionState.ORIGINAL_BACKED_UP, expectedHash = "abc")
        val names = namesFor(tx)
        coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
        coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
        every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
        coEvery { gateway.findObject(tx.instanceId, tx.targetPath) } returns ApiResult.Success(null)
        coEvery { gateway.findObject(tx.instanceId, names.stagePath) } returns ApiResult.Success(null)
        coEvery { gateway.findObject(tx.instanceId, names.backupPath) } returns ApiResult.Success(remote(names.backupPath))
        coEvery { gateway.renameAndVerify(tx.instanceId, names.backupPath, "fixture.txt") } returns ApiResult.Success(remote(tx.targetPath))
        coEvery { dao.markFailedDraft(any(), any(), any(), any(), any(), any()) } returns 1

        SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = 1_000L)

        coVerify(exactly = 1) { gateway.renameAndVerify(tx.instanceId, names.backupPath, "fixture.txt") }
        coVerify(exactly = 1) { dao.markFailedDraft(tx.transactionId, tx.state, any(), 1_000L, "ORIGINAL_RESTORED", null) }
        verify(exactly = 0) { space.deleteDraftFile(any()) }
    }

    @Test
    fun `root recovery only queries the requested instance`() = runTest {
        val dao = mockk<SystemWriteTransactionDao>()
        val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
        coEvery { dao.getRecoveryCandidatesForInstance("instance-a") } returns emptyList()
        coEvery { dao.getExpiredDraftsForInstance("instance-a", 1_000L) } returns emptyList()

        SystemDocumentRecoveryCoordinator(dao, space, mockk(relaxed = true)).recoverLocalDrafts("instance-a", 1_000L)

        coVerify(exactly = 1) { dao.getRecoveryCandidatesForInstance("instance-a") }
        coVerify(exactly = 0) { dao.getRecoveryCandidates() }
    }

    private fun namesFor(tx: SystemWriteTransactionEntity) = SystemDocumentRemoteNames(
        "/.openlist-android-${tx.transactionId}-stage", "/.openlist-android-${tx.transactionId}-backup",
        ".openlist-android-${tx.transactionId}-stage", ".openlist-android-${tx.transactionId}-backup",
    )

    private fun remote(path: String) = SystemDocumentRemoteObject(path, 1, false, "https://redacted.invalid/raw", "")

    private fun entity(state: SystemWriteTransactionState, expectedHash: String? = null) = SystemWriteTransactionEntity(
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
        state = state.name,
        dirtyGeneration = 1,
        committedGeneration = 0,
        reservedBytes = 1,
        expectedSize = 1,
        expectedHash = expectedHash,
        baseFingerprint = null,
        failureStage = null,
        errorCode = null,
        errorMessage = null,
        attemptCount = 0,
        lastAttemptAt = null,
        cleanupAfter = null,
        expiresAt = null,
        createdAt = 0,
        updatedAt = 0,
    )
}
