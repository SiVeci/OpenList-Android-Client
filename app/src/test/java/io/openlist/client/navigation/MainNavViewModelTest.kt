package io.openlist.client.navigation

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainNavViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty instances disable instance scoped tabs and show zero active tasks`() = runTest {
        val viewModel = MainNavViewModel(
            instanceRepository = FakeInstanceRepository(emptyList()),
            taskAggregationRepository = FakeTaskAggregationRepository(),
        )
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentInstanceId)
        assertFalse(state.hasInstances)
        assertEquals(0, state.activeTaskCount)

        job.cancel()
    }

    @Test
    fun `summarizes active tasks for current instance only`() = runTest {
        val viewModel = MainNavViewModel(
            instanceRepository = FakeInstanceRepository(
                listOf(instance("inst-1", current = true), instance("inst-2")),
            ),
            taskAggregationRepository = FakeTaskAggregationRepository(
                tasksByInstance = mapOf(
                    "inst-1" to listOf(
                        task("running", UnifiedTaskStatus.RUNNING),
                        task("pending", UnifiedTaskStatus.PENDING),
                        task("done", UnifiedTaskStatus.SUCCESS),
                    ),
                    "inst-2" to listOf(task("other", UnifiedTaskStatus.RUNNING)),
                ),
            ),
        )
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("inst-1", state.currentInstanceId)
        assertTrue(state.hasInstances)
        assertEquals(2, state.activeTaskCount)

        job.cancel()
    }

    @Test
    fun `enables current instance before task stream emits`() = runTest {
        val viewModel = MainNavViewModel(
            instanceRepository = FakeInstanceRepository(listOf(instance("inst-1", current = true))),
            taskAggregationRepository = FakeTaskAggregationRepository(
                tasksByInstance = mapOf("inst-1" to null),
            ),
        )
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("inst-1", state.currentInstanceId)
        assertTrue(state.hasInstances)
        assertEquals(0, state.activeTaskCount)

        job.cancel()
    }

    @Test
    fun `switches active task badge when current instance changes`() = runTest {
        val instanceRepository = FakeInstanceRepository(
            listOf(instance("inst-1", current = true), instance("inst-2")),
        )
        val viewModel = MainNavViewModel(
            instanceRepository = instanceRepository,
            taskAggregationRepository = FakeTaskAggregationRepository(
                tasksByInstance = mapOf(
                    "inst-1" to listOf(task("old", UnifiedTaskStatus.RUNNING)),
                    "inst-2" to listOf(
                        task("new-1", UnifiedTaskStatus.RUNNING),
                        task("new-2", UnifiedTaskStatus.PENDING),
                        task("failed", UnifiedTaskStatus.FAILED),
                    ),
                ),
            ),
        )
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.activeTaskCount)

        instanceRepository.setCurrent("inst-2")
        advanceUntilIdle()

        assertEquals("inst-2", viewModel.uiState.value.currentInstanceId)
        assertEquals(2, viewModel.uiState.value.activeTaskCount)

        job.cancel()
    }

    private class FakeInstanceRepository(initial: List<Instance>) : InstanceRepository {
        private val instances = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<Instance>> = instances
        override suspend fun getById(id: String): Instance? = instances.value.firstOrNull { it.id == id }
        override suspend fun getCurrent(): Instance? = instances.value.firstOrNull { it.isCurrent }
        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> =
            error("not used")

        override suspend fun setCurrent(id: String) {
            instances.value = instances.value.map { it.copy(isCurrent = it.id == id) }
        }

        override suspend fun delete(id: String) = error("not used")
        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> = error("not used")
    }

    private class FakeTaskAggregationRepository(
        private val tasksByInstance: Map<String, List<UnifiedTask>?> = emptyMap(),
    ) : TaskAggregationRepository {
        override fun observeAllTasks(instanceId: String): Flow<List<UnifiedTask>> =
            tasksByInstance[instanceId]?.let { flowOf(it) } ?: emptyFlow()

        override suspend fun refreshRemoteTasks(instanceId: String): ApiResult<Unit> = error("not used")
        override suspend fun refreshDownloadStatuses(instanceId: String) = error("not used")
        override suspend fun cancelTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> =
            error("not used")

        override suspend fun retryTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> =
            error("not used")

        override suspend fun clearFinishedTasks(instanceId: String, source: TaskSource): ApiResult<Unit> =
            error("not used")
    }
}

private fun instance(id: String, current: Boolean = false) = Instance(
    id = id,
    name = id,
    baseUrl = "https://$id.example.com",
    note = null,
    isCurrent = current,
    createdAt = 0L,
    updatedAt = 0L,
    lastUsedAt = 0L,
)

private fun task(id: String, status: UnifiedTaskStatus) = UnifiedTask(
    id = id,
    instanceId = "inst",
    source = TaskSource.LOCAL_UPLOAD,
    type = TaskType.UPLOAD,
    title = id,
    status = status,
    progress = null,
    path = null,
    localUri = null,
    errorMessage = null,
    createdAt = 0L,
    updatedAt = 0L,
)
