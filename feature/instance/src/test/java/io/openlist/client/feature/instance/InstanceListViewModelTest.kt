package io.openlist.client.feature.instance

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.LoginResult
import io.openlist.client.core.model.Session
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceListViewModelTest {

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
    fun `no instance keeps home state empty and does not refresh tasks`() = runTest {
        val instanceRepository = FakeInstanceRepository(emptyList())
        val authRepository = FakeAuthRepository(emptyList())
        val taskRepository = FakeTaskAggregationRepository()
        val viewModel = InstanceListViewModel(instanceRepository, authRepository, taskRepository)
        val job = launch { viewModel.homeUiState.collect {} }

        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals(emptyList<Instance>(), state.instances)
        assertNull(state.currentInstance)
        assertEquals(AdminEntryVisibility.HIDDEN, state.adminEntryVisibility)
        assertEquals(0, state.taskSummary.totalCount)
        assertEquals(emptyList<String>(), taskRepository.downloadRefreshes)
        assertEquals(emptyList<String>(), taskRepository.remoteRefreshes)

        job.cancel()
    }

    @Test
    fun `current admin instance exposes enabled admin entry and summarized tasks`() = runTest {
        val instance = instance("inst-1", isCurrent = true)
        val instanceRepository = FakeInstanceRepository(listOf(instance))
        val authRepository = FakeAuthRepository(listOf(session("inst-1", role = Session.ROLE_ADMIN)))
        val taskRepository = FakeTaskAggregationRepository(
            "inst-1" to listOf(
                task("running", "inst-1", UnifiedTaskStatus.RUNNING),
                task("pending", "inst-1", UnifiedTaskStatus.PENDING),
                task("failed", "inst-1", UnifiedTaskStatus.FAILED),
                task("done", "inst-1", UnifiedTaskStatus.SUCCESS),
            ),
        )
        val viewModel = InstanceListViewModel(instanceRepository, authRepository, taskRepository)
        val job = launch { viewModel.homeUiState.collect {} }

        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals(instance, state.currentInstance)
        assertEquals("user-inst-1", state.currentSession?.username)
        assertEquals(AdminEntryVisibility.ENABLED, state.adminEntryVisibility)
        assertEquals(1, state.taskSummary.runningCount)
        assertEquals(1, state.taskSummary.pendingCount)
        assertEquals(1, state.taskSummary.failedCount)
        assertEquals(1, state.taskSummary.completedCount)
        assertEquals(listOf("inst-1"), taskRepository.downloadRefreshes)
        assertEquals(listOf("inst-1"), taskRepository.remoteRefreshes)

        job.cancel()
    }

    @Test
    fun `guest session requires authentication while keeping task summary scoped to current instance`() = runTest {
        val instance = instance("guest-inst", isCurrent = true)
        val instanceRepository = FakeInstanceRepository(listOf(instance))
        val authRepository = FakeAuthRepository(listOf(session("guest-inst", role = Session.ROLE_GUEST, isGuest = true)))
        val taskRepository = FakeTaskAggregationRepository(
            "guest-inst" to listOf(task("unknown", "guest-inst", UnifiedTaskStatus.UNKNOWN)),
        )
        val viewModel = InstanceListViewModel(instanceRepository, authRepository, taskRepository)
        val job = launch { viewModel.homeUiState.collect {} }

        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals(AdminEntryVisibility.DISABLED_UNAUTHENTICATED, state.adminEntryVisibility)
        assertEquals(1, state.taskSummary.unknownCount)
        assertEquals(1, state.taskSummary.totalCount)

        job.cancel()
    }

    @Test
    fun `switching current instance moves session tasks and one shot refresh to the new instance`() = runTest {
        val instanceRepository = FakeInstanceRepository(
            listOf(
                instance("inst-1", isCurrent = true),
                instance("inst-2", isCurrent = false),
            ),
        )
        val authRepository = FakeAuthRepository(
            listOf(
                session("inst-1", role = Session.ROLE_GENERAL),
                session("inst-2", role = Session.ROLE_ADMIN),
            ),
        )
        val taskRepository = FakeTaskAggregationRepository(
            "inst-1" to listOf(task("failed", "inst-1", UnifiedTaskStatus.FAILED)),
            "inst-2" to listOf(task("pending", "inst-2", UnifiedTaskStatus.PENDING)),
        )
        val viewModel = InstanceListViewModel(instanceRepository, authRepository, taskRepository)
        val job = launch { viewModel.homeUiState.collect {} }

        advanceUntilIdle()
        assertEquals("inst-1", viewModel.homeUiState.value.currentInstance?.id)
        assertEquals(1, viewModel.homeUiState.value.taskSummary.failedCount)
        assertEquals(AdminEntryVisibility.DISABLED_NOT_ADMIN, viewModel.homeUiState.value.adminEntryVisibility)

        viewModel.selectInstance(instance("inst-2", isCurrent = false))
        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals("inst-2", state.currentInstance?.id)
        assertEquals(1, state.taskSummary.pendingCount)
        assertEquals(0, state.taskSummary.failedCount)
        assertEquals(AdminEntryVisibility.ENABLED, state.adminEntryVisibility)
        assertEquals(listOf("inst-1", "inst-2"), taskRepository.downloadRefreshes)
        assertEquals(listOf("inst-1", "inst-2"), taskRepository.remoteRefreshes)

        viewModel.selectInstance(instance("inst-1", isCurrent = false))
        advanceUntilIdle()
        assertEquals(listOf("inst-1", "inst-2"), taskRepository.downloadRefreshes)
        assertEquals(listOf("inst-1", "inst-2"), taskRepository.remoteRefreshes)

        job.cancel()
    }

    private class FakeInstanceRepository(initialInstances: List<Instance>) : InstanceRepository {
        private val instances = MutableStateFlow(initialInstances)

        override fun observeAll(): Flow<List<Instance>> = instances

        override suspend fun getById(id: String): Instance? = instances.value.firstOrNull { it.id == id }

        override suspend fun getCurrent(): Instance? = instances.value.firstOrNull { it.isCurrent }

        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun setCurrent(id: String) {
            instances.value = instances.value.map { it.copy(isCurrent = it.id == id) }
        }

        override suspend fun delete(id: String) {
            instances.value = instances.value.filterNot { it.id == id }
        }

        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeAuthRepository(initialSessions: List<Session>) : AuthRepository {
        private val sessions = MutableStateFlow(initialSessions)

        override suspend fun getSession(instanceId: String): Session? = sessions.value.firstOrNull { it.instanceId == instanceId }

        override fun observeSession(instanceId: String): Flow<Session?> =
            sessions.map { list -> list.firstOrNull { it.instanceId == instanceId } }

        override fun observeAllSessions(): Flow<List<Session>> = sessions

        override suspend fun loginWithPassword(
            instanceId: String,
            username: String,
            password: String,
            otpCode: String?,
        ): ApiResult<LoginResult> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun loginWithLdap(instanceId: String, username: String, password: String): ApiResult<LoginResult> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun loginAsGuest(instanceId: String): ApiResult<Session> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun loginWithToken(instanceId: String, token: String): ApiResult<Session> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun refreshCurrentUser(instanceId: String): ApiResult<Session> {
            error("Not used in InstanceListViewModelTest")
        }
    }

    private class FakeTaskAggregationRepository(
        vararg initialTasks: Pair<String, List<UnifiedTask>>,
    ) : TaskAggregationRepository {
        private val tasksByInstanceId = initialTasks.toMap().mapValues { MutableStateFlow(it.value) }
        val downloadRefreshes = mutableListOf<String>()
        val remoteRefreshes = mutableListOf<String>()

        override fun observeAllTasks(instanceId: String): Flow<List<UnifiedTask>> =
            tasksByInstanceId[instanceId] ?: MutableStateFlow(emptyList())

        override suspend fun refreshRemoteTasks(instanceId: String): ApiResult<Unit> {
            remoteRefreshes += instanceId
            return ApiResult.Success(Unit)
        }

        override suspend fun refreshDownloadStatuses(instanceId: String) {
            downloadRefreshes += instanceId
        }

        override suspend fun cancelTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> {
            error("Not used in InstanceListViewModelTest")
        }

        override suspend fun retryTask(instanceId: String, taskId: String, source: TaskSource): ApiResult<Unit> {
            error("Not used in InstanceListViewModelTest")
        }
    }

    private companion object {
        fun instance(id: String, isCurrent: Boolean) = Instance(
            id = id,
            name = "Instance $id",
            baseUrl = "https://$id.example.com",
            createdAt = 0L,
            updatedAt = 0L,
            lastUsedAt = 0L,
            isCurrent = isCurrent,
            note = null,
        )

        fun session(
            instanceId: String,
            role: Int,
            isGuest: Boolean = false,
        ) = Session(
            instanceId = instanceId,
            authType = if (isGuest) AuthType.GUEST else AuthType.PASSWORD,
            username = if (isGuest) null else "user-$instanceId",
            role = role,
            permission = 0,
            isGuest = isGuest,
            createdAt = 0L,
            updatedAt = 0L,
        )

        fun task(id: String, instanceId: String, status: UnifiedTaskStatus) = UnifiedTask(
            id = id,
            instanceId = instanceId,
            source = TaskSource.REMOTE,
            type = TaskType.OFFLINE_DOWNLOAD,
            title = id,
            status = status,
            progress = null,
            path = "/",
            localUri = null,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}
