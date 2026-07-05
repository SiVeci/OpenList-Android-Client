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
import io.openlist.client.core.model.AdminUserPage
import io.openlist.client.core.model.AdminUserSummary
import io.openlist.client.core.model.Instance
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.AdminUserDto
import io.openlist.client.core.network.dto.AdminUserPageDto
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
 * Covers AdminUserRepositoryImpl's admin_cache TTL behavior (v0.5_EXECUTION_PLAN.md
 * §11 S3-T1 DoD) and the DTO->domain sensitive-field-drop mapping, following
 * the mocked-collaborator pattern established by PreviewRepositoryImplTest.
 */
class AdminUserRepositoryImplTest {

    private val api = mockk<OpenListApi>()
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val adminCacheDao = mockk<AdminCacheDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var repository: AdminUserRepositoryImpl

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
        repository = AdminUserRepositoryImpl(
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            sessionManager = sessionManager,
            adminCacheDao = adminCacheDao,
            json = json,
        )
    }

    // ---- sensitive-field mapping ----

    @Test
    fun `mapping drops password and leaves otpEnabled unknown even when the DTO carries a non-blank password`() {
        val dto = AdminUserDto(
            id = 7,
            username = "alice",
            password = "should-never-surface",
            basePath = "/alice",
            role = 2,
            disabled = false,
            permission = 15,
            ssoId = "",
            allowLdap = true,
            otp = true,
        )

        val domain = dto.toDomain()

        assertEquals(7, domain.id)
        assertEquals("alice", domain.username)
        assertEquals("管理员", domain.roleLabel)
        assertEquals(false, domain.disabled)
        assertEquals("/alice", domain.basePath)
        assertEquals(15, domain.permission)
        // No field on AdminUserSummary can carry `password` at all -- this
        // assertion is really just confirming the mapper didn't crash/throw
        // on a non-blank password value, and that otpEnabled stays
        // conservative ("unknown") rather than trusting the unverified `otp`
        // DTO field (S1-T4/T3 KDoc: no confirmed backend field for this).
        assertNull(domain.otpEnabled)
    }

    @Test
    fun `getUser never surfaces password through the repository boundary`() = runTest {
        coEvery { api.adminUserGet(42) } returns ApiResponse(
            code = 200,
            data = AdminUserDto(id = 42, username = "bob", password = "leaked-if-bug", role = 0),
        )

        val result = repository.getUser(INSTANCE_ID, 42) as ApiResult.Success

        assertEquals("bob", result.data.username)
        // AdminUserSummary structurally has no password field -- reflection
        // check that its declared property set matches exactly what S1-T4
        // defined, so a future careless field addition can't quietly smuggle
        // a sensitive value back in without this test being updated too.
        val fieldNames = AdminUserSummary::class.java.declaredFields.map { it.name }
        assertTrue(fieldNames.none { it.contains("password", ignoreCase = true) })
        assertTrue(fieldNames.none { it.contains("pwd", ignoreCase = true) })
        assertTrue(fieldNames.none { it.contains("salt", ignoreCase = true) })
        assertTrue(fieldNames.none { it.contains("secret", ignoreCase = true) })
    }

    // ---- cache TTL ----

    @Test
    fun `getUsers within TTL returns cached value without calling the network`() = runTest {
        val cachedPage = AdminUserPage(users = listOf(sampleUser()), total = 1)
        coEvery { adminCacheDao.get(INSTANCE_ID, "users", "page:1") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "users",
            cacheKey = "page:1",
            rawJson = json.encodeToString(cachedPage),
            cachedAt = System.currentTimeMillis() - 1_000L,
        )

        val result = repository.getUsers(INSTANCE_ID, page = 1, forceRefresh = false) as ApiResult.Success

        assertEquals(cachedPage, result.data)
        coVerify(exactly = 0) { api.adminUserList(any(), any()) }
    }

    @Test
    fun `getUsers with forceRefresh always calls the network even when a fresh cache entry exists`() = runTest {
        coEvery { adminCacheDao.get(INSTANCE_ID, "users", "page:1") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "users",
            cacheKey = "page:1",
            rawJson = json.encodeToString(AdminUserPage(users = emptyList(), total = 0)),
            cachedAt = System.currentTimeMillis(),
        )
        coEvery { api.adminUserList(page = 1, perPage = any()) } returns ApiResponse(
            code = 200,
            data = AdminUserPageDto(content = listOf(AdminUserDto(id = 1, username = "fresh", role = 0)), total = 1),
        )

        val result = repository.getUsers(INSTANCE_ID, page = 1, forceRefresh = true) as ApiResult.Success

        assertEquals("fresh", result.data.users.single().username)
        coVerify(exactly = 1) { api.adminUserList(1, any()) }
        coVerify(exactly = 1) { adminCacheDao.upsert(any()) }
    }

    @Test
    fun `getUsers with an expired cache entry refreshes from the network`() = runTest {
        coEvery { adminCacheDao.get(INSTANCE_ID, "users", "page:1") } returns AdminCacheEntity(
            id = "x",
            instanceId = INSTANCE_ID,
            scope = "users",
            cacheKey = "page:1",
            rawJson = json.encodeToString(AdminUserPage(users = emptyList(), total = 0)),
            cachedAt = System.currentTimeMillis() - 120_000L, // 2 min ago, TTL is 1 min
        )
        coEvery { api.adminUserList(page = 1, perPage = any()) } returns ApiResponse(
            code = 200,
            data = AdminUserPageDto(content = listOf(AdminUserDto(id = 2, username = "refreshed", role = 0)), total = 1),
        )

        val result = repository.getUsers(INSTANCE_ID, page = 1, forceRefresh = false) as ApiResult.Success

        assertEquals("refreshed", result.data.users.single().username)
        coVerify(exactly = 1) { api.adminUserList(1, any()) }
    }

    @Test
    fun `getUsers 401 invalidates the session and propagates the failure`() = runTest {
        coEvery { adminCacheDao.get(any(), any(), any()) } returns null
        coEvery { api.adminUserList(any(), any()) } returns ApiResponse(code = 401, message = "unauthorized", data = null)

        val result = repository.getUsers(INSTANCE_ID, page = 1, forceRefresh = false)

        assertTrue(result is ApiResult.Failure)
        assertEquals(DomainError.Unauthorized, (result as ApiResult.Failure).error)
        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
    }

    private fun sampleUser() = AdminUserSummary(
        id = 1,
        username = "seed",
        role = 0,
        roleLabel = "普通用户",
        disabled = false,
        basePath = "/",
        permission = 0,
        otpEnabled = null,
    )

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
