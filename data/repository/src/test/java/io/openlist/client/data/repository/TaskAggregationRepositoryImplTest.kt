package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.model.TaskSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [TaskAggregationRepositoryImpl.cancelTask] (LOCAL_DOWNLOAD branch
 * changed from a hard "not supported" error to forwarding to
 * [TransferRepository.cancelDownload]) and the new [TaskAggregationRepositoryImpl
 * .retryTask] (v1.0_EXECUTION_PLAN.md §11 S2-T3 DoD: "分发/不支持源").
 */
class TaskAggregationRepositoryImplTest {

    private val uploadTaskDao = mockk<UploadTaskDao> { every { observeByInstance(any()) } returns flowOf(emptyList()) }
    private val downloadTaskDao = mockk<DownloadTaskDao> { every { observeByInstance(any()) } returns flowOf(emptyList()) }
    private val remoteTaskDao = mockk<RemoteTaskDao>()
    private val uploadRepository = mockk<UploadRepository>()
    private val taskRepository = mockk<TaskRepository>()
    private val transferRepository = mockk<TransferRepository>()

    private lateinit var repository: TaskAggregationRepositoryImpl

    @Before
    fun setUp() {
        repository = TaskAggregationRepositoryImpl(
            uploadTaskDao = uploadTaskDao,
            downloadTaskDao = downloadTaskDao,
            remoteTaskDao = remoteTaskDao,
            uploadRepository = uploadRepository,
            taskRepository = taskRepository,
            transferRepository = transferRepository,
        )
    }

    // --- cancelTask ---------------------------------------------------------

    @Test
    fun `cancelTask LOCAL_UPLOAD forwards to UploadRepository`() = runTest {
        coEvery { uploadRepository.cancelUpload(TASK_ID) } returns ApiResult.Success(Unit)

        val result = repository.cancelTask(INSTANCE_ID, TASK_ID, TaskSource.LOCAL_UPLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `cancelTask LOCAL_DOWNLOAD now forwards to TransferRepository cancelDownload`() = runTest {
        coEvery { transferRepository.cancelDownload(TASK_ID) } returns ApiResult.Success(Unit)

        val result = repository.cancelTask(INSTANCE_ID, TASK_ID, TaskSource.LOCAL_DOWNLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `cancelTask REMOTE with no cached row returns NotFound`() = runTest {
        coEvery { remoteTaskDao.getById(TASK_ID, INSTANCE_ID) } returns null

        val result = repository.cancelTask(INSTANCE_ID, TASK_ID, TaskSource.REMOTE)

        assertEquals(ApiResult.Failure(DomainError.NotFound), result)
    }

    @Test
    fun `cancelTask REMOTE with a cached row forwards to TaskRepository`() = runTest {
        val cached = remoteTask()
        coEvery { remoteTaskDao.getById(TASK_ID, INSTANCE_ID) } returns cached
        coEvery { taskRepository.cancelRemoteTask(INSTANCE_ID, cached.taskType, TASK_ID) } returns ApiResult.Success(Unit)

        val result = repository.cancelTask(INSTANCE_ID, TASK_ID, TaskSource.REMOTE)

        assertEquals(ApiResult.Success(Unit), result)
    }

    // --- retryTask -----------------------------------------------------------

    @Test
    fun `retryTask LOCAL_UPLOAD forwards to UploadRepository retryUpload`() = runTest {
        coEvery { uploadRepository.retryUpload(TASK_ID) } returns ApiResult.Success(Unit)

        val result = repository.retryTask(INSTANCE_ID, TASK_ID, TaskSource.LOCAL_UPLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `retryTask LOCAL_DOWNLOAD returns an explicit unsupported error`() = runTest {
        val result = repository.retryTask(INSTANCE_ID, TASK_ID, TaskSource.LOCAL_DOWNLOAD) as ApiResult.Failure

        assertTrue(result.error is DomainError.OpenListError)
    }

    @Test
    fun `retryTask REMOTE returns an explicit unsupported error`() = runTest {
        val result = repository.retryTask(INSTANCE_ID, TASK_ID, TaskSource.REMOTE) as ApiResult.Failure

        assertTrue(result.error is DomainError.OpenListError)
    }

    // --- clearFinishedTasks -------------------------------------------------

    @Test
    fun `clearFinishedTasks LOCAL_UPLOAD forwards to UploadRepository`() = runTest {
        coEvery { uploadRepository.clearFinished(INSTANCE_ID) } returns ApiResult.Success(Unit)

        val result = repository.clearFinishedTasks(INSTANCE_ID, TaskSource.LOCAL_UPLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `clearFinishedTasks LOCAL_DOWNLOAD forwards to TransferRepository`() = runTest {
        coEvery { transferRepository.clearFinished(INSTANCE_ID) } returns ApiResult.Success(Unit)

        val result = repository.clearFinishedTasks(INSTANCE_ID, TaskSource.LOCAL_DOWNLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `clearFinishedTasks REMOTE clears successful remote cache rows`() = runTest {
        coEvery { remoteTaskDao.deleteFinishedByInstanceId(INSTANCE_ID) } returns Unit

        val result = repository.clearFinishedTasks(INSTANCE_ID, TaskSource.REMOTE)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { remoteTaskDao.deleteFinishedByInstanceId(INSTANCE_ID) }
    }

    private fun remoteTask() = RemoteTaskEntity(
        id = TASK_ID,
        instanceId = INSTANCE_ID,
        taskType = "copy",
        title = "copy a.txt",
        stateRaw = 1,
        status = "RUNNING",
        progress = null,
        targetPath = "/docs",
        errorMessage = null,
        totalBytes = null,
        rawJson = "{}",
        startTime = null,
        endTime = null,
        cachedAt = 0,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
        const val TASK_ID = "task-1"
    }
}
