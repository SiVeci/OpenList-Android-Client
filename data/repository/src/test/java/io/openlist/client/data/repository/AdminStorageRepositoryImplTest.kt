package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.model.AdminStoragePage
import io.openlist.client.core.model.AdminStorageStatus
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminStorageDetailsDto
import io.openlist.client.core.network.dto.AdminStorageDto
import io.openlist.client.core.network.dto.AdminStoragePageDto
import io.openlist.client.core.network.dto.ApiResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers AdminStorageRepositoryImpl's read-only S3 scope (v0.5_EXECUTION_PLAN.md
 * §11 S3-T3 DoD): admin_cache 30s TTL behavior and defensive mount_details
 * handling (a missing/null mount_details must never fail the mapping).
 */
class AdminStorageRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val adminCacheDao = mockk<AdminCacheDao>(relaxed = true)
    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val previewRepository = mockk<PreviewRepository>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var repository: AdminStorageRepositoryImpl

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
        repository = AdminStorageRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            adminCacheDao = adminCacheDao,
            fileCacheDao = fileCacheDao,
            previewRepository = previewRepository,
            json = json,
        )
    }

    // ---- mount_details defensive mapping ----

    @Test
    fun `mapping with null mount_details still produces a usable summary`() {
        val dto = AdminStorageDto(
            id = 1,
            mountPath = "/local",
            driver = "Local",
            status = "work",
            disabled = false,
            mountDetails = null,
        )

        val domain = dto.toDomain()

        assertEquals("/local", domain.mountPath)
        assertEquals(AdminStorageStatus.ENABLED, domain.status)
        assertNull(domain.mountDetails)
    }

    @Test
    fun `mapping with a present mount_details decodes disk usage`() {
        val dto = AdminStorageDto(
            id = 2,
            mountPath = "/nas",
            driver = "WebDav",
            status = "work",
            disabled = false,
            mountDetails = AdminStorageDetailsDto(totalSpace = 1000L, usedSpace = 400L),
        )

        val domain = dto.toDomain()

        assertEquals(1000L, domain.mountDetails?.totalSpace)
        assertEquals(400L, domain.mountDetails?.usedSpace)
    }

    @Test
    fun `disabled storage maps to DISABLED status regardless of the raw status string`() {
        val dto = AdminStorageDto(id = 3, mountPath = "/x", driver = "Local", status = "work", disabled = true)

        assertEquals(AdminStorageStatus.DISABLED, dto.toDomain().status)
    }

    @Test
    fun `a non-work status on an enabled storage maps to ERROR`() {
        val dto = AdminStorageDto(id = 4, mountPath = "/x", driver = "Local", status = "driver error: timeout", disabled = false)

        assertEquals(AdminStorageStatus.ERROR, dto.toDomain().status)
    }

    // ---- cache TTL ----

    @Test
    fun `getStorages within TTL returns cached value without calling the network`() = runTest {
        val cachedPage = AdminStoragePage(storages = emptyList(), total = 0)
        coEvery { adminCacheDao.get(INSTANCE_ID, "storages", "list") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "storages",
            cacheKey = "list",
            rawJson = json.encodeToString(cachedPage),
            cachedAt = System.currentTimeMillis() - 1_000L,
        )

        val result = repository.getStorages(INSTANCE_ID, forceRefresh = false) as ApiResult.Success

        assertEquals(cachedPage, result.data)
        coVerify(exactly = 0) { api.adminStorageList(any(), any()) }
    }

    @Test
    fun `getStorages with forceRefresh always calls the network`() = runTest {
        coEvery { adminCacheDao.get(INSTANCE_ID, "storages", "list") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "storages",
            cacheKey = "list",
            rawJson = json.encodeToString(AdminStoragePage(storages = emptyList(), total = 0)),
            cachedAt = System.currentTimeMillis(),
        )
        coEvery { api.adminStorageList(page = 1, perPage = any()) } returns ApiResponse(
            code = 200,
            data = AdminStoragePageDto(content = listOf(AdminStorageDto(id = 1, mountPath = "/fresh", driver = "Local", status = "work")), total = 1),
        )

        val result = repository.getStorages(INSTANCE_ID, forceRefresh = true) as ApiResult.Success

        assertEquals("/fresh", result.data.storages.single().mountPath)
        coVerify(exactly = 1) { api.adminStorageList(1, any()) }
    }

    @Test
    fun `getStorages with an expired cache entry (over 30s) refreshes from the network`() = runTest {
        coEvery { adminCacheDao.get(INSTANCE_ID, "storages", "list") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "storages",
            cacheKey = "list",
            rawJson = json.encodeToString(AdminStoragePage(storages = emptyList(), total = 0)),
            cachedAt = System.currentTimeMillis() - 31_000L, // TTL is 30s
        )
        coEvery { api.adminStorageList(page = 1, perPage = any()) } returns ApiResponse(
            code = 200,
            data = AdminStoragePageDto(content = listOf(AdminStorageDto(id = 2, mountPath = "/refreshed", driver = "Local", status = "work")), total = 1),
        )

        val result = repository.getStorages(INSTANCE_ID, forceRefresh = false) as ApiResult.Success

        assertEquals("/refreshed", result.data.storages.single().mountPath)
        coVerify(exactly = 1) { api.adminStorageList(1, any()) }
    }

    @Test
    fun `getStorages 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { adminCacheDao.get(any(), any(), any()) } returns null
        coEvery { api.adminStorageList(any(), any()) } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getStorages(INSTANCE_ID, forceRefresh = false)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    @Test
    fun `getStorage single lookup maps successfully even with absent mount_details`() = runTest {
        coEvery { api.adminStorageGet(9) } returns ApiResponse(
            code = 200,
            data = AdminStorageDto(id = 9, mountPath = "/one", driver = "Local", status = "work", mountDetails = null),
        )

        val result = repository.getStorage(INSTANCE_ID, 9) as ApiResult.Success

        assertEquals("/one", result.data.mountPath)
        assertNull(result.data.mountDetails)
    }

    // ---- S4-T1/T2: enable/disable ----

    @Test
    fun `enableStorage success invalidates storages cache scope and precisely invalidates the mount path`() = runTest {
        coEvery { api.adminStorageEnable(5) } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminStorageGet(5) } returns ApiResponse(
            code = 200,
            data = AdminStorageDto(id = 5, mountPath = "/nas/", driver = "Local", status = "work", disabled = false),
        )

        val result = repository.enableStorage(INSTANCE_ID, 5)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminCacheDao.deleteByScope(INSTANCE_ID, "storages") }
        // mountPath is normalized (trailing slash stripped) before being used as a prefix.
        coVerify(exactly = 1) { fileCacheDao.deleteByPathPrefix(INSTANCE_ID, "/nas") }
        coVerify(exactly = 1) { previewRepository.invalidateByPrefix(INSTANCE_ID, "/nas") }
    }

    @Test
    fun `disableStorage success invalidates storages cache scope and precisely invalidates the mount path`() = runTest {
        coEvery { api.adminStorageDisable(6) } returns ApiResponse(code = 200, data = null)
        coEvery { api.adminStorageGet(6) } returns ApiResponse(
            code = 200,
            data = AdminStorageDto(id = 6, mountPath = "/webdav", driver = "WebDav", status = "work", disabled = true),
        )

        val result = repository.disableStorage(INSTANCE_ID, 6)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminCacheDao.deleteByScope(INSTANCE_ID, "storages") }
        coVerify(exactly = 1) { fileCacheDao.deleteByPathPrefix(INSTANCE_ID, "/webdav") }
        coVerify(exactly = 1) { previewRepository.invalidateByPrefix(INSTANCE_ID, "/webdav") }
    }

    @Test
    fun `enableStorage failure leaves cache untouched and surfaces the backend message`() = runTest {
        coEvery { api.adminStorageEnable(7) } returns ApiResponse(code = 500, message = "this storage have enabled", data = null)

        val result = repository.enableStorage(INSTANCE_ID, 7)

        assertTrue(result is ApiResult.Failure)
        assertEquals("this storage have enabled", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
        coVerify(exactly = 0) { adminCacheDao.deleteByScope(any(), any()) }
        coVerify(exactly = 0) { fileCacheDao.deleteByPathPrefix(any(), any()) }
        coVerify(exactly = 0) { previewRepository.invalidateByPrefix(any(), any()) }
        coVerify(exactly = 0) { api.adminStorageGet(any()) }
    }

    @Test
    fun `disableStorage failure leaves cache untouched and surfaces the backend message`() = runTest {
        coEvery { api.adminStorageDisable(8) } returns ApiResponse(code = 500, message = "failed get storage driver", data = null)

        val result = repository.disableStorage(INSTANCE_ID, 8)

        assertTrue(result is ApiResult.Failure)
        assertEquals("failed get storage driver", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
        coVerify(exactly = 0) { adminCacheDao.deleteByScope(any(), any()) }
        coVerify(exactly = 0) { fileCacheDao.deleteByPathPrefix(any(), any()) }
        coVerify(exactly = 0) { previewRepository.invalidateByPrefix(any(), any()) }
    }

    @Test
    fun `enableStorage 401 invalidates the session and does not touch caches`() = runTest {
        coEvery { api.adminStorageEnable(any()) } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.enableStorage(INSTANCE_ID, 1)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
        coVerify(exactly = 0) { adminCacheDao.deleteByScope(any(), any()) }
    }

    // ---- S4-T1/T2: reload-all (broad invalidation) ----

    @Test
    fun `reloadAllStorages success invalidates storages cache scope and broadly invalidates the whole instance`() = runTest {
        coEvery { api.adminStorageLoadAll() } returns ApiResponse(code = 200, data = null)

        val result = repository.reloadAllStorages(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminCacheDao.deleteByScope(INSTANCE_ID, "storages") }
        coVerify(exactly = 1) { fileCacheDao.deleteByInstanceId(INSTANCE_ID) }
        coVerify(exactly = 1) { previewRepository.invalidateByPrefix(INSTANCE_ID, "/") }
        // Broad invalidation never touches the precise per-storage path helper.
        coVerify(exactly = 0) { fileCacheDao.deleteByPathPrefix(any(), any()) }
    }

    @Test
    fun `reloadAllStorages failure leaves cache untouched and surfaces the backend message`() = runTest {
        coEvery { api.adminStorageLoadAll() } returns ApiResponse(code = 500, message = "failed get enabled storages", data = null)

        val result = repository.reloadAllStorages(INSTANCE_ID)

        assertTrue(result is ApiResult.Failure)
        assertEquals("failed get enabled storages", ((result as ApiResult.Failure).error as DomainError.OpenListError).message)
        coVerify(exactly = 0) { adminCacheDao.deleteByScope(any(), any()) }
        coVerify(exactly = 0) { fileCacheDao.deleteByInstanceId(any()) }
        coVerify(exactly = 0) { previewRepository.invalidateByPrefix(any(), any()) }
    }

    // ---- S4-T4: driver read-only endpoints ----

    @Test
    fun `getDriverNames returns the plain string list on success`() = runTest {
        coEvery { api.adminDriverNames() } returns ApiResponse(code = 200, data = listOf("Local", "WebDav"))

        val result = repository.getDriverNames(INSTANCE_ID) as ApiResult.Success

        assertEquals(listOf("Local", "WebDav"), result.data)
    }

    @Test
    fun `getDriverInfo 404 (unknown driver) surfaces as a Failure, not a crash`() = runTest {
        coEvery { api.adminDriverInfo("Bogus") } returns ApiResponse(code = 404, message = "not found", data = null)

        val result = repository.getDriverInfo(INSTANCE_ID, "Bogus")

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.NotFound, (result as ApiResult.Failure).error)
    }

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
