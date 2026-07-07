package io.openlist.client.feature.settings

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.LoginResult
import io.openlist.client.core.model.Session
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
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cacheDir: java.io.File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cacheDir = Files.createTempDirectory("settings-cache").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `logoutCurrentSession clears current session and emits login target`() = runTest {
        val instanceRepository = FakeInstanceRepository(listOf(instance()))
        val authRepository = FakeAuthRepository(listOf(session()))
        val viewModel = viewModel(instanceRepository = instanceRepository, authRepository = authRepository)
        val currentJob = launch { viewModel.currentInstanceId.collect {} }
        val logoutJob = launch { viewModel.loggedOutInstanceId.collect {} }

        advanceUntilIdle()
        viewModel.logoutCurrentSession()
        advanceUntilIdle()

        assertEquals(listOf(INSTANCE_ID), authRepository.loggedOutInstanceIds)
        assertEquals(INSTANCE_ID, viewModel.loggedOutInstanceId.value)
        assertNull(authRepository.currentSession(INSTANCE_ID))

        currentJob.cancel()
        logoutJob.cancel()
    }

    @Test
    fun `acknowledgeLoggedOut clears one shot logout event`() = runTest {
        val viewModel = viewModel(
            instanceRepository = FakeInstanceRepository(listOf(instance())),
            authRepository = FakeAuthRepository(listOf(session())),
        )
        val currentJob = launch { viewModel.currentInstanceId.collect {} }
        val logoutJob = launch { viewModel.loggedOutInstanceId.collect {} }

        advanceUntilIdle()
        viewModel.logoutCurrentSession()
        advanceUntilIdle()
        viewModel.acknowledgeLoggedOut()

        assertNull(viewModel.loggedOutInstanceId.value)

        currentJob.cancel()
        logoutJob.cancel()
    }

    private fun viewModel(
        instanceRepository: FakeInstanceRepository,
        authRepository: FakeAuthRepository,
    ): SettingsViewModel {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        val appPreferences = mockk<AppPreferences>()
        every { appPreferences.loggingEnabled } returns MutableStateFlow(false)
        return SettingsViewModel(
            context = context,
            appPreferences = appPreferences,
            filesRepository = FakeFilesRepository(),
            instanceRepository = instanceRepository,
            authRepository = authRepository,
        )
    }

    private class FakeInstanceRepository(initialInstances: List<Instance>) : InstanceRepository {
        private val instances = MutableStateFlow(initialInstances)

        override fun observeAll(): Flow<List<Instance>> = instances

        override suspend fun getById(id: String): Instance? = instances.value.firstOrNull { it.id == id }

        override suspend fun getCurrent(): Instance? = instances.value.firstOrNull { it.isCurrent }

        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun setCurrent(id: String) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeAuthRepository(initialSessions: List<Session>) : AuthRepository {
        private val sessions = MutableStateFlow(initialSessions)
        val loggedOutInstanceIds = mutableListOf<String>()

        fun currentSession(instanceId: String): Session? = sessions.value.firstOrNull { it.instanceId == instanceId }

        override suspend fun getSession(instanceId: String): Session? = currentSession(instanceId)

        override fun observeSession(instanceId: String): Flow<Session?> =
            sessions.map { list -> list.firstOrNull { it.instanceId == instanceId } }

        override fun observeAllSessions(): Flow<List<Session>> = sessions

        override suspend fun loginWithPassword(
            instanceId: String,
            username: String,
            password: String,
            otpCode: String?,
        ): ApiResult<LoginResult> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun loginWithLdap(instanceId: String, username: String, password: String): ApiResult<LoginResult> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun loginAsGuest(instanceId: String): ApiResult<Session> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun loginWithToken(instanceId: String, token: String): ApiResult<Session> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun refreshCurrentUser(instanceId: String): ApiResult<Session> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun logout(instanceId: String) {
            loggedOutInstanceIds += instanceId
            sessions.value = sessions.value.filterNot { it.instanceId == instanceId }
        }
    }

    private class FakeFilesRepository : FilesRepository {
        override fun listDirectory(instanceId: String, path: String, forceRefresh: Boolean): Flow<FileListResult> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun getFile(instanceId: String, path: String): ApiResult<FileDetail> {
            error("Not used in SettingsViewModelTest")
        }

        override suspend fun clearAllCache() = Unit
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"

        fun instance() = Instance(
            id = INSTANCE_ID,
            name = "NAS",
            baseUrl = "https://example.com",
            createdAt = 0L,
            updatedAt = 0L,
            lastUsedAt = 0L,
            isCurrent = true,
            note = null,
        )

        fun session() = Session(
            instanceId = INSTANCE_ID,
            authType = AuthType.PASSWORD,
            username = "alice",
            role = Session.ROLE_GENERAL,
            permission = 0,
            isGuest = false,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}
