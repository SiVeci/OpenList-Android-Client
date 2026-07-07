package io.openlist.client.feature.files

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.RecentPathRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.model.BatchOperationResult
import io.openlist.client.core.model.DirectoryCapability
import io.openlist.client.core.model.FileDetail
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.LoginResult
import io.openlist.client.core.model.RecentPath
import io.openlist.client.core.model.Session
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareInboundInfo
import io.openlist.client.core.model.ShareInboundTarget
import io.openlist.client.core.model.ShareWriteRequest
import io.openlist.client.core.model.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileListViewModelTest {

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
    fun `initial path and directory navigation are recorded as recent paths`() = runTest {
        val recentPathRepository = FakeRecentPathRepository()
        val filesRepository = FakeFilesRepository()
        val viewModel = viewModel(
            savedPath = "%2Fdocs%2F",
            filesRepository = filesRepository,
            recentPathRepository = recentPathRepository,
        )

        advanceUntilIdle()
        viewModel.navigateTo("/media/photos/")
        advanceUntilIdle()

        assertEquals(listOf("/docs", "/media/photos"), recentPathRepository.recordedPaths)
        assertEquals(listOf("/docs", "/media/photos"), filesRepository.requestedPaths)
    }

    @Test
    fun `recent path recording failure does not block directory listing`() = runTest {
        val recentPathRepository = FakeRecentPathRepository(shouldFail = true)
        val filesRepository = FakeFilesRepository()
        val viewModel = viewModel(
            filesRepository = filesRepository,
            recentPathRepository = recentPathRepository,
        )

        advanceUntilIdle()

        assertEquals(listOf("/"), recentPathRepository.recordedPaths)
        assertEquals(listOf("/"), filesRepository.requestedPaths)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    private fun viewModel(
        savedPath: String? = null,
        filesRepository: FakeFilesRepository = FakeFilesRepository(),
        recentPathRepository: FakeRecentPathRepository = FakeRecentPathRepository(),
    ): FileListViewModel {
        val handle = SavedStateHandle(
            buildMap {
                put("instanceId", INSTANCE_ID)
                savedPath?.let { put("path", it) }
            },
        )
        return FileListViewModel(
            savedStateHandle = handle,
            filesRepository = filesRepository,
            instanceRepository = FakeInstanceRepository(),
            authRepository = FakeAuthRepository(),
            fileOperationRepository = FakeFileOperationRepository(),
            directoryPickerRepository = FakeDirectoryPickerRepository(),
            uploadRepository = FakeUploadRepository(),
            shareRepository = FakeShareRepository(),
            recentPathRepository = recentPathRepository,
        )
    }

    private class FakeRecentPathRepository(
        private val shouldFail: Boolean = false,
    ) : RecentPathRepository {
        val recordedPaths = mutableListOf<String>()

        override fun observeByInstance(instanceId: String): Flow<List<RecentPath>> = emptyFlow()

        override fun observeAll(): Flow<List<RecentPath>> = emptyFlow()

        override suspend fun recordPath(instanceId: String, path: String, displayName: String?) {
            recordedPaths += path
            if (shouldFail) error("recent path write failed")
        }

        override suspend fun deleteByInstanceId(instanceId: String) = Unit
    }

    private class FakeFilesRepository : FilesRepository {
        val requestedPaths = mutableListOf<String>()

        override fun listDirectory(instanceId: String, path: String, forceRefresh: Boolean): Flow<FileListResult> {
            requestedPaths += path
            return flowOf(FileListResult.Fresh(nodes = emptyList(), capability = DirectoryCapability.UNKNOWN))
        }

        override suspend fun getFile(instanceId: String, path: String): ApiResult<FileDetail> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun clearAllCache() = Unit
    }

    private class FakeInstanceRepository : InstanceRepository {
        override fun observeAll(): Flow<List<Instance>> = emptyFlow()

        override suspend fun getById(id: String): Instance = Instance(
            id = id,
            name = "Test",
            baseUrl = "https://example.com",
            createdAt = 0L,
            updatedAt = 0L,
            lastUsedAt = 0L,
            isCurrent = true,
            note = null,
        )

        override suspend fun getCurrent(): Instance? = null

        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun setCurrent(id: String) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun getSession(instanceId: String): Session? = null

        override fun observeSession(instanceId: String): Flow<Session?> = flowOf(null)

        override fun observeAllSessions(): Flow<List<Session>> = emptyFlow()

        override suspend fun loginWithPassword(
            instanceId: String,
            username: String,
            password: String,
            otpCode: String?,
        ): ApiResult<LoginResult> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun loginWithLdap(instanceId: String, username: String, password: String): ApiResult<LoginResult> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun loginAsGuest(instanceId: String): ApiResult<Session> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun loginWithToken(instanceId: String, token: String): ApiResult<Session> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun refreshCurrentUser(instanceId: String): ApiResult<Session> {
            error("Not used in FileListViewModelTest")
        }
    }

    private class FakeFileOperationRepository : FileOperationRepository {
        override suspend fun mkdir(instanceId: String, parentPath: String, name: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun rename(instanceId: String, path: String, newName: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun remove(instanceId: String, dir: String, names: List<String>): ApiResult<BatchOperationResult> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun move(
            instanceId: String,
            srcDir: String,
            targetDir: String,
            names: List<String>,
        ): ApiResult<BatchOperationResult> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun copy(
            instanceId: String,
            srcDir: String,
            targetDir: String,
            names: List<String>,
        ): ApiResult<BatchOperationResult> {
            error("Not used in FileListViewModelTest")
        }
    }

    private class FakeDirectoryPickerRepository : DirectoryPickerRepository {
        override suspend fun listDirectories(instanceId: String, path: String): ApiResult<List<FileNode>> {
            error("Not used in FileListViewModelTest")
        }
    }

    private class FakeUploadRepository : UploadRepository {
        override suspend fun enqueueUpload(instanceId: String, targetDir: String, localUris: List<Uri>): ApiResult<List<String>> {
            error("Not used in FileListViewModelTest")
        }

        override fun observeUploadTasks(instanceId: String): Flow<List<UploadTask>> = flowOf(emptyList())

        override suspend fun cancelUpload(taskId: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun retryUpload(taskId: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }
    }

    private class FakeShareRepository : ShareRepository {
        override fun observeShares(instanceId: String): Flow<List<Share>> = emptyFlow()

        override suspend fun listShares(instanceId: String): ApiResult<List<Share>> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun getShare(instanceId: String, id: String): ApiResult<Share> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun createShare(instanceId: String, request: ShareWriteRequest): ApiResult<Share> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun updateShare(instanceId: String, id: String, request: ShareWriteRequest): ApiResult<Share> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun enableShare(instanceId: String, id: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun disableShare(instanceId: String, id: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun deleteShare(instanceId: String, id: String): ApiResult<Unit> {
            error("Not used in FileListViewModelTest")
        }

        override fun buildShareUrl(instanceBaseUrl: String, id: String): String = "$instanceBaseUrl/@s/$id"

        override suspend fun resolveInboundUrl(url: String): ShareInboundTarget? {
            error("Not used in FileListViewModelTest")
        }

        override suspend fun getInboundShare(
            instanceId: String,
            sid: String,
            path: String?,
            password: String?,
        ): ApiResult<ShareInboundInfo> {
            error("Not used in FileListViewModelTest")
        }
    }

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
