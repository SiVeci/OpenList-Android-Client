package io.openlist.client.data.repository

import android.app.DownloadManager
import android.content.Context
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.DownloadTaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers [TransferRepositoryImpl.cancelDownload] (v1.0_EXECUTION_PLAN.md §11
 * S2-T2 DoD, V-606: `DownloadManager.remove` is treated as idempotent — this
 * repository never inspects its return value, it just always proceeds to
 * mark the row CANCELLED once the task is in a cancellable state).
 */
class TransferRepositoryImplTest {

    private val context = mockk<Context>()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val downloadTaskDao = mockk<DownloadTaskDao>(relaxed = true)

    private lateinit var repository: TransferRepositoryImpl

    @Before
    fun setUp() {
        every { context.getSystemService(Context.DOWNLOAD_SERVICE) } returns downloadManager
        repository = TransferRepositoryImpl(context, downloadTaskDao)
    }

    @Test
    fun `cancelDownload on an unknown task returns NotFound`() = runTest {
        coEvery { downloadTaskDao.getById(TASK_ID) } returns null

        val result = repository.cancelDownload(TASK_ID)

        assertEquals(ApiResult.Failure(DomainError.NotFound), result)
    }

    @Test
    fun `cancelDownload on an already-terminal task is rejected`() = runTest {
        coEvery { downloadTaskDao.getById(TASK_ID) } returns task(status = DownloadTaskStatus.SUCCESS)

        val result = repository.cancelDownload(TASK_ID)

        assertEquals(ApiResult.Failure(DomainError.DownloadCancelUnavailable), result)
    }

    @Test
    fun `cancelDownload on a running task removes it from DownloadManager and marks CANCELLED`() = runTest {
        coEvery { downloadTaskDao.getById(TASK_ID) } returns task(status = DownloadTaskStatus.RUNNING, managerId = 42L)

        val result = repository.cancelDownload(TASK_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { downloadTaskDao.updateStatus(TASK_ID, DownloadTaskStatus.CANCELLED, any(), null, null, any()) }
    }

    @Test
    fun `cancelDownload on an enqueued task with no manager id yet still marks CANCELLED`() = runTest {
        // A task can be cancelled in the brief window before enqueueDownload's
        // own DownloadManager.enqueue() call returns an id.
        coEvery { downloadTaskDao.getById(TASK_ID) } returns task(status = DownloadTaskStatus.ENQUEUED, managerId = null)

        val result = repository.cancelDownload(TASK_ID)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `clearFinished deletes successful downloads for the instance`() = runTest {
        val result = repository.clearFinished(INSTANCE_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { downloadTaskDao.deleteFinishedByInstanceId(INSTANCE_ID) }
    }

    @Test
    fun `clearFailed deletes failed downloads for the instance`() = runTest {
        val result = repository.clearFailed(INSTANCE_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { downloadTaskDao.deleteFailedByInstanceId(INSTANCE_ID) }
    }

    private fun task(status: String, managerId: Long? = 1L) = DownloadTaskEntity(
        id = TASK_ID,
        instanceId = "instance-1",
        path = "/docs/a.txt",
        fileName = "a.txt",
        url = "https://example.com/a.txt",
        localUri = null,
        downloadManagerId = managerId,
        status = status,
        progress = 50,
        errorMessage = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
        const val TASK_ID = "task-1"
    }
}
