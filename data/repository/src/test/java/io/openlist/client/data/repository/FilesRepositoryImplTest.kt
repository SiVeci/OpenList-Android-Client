package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.Session
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsListResp
import io.openlist.client.core.network.dto.ObjResp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers the v1.0 [io.openlist.client.core.model.DirectoryCapability]
 * resolution added to FilesRepositoryImpl (v1.0_EXECUTION_PLAN.md §11 S1-T5
 * DoD: "write=true/false/缓存 unknown 三态"), plus V-604's key finding that
 * `write` alone is not sufficient — the session's CanWriteContent bit
 * (Session.PERM_WRITE) must also be set.
 */
class FilesRepositoryImplTest {

    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val api = mockk<OpenListApi>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()

    private lateinit var repository: FilesRepositoryImpl

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
        coEvery { fileCacheDao.getByParent(any(), any()) } returns emptyList()
        coEvery { fileCacheDao.getCachedAt(any(), any()) } returns null
        repository = FilesRepositoryImpl(
            fileCacheDao = fileCacheDao,
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            authRepository = authRepository,
        )
    }

    @Test
    fun `write true and session has CanWriteContent bit yields canWrite true`() = runTest {
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(permission = 1 shl Session.PERM_WRITE)
        coEvery { api.fsList(any()) } returns ApiResponse(code = 200, data = FsListResp(content = emptyList(), write = true))

        val results = repository.listDirectory(INSTANCE_ID, "/docs").toList()

        val fresh = results.filterIsInstance<FileListResult.Fresh>().single()
        assertEquals(true, fresh.capability.canWrite)
    }

    @Test
    fun `write true but session missing CanWriteContent bit yields canWrite false (V-604)`() = runTest {
        // meta ACL allows write, but the user's own upload permission bit is off —
        // server/handles/fsread.go requires both; `write` alone overstates it.
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(permission = 0)
        coEvery { api.fsList(any()) } returns ApiResponse(code = 200, data = FsListResp(content = emptyList(), write = true))

        val results = repository.listDirectory(INSTANCE_ID, "/docs").toList()

        val fresh = results.filterIsInstance<FileListResult.Fresh>().single()
        assertEquals(false, fresh.capability.canWrite)
    }

    @Test
    fun `write false yields canWrite false regardless of session bit`() = runTest {
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(permission = 1 shl Session.PERM_WRITE)
        coEvery { api.fsList(any()) } returns ApiResponse(code = 200, data = FsListResp(content = emptyList(), write = false))

        val results = repository.listDirectory(INSTANCE_ID, "/docs").toList()

        val fresh = results.filterIsInstance<FileListResult.Fresh>().single()
        assertEquals(false, fresh.capability.canWrite)
    }

    @Test
    fun `no session on record yields canWrite false rather than unknown`() = runTest {
        coEvery { authRepository.getSession(INSTANCE_ID) } returns null
        coEvery { api.fsList(any()) } returns ApiResponse(code = 200, data = FsListResp(content = emptyList(), write = true))

        val results = repository.listDirectory(INSTANCE_ID, "/docs").toList()

        val fresh = results.filterIsInstance<FileListResult.Fresh>().single()
        assertEquals(false, fresh.capability.canWrite)
    }

    @Test
    fun `a cache-only emission (before network lands) reports capability unknown`() = runTest {
        coEvery { fileCacheDao.getByParent(INSTANCE_ID, "/docs") } returns listOf(
            io.openlist.client.core.database.entity.FileCacheEntity(
                instanceId = INSTANCE_ID,
                path = "/docs/a.txt",
                parentPath = "/docs",
                name = "a.txt",
                isDir = false,
                size = 1,
                modifiedAt = null,
                sign = "",
                thumb = "",
                type = 0,
                cachedAt = System.currentTimeMillis(),
            ),
        )
        coEvery { fileCacheDao.getCachedAt(INSTANCE_ID, "/docs") } returns System.currentTimeMillis() - 60_000
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(permission = 1 shl Session.PERM_WRITE)
        coEvery { api.fsList(any()) } returns ApiResponse(code = 200, data = FsListResp(content = emptyList(), write = true))

        val results = repository.listDirectory(INSTANCE_ID, "/docs").toList()

        val cached = results.filterIsInstance<FileListResult.Cached>().single()
        assertNull(cached.capability.canWrite)
    }

    private fun session(permission: Int) = Session(
        instanceId = INSTANCE_ID,
        authType = AuthType.PASSWORD,
        username = "alice",
        role = Session.ROLE_GENERAL,
        permission = permission,
        isGuest = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
