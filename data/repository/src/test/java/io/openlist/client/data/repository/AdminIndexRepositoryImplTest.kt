package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminIndexProgressDto
import io.openlist.client.core.network.dto.AdminIndexUpdateReq
import io.openlist.client.core.network.dto.ApiResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers AdminIndexRepositoryImpl (v0.5_EXECUTION_PLAN.md §11 S6-T1 DoD):
 * defensive decode of malformed/missing progress fields, the `isRunning`
 * client-derivation across running/done/errored scenarios, build/clear
 * "index is running" message passthrough, and 401 handling for all 5 methods.
 */
class AdminIndexRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: AdminIndexRepositoryImpl

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
        repository = AdminIndexRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
        )
    }

    // ---- defensive decode / isRunning derivation ----

    @Test
    fun `a fully-populated running progress maps isRunning to true`() = runTest {
        coEvery { api.adminIndexProgress() } returns ApiResponse(
            code = 200,
            data = AdminIndexProgressDto(objCount = 42, isDone = false, lastDoneTime = null, error = ""),
        )

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertEquals(42L, result.data.objCount)
        assertFalse(result.data.isDone)
        assertNull(result.data.error)
        assertTrue(result.data.isRunning)
    }

    @Test
    fun `a done progress with no error maps isRunning to false`() = runTest {
        coEvery { api.adminIndexProgress() } returns ApiResponse(
            code = 200,
            data = AdminIndexProgressDto(objCount = 100, isDone = true, lastDoneTime = "2024-01-01T00:00:00Z", error = ""),
        )

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertTrue(result.data.isDone)
        assertFalse(result.data.isRunning)
        assertNull(result.data.error)
        assertEquals(100L, result.data.objCount)
    }

    @Test
    fun `a progress with a non-blank error maps isRunning to false even if isDone is false`() = runTest {
        // Defensive scenario: even if the backend somehow reports isDone=false
        // alongside a non-blank error, an error present is treated as "not
        // actively running" -- isRunning requires *both* !isDone and no error.
        coEvery { api.adminIndexProgress() } returns ApiResponse(
            code = 200,
            data = AdminIndexProgressDto(objCount = 5, isDone = false, lastDoneTime = null, error = "walk failed: permission denied"),
        )

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertFalse(result.data.isRunning)
        assertEquals("walk failed: permission denied", result.data.error)
    }

    @Test
    fun `an errored-and-done progress maps isRunning to false and surfaces the error`() = runTest {
        coEvery { api.adminIndexProgress() } returns ApiResponse(
            code = 200,
            data = AdminIndexProgressDto(objCount = 10, isDone = true, lastDoneTime = "2024-01-01T00:00:00Z", error = "build index error: boom"),
        )

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertFalse(result.data.isRunning)
        assertEquals("build index error: boom", result.data.error)
    }

    @Test
    fun `missing lastDoneTime and a malformed timestamp both decode to null without crashing`() = runTest {
        coEvery { api.adminIndexProgress() } returns ApiResponse(
            code = 200,
            data = AdminIndexProgressDto(objCount = 1, isDone = true, lastDoneTime = "not-a-real-timestamp", error = ""),
        )

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertNull(result.data.lastDoneTime)
    }

    @Test
    fun `default-constructed (all-missing-field) DTO decodes to a safe default progress`() = runTest {
        // Simulates a malformed/near-empty backend response: every
        // AdminIndexProgressDto field already carries a decode-time default,
        // so a missing field never throws -- it falls back to that default.
        coEvery { api.adminIndexProgress() } returns ApiResponse(code = 200, data = AdminIndexProgressDto())

        val result = repository.getProgress(INSTANCE_ID) as ApiResult.Success

        assertEquals(0L, result.data.objCount)
        assertFalse(result.data.isDone)
        assertNull(result.data.error)
        assertNull(result.data.lastDoneTime)
        assertTrue(result.data.isRunning)
    }

    // ---- build/clear "index is running" message passthrough ----

    @Test
    fun `buildIndex surfaces the backends index-is-running message verbatim`() = runTest {
        coEvery { api.adminIndexBuild() } returns ApiResponse(code = 400, message = "index is running", data = null)

        val result = repository.buildIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals("index is running", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
    }

    @Test
    fun `clearIndex surfaces the backends index-is-running message verbatim`() = runTest {
        coEvery { api.adminIndexClear() } returns ApiResponse(code = 400, message = "index is running", data = null)

        val result = repository.clearIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals("index is running", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
    }

    @Test
    fun `buildIndex success returns Unit`() = runTest {
        coEvery { api.adminIndexBuild() } returns ApiResponse(code = 200, data = null)

        val result = repository.buildIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `stopIndex success returns Unit`() = runTest {
        coEvery { api.adminIndexStop() } returns ApiResponse(code = 200, data = null)

        val result = repository.stopIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `updateIndex sends the DEC-504 default paths and maxDepth when the caller uses defaults`() = runTest {
        coEvery { api.adminIndexUpdate(any()) } returns ApiResponse(code = 200, data = null)

        val result = repository.updateIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminIndexUpdate(AdminIndexUpdateReq(paths = listOf("/"), maxDepth = -1)) }
    }

    @Test
    fun `updateIndex forwards caller-specified paths and maxDepth`() = runTest {
        coEvery { api.adminIndexUpdate(any()) } returns ApiResponse(code = 200, data = null)

        val result = repository.updateIndex(INSTANCE_ID, paths = listOf("/photos"), maxDepth = 3)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminIndexUpdate(AdminIndexUpdateReq(paths = listOf("/photos"), maxDepth = 3)) }
    }

    @Test
    fun `updateIndex surfaces the backends index-is-running message verbatim`() = runTest {
        coEvery { api.adminIndexUpdate(any()) } returns ApiResponse(code = 400, message = "index is running", data = null)

        val result = repository.updateIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals("index is running", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
    }

    // ---- 401 handling across all 5 methods ----

    @Test
    fun `getProgress 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { api.adminIndexProgress() } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getProgress(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `buildIndex 401 invalidates the session`() = runTest {
        coEvery { api.adminIndexBuild() } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.buildIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `updateIndex 401 invalidates the session`() = runTest {
        coEvery { api.adminIndexUpdate(any()) } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.updateIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `stopIndex 401 invalidates the session`() = runTest {
        coEvery { api.adminIndexStop() } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.stopIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `clearIndex 401 invalidates the session`() = runTest {
        coEvery { api.adminIndexClear() } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.clearIndex(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
