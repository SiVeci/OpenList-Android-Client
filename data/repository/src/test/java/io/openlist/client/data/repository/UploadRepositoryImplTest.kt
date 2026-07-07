package io.openlist.client.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.UploadTaskEntity
import io.openlist.client.core.database.entity.UploadTaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Covers [UploadRepositoryImpl.retryUpload] (v1.0_EXECUTION_PLAN.md §11 S2-T1
 * DoD: "复位/URI 失效/状态门控").
 */
class UploadRepositoryImplTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val uploadTaskDao = mockk<UploadTaskDao>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private lateinit var repository: UploadRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        repository = UploadRepositoryImpl(context, uploadTaskDao, workManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `retryUpload on an unknown task returns NotFound`() = runTest {
        coEvery { uploadTaskDao.getById(TASK_ID) } returns null

        val result = repository.retryUpload(TASK_ID)

        assertEquals(ApiResult.Failure(DomainError.NotFound), result)
    }

    @Test
    fun `retryUpload on a non-FAILED task is rejected`() = runTest {
        coEvery { uploadTaskDao.getById(TASK_ID) } returns task(status = UploadTaskStatus.RUNNING)

        val result = repository.retryUpload(TASK_ID)

        assertEquals(ApiResult.Failure(DomainError.UploadRetryUnavailable), result)
    }

    @Test
    fun `retryUpload with a no-longer-readable SAF grant is rejected`() = runTest {
        coEvery { uploadTaskDao.getById(TASK_ID) } returns task(status = UploadTaskStatus.FAILED)
        every { contentResolver.openInputStream(any()) } throws SecurityException("permission revoked")

        val result = repository.retryUpload(TASK_ID)

        assertEquals(ApiResult.Failure(DomainError.UploadRetryUnavailable), result)
    }

    @Test
    fun `retryUpload with a readable grant resets progress and re-enqueues`() = runTest {
        coEvery { uploadTaskDao.getById(TASK_ID) } returns task(status = UploadTaskStatus.FAILED)
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(ByteArray(0))

        val result = repository.retryUpload(TASK_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { uploadTaskDao.updateProgress(TASK_ID, UploadTaskStatus.PENDING, 0L, null, any()) }
        coVerify { uploadTaskDao.setWorkRequestId(TASK_ID, any()) }
    }

    @Test
    fun `clearFinished deletes successful uploads for the instance`() = runTest {
        val result = repository.clearFinished(INSTANCE_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { uploadTaskDao.deleteFinishedByInstanceId(INSTANCE_ID) }
    }

    @Test
    fun `clearFailed deletes failed uploads for the instance`() = runTest {
        val result = repository.clearFailed(INSTANCE_ID)

        assertEquals(ApiResult.Success(Unit), result)
        coVerify { uploadTaskDao.deleteFailedByInstanceId(INSTANCE_ID) }
    }

    private fun task(status: String) = UploadTaskEntity(
        id = TASK_ID,
        instanceId = "instance-1",
        targetDir = "/docs",
        localUri = "content://com.android.providers/document/1",
        fileName = "a.txt",
        mimeType = "text/plain",
        totalBytes = 10,
        uploadedBytes = 5,
        status = status,
        errorMessage = "网络错误，请重试",
        workRequestId = null,
        overwrite = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
        const val TASK_ID = "task-1"
    }
}
