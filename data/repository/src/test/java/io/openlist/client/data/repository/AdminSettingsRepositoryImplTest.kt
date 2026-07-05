package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.entity.AdminCacheEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminSettingDto
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.model.AdminSettingItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers AdminSettingsRepositoryImpl (v0.5_EXECUTION_PLAN.md §11 S7-T1 DoD):
 * the private-value-blanked-before-cache guarantee (P-507), key-keyword-based
 * privacy detection as defense-in-depth even when `flag == 0` (P-508), the
 * 5-minute admin_cache TTL, forceRefresh bypass, and group filter passthrough.
 * Mirrors the mocked-collaborator pattern established by
 * AdminUserRepositoryImplTest/AdminIndexRepositoryImplTest.
 */
class AdminSettingsRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val adminCacheDao = mockk<AdminCacheDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var repository: AdminSettingsRepositoryImpl

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
        repository = AdminSettingsRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            adminCacheDao = adminCacheDao,
            json = json,
        )
    }

    // ---- private-value handling ----

    @Test
    fun `flag PRIVATE blanks value before it is ever cached`() = runTest {
        coEvery { adminCacheDao.get(any(), any(), any()) } returns null
        coEvery { api.adminSettingList(group = null, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(
                AdminSettingDto(key = "aria2_token", value = "super-secret-token-value", flag = 1, group = 5),
            ),
        )

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = false) as ApiResult.Success

        val item = result.data.single()
        assertTrue(item.isPrivate)
        assertNull(item.value)

        // The cached JSON string must not contain the secret substring anywhere.
        coVerify(exactly = 1) { adminCacheDao.upsert(match { !it.rawJson.contains("super-secret-token-value") }) }
        coVerify(exactly = 0) { adminCacheDao.upsert(match { it.rawJson.contains("super-secret-token-value") }) }
    }

    @Test
    fun `key keyword detects privacy even when flag is PUBLIC (defense in depth)`() = runTest {
        coEvery { adminCacheDao.get(any(), any(), any()) } returns null
        coEvery { api.adminSettingList(group = null, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(
                AdminSettingDto(key = "some_api_secret", value = "leaked-if-bug", flag = 0),
                AdminSettingDto(key = "webauthn_password_hint", value = "also-leaked-if-bug", flag = 0),
                AdminSettingDto(key = "site_title", value = "My Site", flag = 0),
            ),
        )

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = false) as ApiResult.Success

        val bySecretKey = result.data.single { it.key == "some_api_secret" }
        assertTrue(bySecretKey.isPrivate)
        assertNull(bySecretKey.value)

        val byPasswordKey = result.data.single { it.key == "webauthn_password_hint" }
        assertTrue(byPasswordKey.isPrivate)
        assertNull(byPasswordKey.value)

        val publicItem = result.data.single { it.key == "site_title" }
        assertFalse(publicItem.isPrivate)
        assertEquals("My Site", publicItem.value)

        coVerify(exactly = 0) { adminCacheDao.upsert(match { it.rawJson.contains("leaked-if-bug") }) }
        coVerify(exactly = 0) { adminCacheDao.upsert(match { it.rawJson.contains("also-leaked-if-bug") }) }
    }

    @Test
    fun `getDefaultSettings applies the same privacy blanking rules`() = runTest {
        coEvery { api.adminSettingDefault(group = null, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(AdminSettingDto(key = "ldap_login_secret", value = "default-secret", flag = 1)),
        )

        val result = repository.getDefaultSettings(INSTANCE_ID, group = null) as ApiResult.Success

        val item = result.data.single()
        assertTrue(item.isPrivate)
        assertNull(item.value)
    }

    // ---- cache TTL ----

    @Test
    fun `getSettings within TTL returns cached value without calling the network`() = runTest {
        val cachedRawJson = json.encodeToString(listOf(sampleItem()))
        coEvery { adminCacheDao.get(INSTANCE_ID, "settings", "all") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "settings",
            cacheKey = "all",
            rawJson = cachedRawJson,
            cachedAt = System.currentTimeMillis() - 1_000L,
        )

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = false) as ApiResult.Success

        assertEquals("site_title", result.data.single().key)
        coVerify(exactly = 0) { api.adminSettingList(any(), any()) }
    }

    @Test
    fun `getSettings with forceRefresh always calls the network even when a fresh cache entry exists`() = runTest {
        val cachedRawJson = json.encodeToString(listOf(sampleItem()))
        coEvery { adminCacheDao.get(INSTANCE_ID, "settings", "all") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "settings",
            cacheKey = "all",
            rawJson = cachedRawJson,
            cachedAt = System.currentTimeMillis(),
        )
        coEvery { api.adminSettingList(group = null, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(AdminSettingDto(key = "fresh_key", value = "fresh-value", flag = 0)),
        )

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = true) as ApiResult.Success

        assertEquals("fresh_key", result.data.single().key)
        coVerify(exactly = 1) { api.adminSettingList(null, null) }
    }

    @Test
    fun `getSettings with an expired cache entry refreshes from the network`() = runTest {
        val cachedRawJson = json.encodeToString(listOf(sampleItem()))
        coEvery { adminCacheDao.get(INSTANCE_ID, "settings", "all") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "settings",
            cacheKey = "all",
            rawJson = cachedRawJson,
            cachedAt = System.currentTimeMillis() - (6 * 60 * 1000L), // 6 min ago, TTL is 5 min
        )
        coEvery { api.adminSettingList(group = null, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(AdminSettingDto(key = "refreshed_key", value = "refreshed-value", flag = 0)),
        )

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = false) as ApiResult.Success

        assertEquals("refreshed_key", result.data.single().key)
        coVerify(exactly = 1) { api.adminSettingList(null, null) }
    }

    // ---- group filter passthrough ----

    @Test
    fun `group filter is forwarded to the network call and used as the cache key`() = runTest {
        coEvery { adminCacheDao.get(INSTANCE_ID, "settings", "group:4") } returns null
        coEvery { api.adminSettingList(group = 4, groups = null) } returns ApiResponse(
            code = 200,
            data = listOf(AdminSettingDto(key = "site_url", value = "https://x", flag = 0, group = 4)),
        )

        val result = repository.getSettings(INSTANCE_ID, group = 4, forceRefresh = false) as ApiResult.Success

        assertEquals("site_url", result.data.single().key)
        coVerify(exactly = 1) { api.adminSettingList(4, null) }
        coVerify(exactly = 1) { adminCacheDao.upsert(match { it.cacheKey == "group:4" && it.scope == "settings" }) }
    }

    // ---- 401 ----

    @Test
    fun `getSettings 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { adminCacheDao.get(any(), any(), any()) } returns null
        coEvery { api.adminSettingList(any(), any()) } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getSettings(INSTANCE_ID, group = null, forceRefresh = false)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    private fun sampleItem() = AdminSettingItem(
        key = "site_title",
        value = "My Site",
        type = "string",
        group = 1,
        flag = 0,
        isPrivate = false,
    )

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
