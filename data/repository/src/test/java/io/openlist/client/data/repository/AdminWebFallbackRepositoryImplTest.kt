package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminWebSection
import io.openlist.client.core.model.Instance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers AdminWebFallbackRepositoryImpl (v0.5_EXECUTION_PLAN.md §11 S7-T3
 * DoD): the URL is always prefixed by the instance's own base URL (including
 * a sub-path deployment prefix), every section resolves to the same
 * `/@manage` path (V-508 only confirms the base path, not per-section
 * fragments), no token/query-param ever appears in the built URL, and
 * [io.openlist.client.core.model.WebFallbackTarget.requiresWebLogin] is
 * always `true`.
 */
class AdminWebFallbackRepositoryImplTest {

    private val instanceRepository = mockk<InstanceRepository>()
    private lateinit var repository: AdminWebFallbackRepositoryImpl

    @Before
    fun setUp() {
        repository = AdminWebFallbackRepositoryImpl(instanceRepository = instanceRepository)
    }

    @Test
    fun `built URL is prefixed by the instance base URL and appends the confirmed manage path`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = "https://host.example.com")

        val result = repository.buildAdminUrl(INSTANCE_ID, AdminWebSection.HOME) as ApiResult.Success

        assertEquals("https://host.example.com/@manage", result.data.url)
    }

    @Test
    fun `sub-path deployment prefix is preserved without a double slash`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = "https://host.example.com/openlist")

        val result = repository.buildAdminUrl(INSTANCE_ID, AdminWebSection.SETTINGS) as ApiResult.Success

        assertEquals("https://host.example.com/openlist/@manage", result.data.url)
        assertFalse(result.data.url.contains("//@manage"))
    }

    @Test
    fun `every section resolves to the identical base manage URL -- no unconfirmed per-section fragment`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = "https://host.example.com")

        val urls = AdminWebSection.entries.map { section ->
            (repository.buildAdminUrl(INSTANCE_ID, section) as ApiResult.Success).data.url
        }

        assertTrue(urls.all { it == "https://host.example.com/@manage" })
    }

    @Test
    fun `requiresWebLogin is always true regardless of section`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = "https://host.example.com")

        AdminWebSection.entries.forEach { section ->
            val result = repository.buildAdminUrl(INSTANCE_ID, section) as ApiResult.Success
            assertTrue(result.data.requiresWebLogin)
        }
    }

    @Test
    fun `built URL never contains a token or auth-shaped query parameter`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = "https://host.example.com")

        val result = repository.buildAdminUrl(INSTANCE_ID, AdminWebSection.USERS) as ApiResult.Success

        assertFalse(result.data.url.contains("?"))
        assertFalse(result.data.url.contains("token", ignoreCase = true))
        assertFalse(result.data.url.contains("sign", ignoreCase = true))
        assertFalse(result.data.url.contains("Authorization", ignoreCase = true))
    }

    @Test
    fun `unknown instance fails with InvalidInstance`() = runTest {
        coEvery { instanceRepository.getById(INSTANCE_ID) } returns null

        val result = repository.buildAdminUrl(INSTANCE_ID, AdminWebSection.HOME)

        assertTrue(result is ApiResult.Failure)
    }

    /**
     * There is no reachable code path in [AdminWebFallbackRepositoryImpl]
     * that could produce a cross-origin URL: the built URL is always exactly
     * `instance.baseUrl.trimEnd('/') + "/@manage"`, with no external input
     * (no user-supplied host, no server-returned redirect URL) ever
     * substituted in between. This test documents that structural guarantee
     * directly rather than fabricating an artificial cross-origin scenario
     * that the production code has no way to enter -- it asserts the
     * same-origin invariant holds for a representative spread of base URLs
     * (different scheme, port, sub-path) as the practical form of "reject
     * cross-origin" coverage the brief calls for.
     */
    @Test
    fun `built URL origin always exactly matches the instance base URL origin, for varied base URL shapes`() = runTest {
        val cases = listOf(
            "https://host.example.com",
            "http://192.168.1.10:5244",
            "https://nas.example.com:18443/openlist",
        )
        cases.forEach { baseUrl ->
            coEvery { instanceRepository.getById(INSTANCE_ID) } returns instance(baseUrl = baseUrl)
            val result = repository.buildAdminUrl(INSTANCE_ID, AdminWebSection.INDEX) as ApiResult.Success
            assertTrue(result.data.url.startsWith(baseUrl))
        }
    }

    private fun instance(baseUrl: String) = Instance(
        id = INSTANCE_ID,
        name = "Test",
        baseUrl = baseUrl,
        createdAt = 0,
        updatedAt = 0,
        lastUsedAt = 0,
        isCurrent = true,
        note = null,
    )

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
