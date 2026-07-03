package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.MoveCopyReq
import io.openlist.client.core.network.dto.RemoveReq
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers decision C's client-side batch aggregation (all-succeed / all-fail /
 * partial-fail) plus the early-abort-on-401 path, since the backend itself
 * gives no per-item result and aborts on first error (v0.2_EXECUTION_PLAN.md
 * §12.1/§17). The network/db/session collaborators are mocked; only the
 * aggregation and path-building logic in [FileOperationRepositoryImpl] is
 * under test.
 */
class FileOperationRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: FileOperationRepositoryImpl

    @Before
    fun setUp() {
        val instance = Instance(
            id = INSTANCE_ID,
            name = "Test",
            baseUrl = "https://example.com/",
            createdAt = 0,
            updatedAt = 0,
            lastUsedAt = 0,
            isCurrent = true,
            note = null,
        )
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance
        every { clientFactory.apiFor(any()) } returns api
        repository = FileOperationRepositoryImpl(
            fileCacheDao = fileCacheDao,
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
        )
    }

    @Test
    fun `remove all succeed`() = runTest {
        coEvery { api.fsRemove(any()) } returns success()

        val batch = (repository.remove(INSTANCE_ID, "/docs", listOf("a.txt", "b.txt")) as ApiResult.Success).data

        assertEquals(2, batch.total)
        assertEquals(2, batch.successCount)
        assertEquals(0, batch.failedCount)
        assertEquals(emptyList<Any>(), batch.failedItems)
    }

    @Test
    fun `remove all fail`() = runTest {
        coEvery { api.fsRemove(any()) } returns failure(500, "boom")

        val batch = (repository.remove(INSTANCE_ID, "/docs", listOf("a.txt", "b.txt")) as ApiResult.Success).data

        assertEquals(2, batch.total)
        assertEquals(0, batch.successCount)
        assertEquals(2, batch.failedCount)
    }

    @Test
    fun `remove partial failure reports which item failed and why`() = runTest {
        // Code 400 falls into ErrorMapping's fallback branch, which preserves
        // the raw backend message verbatim (unlike 401/403/404/5xx, which map
        // to a fixed localized string by design — see ErrorMapping.kt).
        coEvery { api.fsRemove(match<RemoveReq> { it.names == listOf("a.txt") }) } returns success()
        coEvery { api.fsRemove(match<RemoveReq> { it.names == listOf("b.txt") }) } returns failure(400, "invalid file name [b.txt]")

        val batch = (repository.remove(INSTANCE_ID, "/docs", listOf("a.txt", "b.txt")) as ApiResult.Success).data

        assertEquals(1, batch.successCount)
        assertEquals(1, batch.failedCount)
        assertEquals("/docs/b.txt", batch.failedItems.single().path)
        assertEquals("invalid file name [b.txt]", batch.failedItems.single().reason)
    }

    @Test
    fun `remove stops calling the backend after 401 and marks the rest failed`() = runTest {
        coEvery { api.fsRemove(any()) } returns failure(401, "unauthorized")

        val batch = (repository.remove(INSTANCE_ID, "/docs", listOf("a.txt", "b.txt", "c.txt")) as ApiResult.Success).data

        assertEquals(3, batch.total)
        assertEquals(0, batch.successCount)
        assertEquals(3, batch.failedCount)
        coVerify(exactly = 1) { api.fsRemove(any()) }
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `move aggregates per-item results and invalidates source and target`() = runTest {
        coEvery { api.fsMove(match<MoveCopyReq> { it.names == listOf("a.txt") }) } returns success()
        coEvery { api.fsMove(match<MoveCopyReq> { it.names == listOf("b.txt") }) } returns failure(403, "no permission")

        val batch = (repository.move(INSTANCE_ID, "/src", "/dst", listOf("a.txt", "b.txt")) as ApiResult.Success).data

        assertEquals(1, batch.successCount)
        assertEquals(1, batch.failedCount)
        coVerify { fileCacheDao.clearDirectory(INSTANCE_ID, "/src") }
        coVerify { fileCacheDao.clearDirectory(INSTANCE_ID, "/dst") }
    }

    @Test
    fun `copy all succeed invalidates only the target directory`() = runTest {
        coEvery { api.fsCopy(any()) } returns success()

        val batch = (repository.copy(INSTANCE_ID, "/src", "/dst", listOf("a.txt")) as ApiResult.Success).data

        assertEquals(1, batch.successCount)
        coVerify { fileCacheDao.clearDirectory(INSTANCE_ID, "/dst") }
        coVerify(exactly = 0) { fileCacheDao.clearDirectory(INSTANCE_ID, "/src") }
    }

    private fun success() = ApiResponse<JsonElement?>(code = 200, message = "success", data = null)

    private fun failure(code: Int, message: String) = ApiResponse<JsonElement?>(code = code, message = message, data = null)

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
