package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.model.SystemWriteTransactionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P5-T05 persisted-journal fault matrix.  Each interruption form starts from
 * the exact durable state a process kill, timeout, or disconnected request
 * leaves behind, then uses an in-memory remote listing to prove convergence.
 * The sentinel is an unrelated user object: no recovery case may touch it.
 */
class SystemDocumentRecoveryFaultMatrixTest {
    @Test
    fun `F01 local writing leaves no remote object for every interruption`() = runTest {
        interruptionKinds.forEach { kind ->
            val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
            val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
            val gateway = mockk<SystemDocumentRemoteGateway>(relaxed = true)
            val tx = transaction(SystemWriteTransactionState.LOCAL_WRITING)
            coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
            coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
            coEvery { dao.markFailedDraft(any(), any(), any(), any(), any(), any()) } returns 1

            SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = NOW)

            coVerify(exactly = 1) { dao.markFailedDraft(tx.transactionId, tx.state, any(), NOW, "PROCESS_INTERRUPTED", any()) }
            coVerify(exactly = 0) { gateway.uploadAndVerifyStage(any(), any(), any()) }
            coVerify(exactly = 0) { gateway.renameAndVerify(any(), any(), any()) }
            coVerify(exactly = 0) { gateway.removeAndVerifyAbsent(any(), any()) }
        }
    }

    @Test
    fun `F02 through F07 converge after disconnect timeout and process termination`() = runTest {
        phases.forEach { phase ->
            interruptionKinds.forEach { kind ->
                val dao = mockk<SystemWriteTransactionDao>(relaxed = true)
                val space = mockk<SystemDocumentSpaceManager>(relaxed = true)
                val gateway = mockk<SystemDocumentRemoteGateway>()
                val tx = transaction(phase.state)
                val names = namesFor(tx)
                val remote = initialRemote(phase, names, tx.targetPath).toMutableMap()
                val sentinelPath = "/unrelated-user-file.txt"
                remote[sentinelPath] = "sentinel"

                coEvery { dao.getRecoveryCandidates() } returns listOf(tx)
                coEvery { dao.getExpiredDrafts(any()) } returns emptyList()
                every { gateway.namesFor(tx.targetPath, tx.transactionId, any()) } returns names
                coEvery { gateway.findObject(tx.instanceId, any()) } coAnswers {
                    val path = invocation.args[1] as String
                    ApiResult.Success(remote[path]?.let { objectAt(path) })
                }
                coEvery { gateway.verifyObject(tx.instanceId, any(), any(), any()) } coAnswers {
                    val path = invocation.args[1] as String
                    if (remote[path] == "new") ApiResult.Success(objectAt(path))
                    else ApiResult.Failure(io.openlist.client.core.common.DomainError.NotFound)
                }
                coEvery { gateway.renameAndVerify(tx.instanceId, any(), any()) } coAnswers {
                    val source = invocation.args[1] as String
                    val destination = "/${invocation.args[2] as String}"
                    val value = remote[source]
                    if (value == null || remote.containsKey(destination)) {
                        ApiResult.Failure(io.openlist.client.core.common.DomainError.NotFound)
                    } else {
                        remote.remove(source)
                        remote[destination] = value
                        ApiResult.Success(objectAt(destination))
                    }
                }
                coEvery { gateway.removeAndVerifyAbsent(tx.instanceId, any()) } coAnswers {
                    val path = invocation.args[1] as String
                    if (path == sentinelPath) error("unrelated object must never be removed")
                    remote.remove(path)
                    ApiResult.Success(Unit)
                }
                coEvery { dao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1
                coEvery { dao.markFailedDraft(any(), any(), any(), any(), any(), any()) } returns 1

                SystemDocumentRecoveryCoordinator(dao, space, gateway).recoverLocalDrafts(now = NOW)

                assertEquals("$phase/$kind must retain the unrelated object", "sentinel", remote[sentinelPath])
                assertEquals("$phase/$kind must leave exactly one official target", phase.expectedTarget, remote[tx.targetPath])
                assertEquals("$phase/$kind must clear transaction stage", null, remote[names.stagePath])
                assertEquals("$phase/$kind must clear transaction backup", null, remote[names.backupPath])
                coVerify(exactly = 0) { gateway.uploadAndVerifyStage(any(), any(), any()) }
                if (phase.expectedTarget == "new") {
                    coVerify(exactly = 1) { dao.delete(tx.transactionId) }
                } else {
                    coVerify(exactly = 1) { dao.markFailedDraft(tx.transactionId, tx.state, any(), NOW, any(), any()) }
                }
            }
        }
    }

    private fun initialRemote(phase: Phase, names: SystemDocumentRemoteNames, target: String): Map<String, String> = when (phase) {
        Phase.F02_STAGE_UPLOAD -> mapOf(target to "old")
        Phase.F03_STAGE_UPLOADED -> mapOf(target to "old", names.stagePath to "new")
        Phase.F04_ORIGINAL_BACKED_UP -> mapOf(names.stagePath to "new", names.backupPath to "old")
        Phase.F05_TARGET_PROMOTED,
        Phase.F06_TARGET_VERIFIED,
        Phase.F07_BACKUP_CLEANUP -> mapOf(target to "new", names.backupPath to "old")
    }

    private fun namesFor(tx: SystemWriteTransactionEntity) = SystemDocumentRemoteNames(
        "/.openlist-android-${tx.transactionId}-stage", "/.openlist-android-${tx.transactionId}-backup",
        ".openlist-android-${tx.transactionId}-stage", ".openlist-android-${tx.transactionId}-backup",
    )

    private fun objectAt(path: String) = SystemDocumentRemoteObject(path, 1, false, "https://redacted.invalid/raw", "")

    private fun transaction(state: SystemWriteTransactionState) = SystemWriteTransactionEntity(
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
        expectedHash = "hash",
        baseFingerprint = null,
        failureStage = null,
        errorCode = null,
        errorMessage = null,
        attemptCount = 1,
        lastAttemptAt = NOW,
        cleanupAfter = null,
        expiresAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private enum class Phase(val state: SystemWriteTransactionState, val expectedTarget: String) {
        F02_STAGE_UPLOAD(SystemWriteTransactionState.REMOTE_STAGING, "old"),
        F03_STAGE_UPLOADED(SystemWriteTransactionState.REMOTE_STAGED, "old"),
        F04_ORIGINAL_BACKED_UP(SystemWriteTransactionState.ORIGINAL_BACKED_UP, "new"),
        F05_TARGET_PROMOTED(SystemWriteTransactionState.TARGET_PROMOTED, "new"),
        F06_TARGET_VERIFIED(SystemWriteTransactionState.TARGET_VERIFIED, "new"),
        F07_BACKUP_CLEANUP(SystemWriteTransactionState.CLEANUP_PENDING, "new"),
    }

    private companion object {
        const val NOW = 1_000L
        val interruptionKinds = listOf("network_disconnected", "request_timeout", "process_terminated")
        val phases = Phase.entries
    }
}
