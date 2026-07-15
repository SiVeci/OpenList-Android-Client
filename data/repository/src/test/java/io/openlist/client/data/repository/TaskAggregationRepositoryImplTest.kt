package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.SystemWriteTransactionEntity
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.domain.SystemDocumentsRepository
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.model.SystemDocumentRecoveryAction
import io.openlist.client.core.model.SystemWriteTransactionState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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
    private val remoteTaskDao = mockk<RemoteTaskDao> { every { observeByInstance(any()) } returns flowOf(emptyList()) }
    private val uploadRepository = mockk<UploadRepository>()
    private val taskRepository = mockk<TaskRepository>()
    private val transferRepository = mockk<TransferRepository>()
    private val systemWriteTransactionDao = mockk<SystemWriteTransactionDao> {
        every { observeRecoverableByInstance(any()) } returns flowOf(emptyList())
    }
    private val systemDocumentsRepository = mockk<SystemDocumentsRepository>()
    private val instanceDao = mockk<InstanceDao> { every { observeAll() } returns flowOf(listOf(instance())) }

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
            systemWriteTransactionDao = systemWriteTransactionDao,
            systemDocumentsRepository = systemDocumentsRepository,
            instanceDao = instanceDao,
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

    @Test
    fun `retryTask SYSTEM_DOCUMENT stays in system document repository`() = runTest {
        coEvery { systemDocumentsRepository.retrySave(TASK_ID) } returns ApiResult.Success(Unit)

        val result = repository.retryTask(INSTANCE_ID, TASK_ID, TaskSource.SYSTEM_DOCUMENT)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify(exactly = 1) { systemDocumentsRepository.retrySave(TASK_ID) }
        coVerify(exactly = 0) { uploadRepository.retryUpload(any()) }
    }

    @Test
    fun `failed system draft joins existing task stream with TTL and recovery actions`() = runTest {
        every { systemWriteTransactionDao.observeRecoverableByInstance(INSTANCE_ID) } returns flowOf(
            listOf(systemDraft(SystemWriteTransactionState.FAILED_DRAFT)),
        )

        val task = repository.observeAllTasks(INSTANCE_ID).first().single()

        assertEquals(TaskSource.SYSTEM_DOCUMENT, task.source)
        assertEquals(TaskType.SYSTEM_SAVE, task.type)
        assertEquals(UnifiedTaskStatus.FAILED, task.status)
        assertEquals(9_999L, task.expiresAt)
        assertEquals("测试实例", task.instanceName)
        assertEquals("folder", task.directorySummary)
        assertEquals(
            setOf(SystemDocumentRecoveryAction.RETRY_SAVE, SystemDocumentRecoveryAction.EXPORT_COPY, SystemDocumentRecoveryAction.DELETE_DRAFT),
            task.recoveryActions,
        )
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

    @Test
    fun `clearFailedTasks LOCAL_UPLOAD forwards to UploadRepository`() = runTest {
        coEvery { uploadRepository.clearFailed(INSTANCE_ID) } returns ApiResult.Success(Unit)

        val result = repository.clearFailedTasks(INSTANCE_ID, TaskSource.LOCAL_UPLOAD)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `clearFailedTasks null source clears all local and remote failed rows`() = runTest {
        coEvery { uploadRepository.clearFailed(INSTANCE_ID) } returns ApiResult.Success(Unit)
        coEvery { transferRepository.clearFailed(INSTANCE_ID) } returns ApiResult.Success(Unit)
        coEvery { remoteTaskDao.deleteFailedByInstanceId(INSTANCE_ID) } returns Unit

        val result = repository.clearFailedTasks(INSTANCE_ID, null)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { uploadRepository.clearFailed(INSTANCE_ID) }
        coVerify { transferRepository.clearFailed(INSTANCE_ID) }
        coVerify { remoteTaskDao.deleteFailedByInstanceId(INSTANCE_ID) }
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

    private fun systemDraft(state: SystemWriteTransactionState) = SystemWriteTransactionEntity(
        transactionId = TASK_ID,
        instanceId = INSTANCE_ID,
        documentId = null,
        targetPath = "/folder/fixture.txt",
        displayName = "fixture.txt",
        localRelativePath = "system-documents-drafts/$TASK_ID.draft",
        remoteTempPath = null,
        remoteBackupPath = null,
        remoteStageName = null,
        remoteBackupName = null,
        state = state.name,
        dirtyGeneration = 1,
        committedGeneration = 0,
        reservedBytes = 1,
        expectedSize = 1,
        expectedHash = null,
        baseFingerprint = null,
        failureStage = null,
        errorCode = "NETWORK",
        errorMessage = null,
        attemptCount = 1,
        lastAttemptAt = null,
        cleanupAfter = null,
        expiresAt = 9_999L,
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun instance() = InstanceEntity(
        id = INSTANCE_ID,
        name = "测试实例",
        baseUrl = "https://example.invalid",
        createdAt = 0L,
        updatedAt = 0L,
        lastUsedAt = 0L,
        isCurrent = true,
        note = null,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
        const val TASK_ID = "task-1"
    }
}
