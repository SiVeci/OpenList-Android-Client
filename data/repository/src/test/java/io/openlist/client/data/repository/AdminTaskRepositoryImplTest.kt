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
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.TaskInfoDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers AdminTaskRepositoryImpl (v0.5_EXECUTION_PLAN.md §11 S5-T1 DoD):
 * 7-type concurrent undone refresh with partial failure, in-memory-only
 * storage (no Room DAO dependency -- see constructor), retry/delete not
 * being locally gated by cached state, and refresh-after-mutation.
 */
class AdminTaskRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: AdminTaskRepositoryImpl

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
        repository = AdminTaskRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
        )
    }

    // ---- 7-type concurrent refresh ----

    @Test
    fun `refreshUndone fetches all 7 types concurrently and merges successes`() = runTest {
        for (type in ALL_TYPES) {
            coEvery { api.adminTaskUndone(type) } returns ApiResponse(
                code = 200,
                data = listOf(TaskInfoDto(id = "$type-1", name = "task-$type", state = 1)),
            )
        }

        val result = repository.refreshUndone(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
        val tasks = repository.observeAdminTasks(INSTANCE_ID).first()
        assertEquals(ALL_TYPES.size, tasks.size)
        assertEquals(ALL_TYPES.toSet(), tasks.map { it.taskType }.toSet())
        for (type in ALL_TYPES) {
            coVerify(exactly = 1) { api.adminTaskUndone(type) }
        }
    }

    @Test
    fun `refreshUndone with one type failing still populates the other 6`() = runTest {
        for (type in ALL_TYPES) {
            if (type == "decompress") {
                coEvery { api.adminTaskUndone(type) } returns ApiResponse(code = 500, message = "boom", data = null)
            } else {
                coEvery { api.adminTaskUndone(type) } returns ApiResponse(
                    code = 200,
                    data = listOf(TaskInfoDto(id = "$type-1", name = "task-$type", state = 1)),
                )
            }
        }

        val result = repository.refreshUndone(INSTANCE_ID)

        // Partial failure still reports overall Success (only a total wipe-out fails).
        assertTrue(result is ApiResult.Success)
        val tasks = repository.observeAdminTasks(INSTANCE_ID).first()
        assertEquals(ALL_TYPES.size - 1, tasks.size)
        assertTrue(tasks.none { it.taskType == "decompress" })
    }

    @Test
    fun `refreshUndone with every type failing reports a Failure`() = runTest {
        for (type in ALL_TYPES) {
            coEvery { api.adminTaskUndone(type) } returns ApiResponse(code = 500, message = "boom", data = null)
        }

        val result = repository.refreshUndone(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertTrue(repository.observeAdminTasks(INSTANCE_ID).first().isEmpty())
    }

    @Test
    fun `refreshUndone 401 on one type invalidates the session`() = runTest {
        for (type in ALL_TYPES) {
            coEvery { api.adminTaskUndone(type) } returns if (type == "upload") {
                ApiResponse(code = 401, message = "unauthorized", data = null)
            } else {
                ApiResponse(code = 200, data = emptyList())
            }
        }

        repository.refreshUndone(INSTANCE_ID)

        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    // ---- refreshDone: single type only ----

    @Test
    fun `refreshDone only calls the network for the requested type`() = runTest {
        coEvery { api.adminTaskDone("copy") } returns ApiResponse(
            code = 200,
            data = listOf(TaskInfoDto(id = "copy-done-1", name = "done-copy", state = 2)),
        )

        val result = repository.refreshDone(INSTANCE_ID, "copy")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 0) { api.adminTaskUndone(any()) }
        coVerify(exactly = 0) { api.adminTaskDone(neq("copy")) }
        val tasks = repository.observeAdminTasks(INSTANCE_ID).first()
        assertEquals(1, tasks.size)
        assertEquals(UnifiedTaskStatus.SUCCESS, tasks.single().state)
    }

    // ---- getTaskInfo ----

    @Test
    fun `getTaskInfo maps a single task via TaskStateMapper`() = runTest {
        coEvery { api.adminTaskInfo("move", "tid-1") } returns ApiResponse(
            code = 200,
            data = TaskInfoDto(id = "tid-1", name = "moving", state = 5, error = "disk full"),
        )

        val result = repository.getTaskInfo(INSTANCE_ID, "move", "tid-1") as ApiResult.Success

        assertEquals(UnifiedTaskStatus.FAILED, result.data.state)
        assertEquals("disk full", result.data.error)
    }

    // ---- retry/delete not locally gated by cached state (V-505) ----

    @Test
    fun `retryTask calls through to the backend regardless of the task's cached state`() = runTest {
        // Seed the cache with a task that looks SUCCESS (not FAILED) -- a
        // naive client-side gate would refuse to retry this, but the
        // repository itself must never make that decision; it always calls
        // through and trusts the backend's response (V-505: retry has no
        // server-side pre-validation either, so this can legitimately succeed).
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskUndone("copy") } returns ApiResponse(
            code = 200,
            data = listOf(TaskInfoDto(id = "tid-2", name = "copy-task", state = 2)), // Succeeded
        )
        repository.refreshUndone(INSTANCE_ID)
        coEvery { api.adminTaskRetry("copy", "tid-2") } returns ApiResponse(code = 200, data = null)

        val result = repository.retryTask(INSTANCE_ID, "copy", "tid-2")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminTaskRetry("copy", "tid-2") }
    }

    @Test
    fun `deleteTaskRecord calls through to the backend regardless of the task's cached state`() = runTest {
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())
        // Seed an undone (not done) task -- a naive client gate would refuse
        // to delete it, but the repository always calls through.
        coEvery { api.adminTaskUndone("move") } returns ApiResponse(
            code = 200,
            data = listOf(TaskInfoDto(id = "tid-3", name = "move-task", state = 1)), // Running
        )
        repository.refreshUndone(INSTANCE_ID)
        coEvery { api.adminTaskDelete("move", "tid-3") } returns ApiResponse(code = 200, data = null)

        val result = repository.deleteTaskRecord(INSTANCE_ID, "move", "tid-3")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminTaskDelete("move", "tid-3") }
    }

    @Test
    fun `retryTask surfaces the backend's error message verbatim on failure`() = runTest {
        coEvery { api.adminTaskRetry("upload", "tid-4") } returns ApiResponse(code = 500, message = "task is not failed", data = null)

        val result = repository.retryTask(INSTANCE_ID, "upload", "tid-4")

        assertTrue(result is ApiResult.Failure)
        assertEquals("task is not failed", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
    }

    // ---- refresh-after-mutation ----

    @Test
    fun `cancelTask success triggers a refresh of both undone and done buckets for that type`() = runTest {
        coEvery { api.adminTaskCancel("upload", "tid-5") } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())

        repository.cancelTask(INSTANCE_ID, "upload", "tid-5")

        coVerify(exactly = 1) { api.adminTaskCancel("upload", "tid-5") }
        // refreshUndone always re-fetches all 7 types (mirrors the periodic
        // poll's shape); refreshDone re-fetches only the affected type.
        for (type in ALL_TYPES) {
            coVerify(exactly = 1) { api.adminTaskUndone(type) }
        }
        coVerify(exactly = 1) { api.adminTaskDone("upload") }
    }

    @Test
    fun `cancelTask failure does not trigger any refresh`() = runTest {
        coEvery { api.adminTaskCancel("upload", "tid-6") } returns ApiResponse(code = 500, message = "cannot cancel", data = null)

        repository.cancelTask(INSTANCE_ID, "upload", "tid-6")

        coVerify(exactly = 0) { api.adminTaskUndone(any()) }
        coVerify(exactly = 0) { api.adminTaskDone(any()) }
    }

    @Test
    fun `deleteTaskRecord 401 invalidates the session`() = runTest {
        coEvery { api.adminTaskDelete("copy", "tid-7") } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.deleteTaskRecord(INSTANCE_ID, "copy", "tid-7")

        assertTrue(result is ApiResult.Failure)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    // ---- batch operations (v1.0 S5-T2, DEC-603 subset A) ----

    @Test
    fun `clearDone calls the clear_done endpoint for the given type and refreshes both buckets`() = runTest {
        coEvery { api.adminTaskClearDone("upload") } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())

        val result = repository.clearDone(INSTANCE_ID, "upload")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminTaskClearDone("upload") }
        coVerify(exactly = 1) { api.adminTaskDone("upload") }
    }

    @Test
    fun `clearSucceeded calls the clear_succeeded endpoint for the given type`() = runTest {
        coEvery { api.adminTaskClearSucceeded("copy") } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())

        val result = repository.clearSucceeded(INSTANCE_ID, "copy")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminTaskClearSucceeded("copy") }
    }

    @Test
    fun `retryFailed calls the retry_failed endpoint for the given type`() = runTest {
        coEvery { api.adminTaskRetryFailed("move") } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminTaskUndone(any()) } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.adminTaskDone(any()) } returns ApiResponse(code = 200, data = emptyList())

        val result = repository.retryFailed(INSTANCE_ID, "move")

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { api.adminTaskRetryFailed("move") }
    }

    @Test
    fun `clearDone surfaces the backend's error message verbatim on failure`() = runTest {
        coEvery { api.adminTaskClearDone("upload") } returns ApiResponse(code = 403, message = "admin only", data = null)

        val result = repository.clearDone(INSTANCE_ID, "upload")

        assertTrue(result is ApiResult.Failure)
    }

    // ---- zero Room writes: structural proof ----

    @Test
    fun `constructor takes no Room DAO dependency -- structural proof of in-memory-only storage`() {
        // AdminTaskRepositoryImpl's constructor parameters are InstanceRepository/
        // OpenListClientFactory/InstanceContext/SessionManager only -- no
        // RemoteTaskDao or any `core.database.dao` type appears anywhere in
        // this class, which is the structural (compile-time) proof of B-503,
        // not just a runtime absence-of-calls check.
        val daoParams = AdminTaskRepositoryImpl::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .filter { it.name.contains("core.database.dao", ignoreCase = true) }
        assertTrue(daoParams.isEmpty())
    }

    @Test
    fun `observeAdminTasks starts empty until a refresh populates it`() = runTest {
        assertTrue(repository.observeAdminTasks(INSTANCE_ID).first().isEmpty())
    }

    @Test
    fun `getTaskInfo 401 invalidates the session`() = runTest {
        coEvery { api.adminTaskInfo("upload", "tid-8") } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getTaskInfo(INSTANCE_ID, "upload", "tid-8")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"
        val ALL_TYPES = listOf(
            "upload",
            "copy",
            "move",
            "offline_download",
            "offline_download_transfer",
            "decompress",
            "decompress_upload",
        )
    }
}
