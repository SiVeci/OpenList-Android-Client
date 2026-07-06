package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetResp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the v1.0 share-inbound additions to ShareRepositoryImpl
 * (v1.0_EXECUTION_PLAN.md §11 S3-T2 DoD: "同源/跨域/未配置/需密码/密码错/已禁用").
 */
class ShareRepositoryImplTest {

    private val shareDao = mockk<ShareDao>(relaxed = true)
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val api = mockk<OpenListApi>()

    private lateinit var repository: ShareRepositoryImpl

    @Before
    fun setUp() {
        every { clientFactory.apiFor(any()) } returns api
        repository = ShareRepositoryImpl(
            shareDao = shareDao,
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    // --- resolveInboundUrl --------------------------------------------------

    @Test
    fun `resolveInboundUrl matches a configured instance by scheme host and port`() = runTest {
        every { instanceRepository.observeAll() } returns flowOf(
            listOf(instance(id = "i1", baseUrl = "https://nas.example.com:18443")),
        )

        val target = repository.resolveInboundUrl("https://nas.example.com:18443/@s/sid1/a.txt")

        assertEquals("i1", target?.instanceId)
        assertEquals("sid1", target?.sid)
        assertEquals("a.txt", target?.path)
    }

    @Test
    fun `resolveInboundUrl returns null for a cross-instance (unconfigured host) link`() = runTest {
        every { instanceRepository.observeAll() } returns flowOf(
            listOf(instance(id = "i1", baseUrl = "https://nas.example.com:18443")),
        )

        val target = repository.resolveInboundUrl("https://someone-elses-nas.example.org/@s/sid1")

        assertNull(target)
    }

    @Test
    fun `resolveInboundUrl returns null when no instances are configured`() = runTest {
        every { instanceRepository.observeAll() } returns flowOf(emptyList())

        val target = repository.resolveInboundUrl("https://nas.example.com/@s/sid1")

        assertNull(target)
    }

    @Test
    fun `resolveInboundUrl returns null for a non-share URL`() = runTest {
        every { instanceRepository.observeAll() } returns flowOf(
            listOf(instance(id = "i1", baseUrl = "https://nas.example.com")),
        )

        val target = repository.resolveInboundUrl("https://nas.example.com/openlist/local/a.txt")

        assertNull(target)
    }

    @Test
    fun `resolveInboundUrl distinguishes instances by port on the same host`() = runTest {
        every { instanceRepository.observeAll() } returns flowOf(
            listOf(
                instance(id = "http-instance", baseUrl = "http://nas.example.com"),
                instance(id = "https-instance", baseUrl = "https://nas.example.com:18443"),
            ),
        )

        val target = repository.resolveInboundUrl("https://nas.example.com:18443/@s/sid1")

        assertEquals("https-instance", target?.instanceId)
    }

    // --- getInboundShare -----------------------------------------------------

    @Test
    fun `getInboundShare success returns file info for a leaf path`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(id = INSTANCE_ID, baseUrl = "https://example.com")
        coEvery { api.fsGet(any()) } returns ApiResponse(
            code = 200,
            data = FsGetResp(name = "a.txt", size = 100, isDir = false, rawUrl = "https://example.com/p/a.txt?sign=abc"),
        )

        val result = repository.getInboundShare(INSTANCE_ID, "sid1", "a.txt", password = null)

        assertTrue(result is ApiResult.Success)
        val info = (result as ApiResult.Success).data
        assertEquals("a.txt", info.name)
        assertEquals(false, info.isDir)
        assertEquals("https://example.com/p/a.txt?sign=abc", info.rawUrl)
    }

    @Test
    fun `getInboundShare wrong share code maps to SharePasswordRequired (V-607c)`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(id = INSTANCE_ID, baseUrl = "https://example.com")
        coEvery { api.fsGet(any()) } returns ApiResponse(code = 403, message = "wrong share code")

        val result = repository.getInboundShare(INSTANCE_ID, "sid1", null, password = "wrong")

        assertEquals(ApiResult.Failure(DomainError.SharePasswordRequired), result)
    }

    @Test
    fun `getInboundShare expired share preserves the backend message`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(id = INSTANCE_ID, baseUrl = "https://example.com")
        coEvery { api.fsGet(any()) } returns ApiResponse(code = 500, message = "the share has expired or is no longer valid")

        val result = repository.getInboundShare(INSTANCE_ID, "sid1", null, password = null) as ApiResult.Failure

        assertEquals(DomainError.OpenListError(500, "the share has expired or is no longer valid"), result.error)
    }

    @Test
    fun `getInboundShare a real 403 permission error is not misread as SharePasswordRequired`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(id = INSTANCE_ID, baseUrl = "https://example.com")
        coEvery { api.fsGet(any()) } returns ApiResponse(code = 403, message = "permission denied")

        val result = repository.getInboundShare(INSTANCE_ID, "sid1", null, password = null) as ApiResult.Failure

        assertTrue(result.error !is DomainError.SharePasswordRequired)
    }

    @Test
    fun `getInboundShare on an unknown instance returns InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns null

        val result = repository.getInboundShare(INSTANCE_ID, "sid1", null, password = null)

        assertEquals(ApiResult.Failure(DomainError.InvalidInstance), result)
    }

    private fun instance(id: String, baseUrl: String) = Instance(
        id = id,
        name = "Test",
        baseUrl = baseUrl,
        createdAt = 0,
        updatedAt = 0,
        lastUsedAt = 0,
        isCurrent = true,
        note = null,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
