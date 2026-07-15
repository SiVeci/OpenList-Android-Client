package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.model.SystemDocumentOpenMode
import io.openlist.client.core.domain.SystemDocumentFailureNotifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemDocumentWriteCoordinatorTest {
    private val transactionDao = mockk<SystemWriteTransactionDao>(relaxed = true)
    private val spaceManager = mockk<SystemDocumentSpaceManager>(relaxed = true)
    private val committer = mockk<SystemDocumentLocalCommitter>()
    private val failureNotifier = mockk<SystemDocumentFailureNotifier>(relaxed = true)
    private val writeInvalidator = mockk<SystemDocumentWriteInvalidator>(relaxed = true)

    @Test
    fun `local draft journals before writing and fsync uses local-only seam`() = runTest {
        val draft = File.createTempFile("v14-draft", ".tmp").apply { delete() }
        every { spaceManager.draftFile(any()) } returns draft
        coEvery { spaceManager.reserveDraft(any(), 0L) } returns true
        coEvery { spaceManager.growDraftReservation(any(), any()) } returns true
        coEvery { transactionDao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { committer.submitLocalReady(any(), any()) } returns ApiResult.Success(Unit)
        val coordinator = SystemDocumentWriteCoordinator(transactionDao, spaceManager, SystemDocumentPathLock(), committer, failureNotifier, writeInvalidator)

        val opened = coordinator.open("fixture", null, "/note.txt", "note.txt", SystemDocumentOpenMode.WRITE_TRUNCATE)
        assertTrue("opened=$opened", opened is ApiResult.Success)
        val handle = (opened as ApiResult.Success).data
        assertEquals(3, handle.write(0, "abc".encodeToByteArray()))
        assertEquals(2, handle.write(3, "de".encodeToByteArray()))
        assertEquals(5L, handle.sizeBytes)
        assertArrayEquals("abcde".encodeToByteArray(), handle.read(0, 5))
        assertTrue(handle.fsync() is ApiResult.Success)

        coVerify(exactly = 1) { transactionDao.insert(any()) }
        coVerify(exactly = 1) { committer.submitLocalReady(any(), 2L) }
        coVerify(atLeast = 2) { transactionDao.updateLocalProgress(any(), any(), any(), any(), any()) }
        handle.close()
        draft.delete()
    }

    @Test
    fun `rw refuses to pretend an unmaterialized remote document is empty`() = runTest {
        val coordinator = SystemDocumentWriteCoordinator(transactionDao, spaceManager, SystemDocumentPathLock(), committer, failureNotifier, writeInvalidator)

        val result = coordinator.open("fixture", "document", "/existing.txt", "existing.txt", SystemDocumentOpenMode.READ_WRITE)

        assertTrue(result is ApiResult.Failure)
        coVerify(exactly = 0) { transactionDao.insert(any()) }
    }

    @Test
    fun `rw materializes supplied content and wa always appends`() = runTest {
        val initial = File.createTempFile("v14-source", ".tmp").apply { writeText("abc") }
        val draft = File.createTempFile("v14-draft", ".tmp").apply { delete() }
        every { spaceManager.draftFile(any()) } returns draft
        coEvery { spaceManager.reserveDraft(any(), 3L) } returns true
        coEvery { spaceManager.growDraftReservation(any(), any()) } returns true
        coEvery { transactionDao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { committer.submitLocalReady(any(), any()) } returns ApiResult.Success(Unit)
        val coordinator = SystemDocumentWriteCoordinator(transactionDao, spaceManager, SystemDocumentPathLock(), committer, failureNotifier, writeInvalidator)

        val rw = coordinator.open("fixture", "document", "/existing.txt", "existing.txt", SystemDocumentOpenMode.READ_WRITE, initial)
        assertTrue(rw is ApiResult.Success)
        assertArrayEquals("abc".encodeToByteArray(), (rw as ApiResult.Success).data.read(0, 3))
        rw.data.close()

        val wa = coordinator.open("fixture", "document", "/existing.txt", "existing.txt", SystemDocumentOpenMode.WRITE_APPEND, initial)
        assertTrue(wa is ApiResult.Success)
        val handle = (wa as ApiResult.Success).data
        assertEquals(1, handle.write(0, "d".encodeToByteArray()))
        assertArrayEquals("abcd".encodeToByteArray(), handle.read(0, 4))
        handle.close()
        initial.delete()
        draft.delete()
    }

    @Test
    fun `failed close-time save notifies by instance without exposing draft details`() = runTest {
        val draft = File.createTempFile("v14-draft", ".tmp").apply { delete() }
        every { spaceManager.draftFile(any()) } returns draft
        coEvery { spaceManager.reserveDraft(any(), 0L) } returns true
        coEvery { spaceManager.growDraftReservation(any(), any()) } returns true
        coEvery { transactionDao.compareAndSetState(any(), any(), any(), any(), any(), any()) } returns 1
        coEvery { committer.submitLocalReady(any(), any()) } returns ApiResult.Failure(DomainError.NetworkUnavailable)
        val coordinator = SystemDocumentWriteCoordinator(transactionDao, spaceManager, SystemDocumentPathLock(), committer, failureNotifier, writeInvalidator)

        val handle = (coordinator.open("fixture", null, "/note.txt", "note.txt", SystemDocumentOpenMode.WRITE_TRUNCATE) as ApiResult.Success).data
        handle.write(0, "draft".encodeToByteArray())

        assertTrue(handle.fsync() is ApiResult.Failure)
        handle.close()
        coVerify(exactly = 1) { failureNotifier.notifySaveNeedsAttention("fixture") }
        draft.delete()
    }
}
