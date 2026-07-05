package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.model.AdminAccessState
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Session
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers AdminGateRepositoryImpl's six-state machine (v0.5_EXECUTION_PLAN.md
 * §11 S2-T1 DoD: "全部状态分支单测覆盖") for both [AdminGateRepositoryImpl
 * .checkAccess] (force-refresh path) and [AdminGateRepositoryImpl.observeAccess]
 * (reactive/local path).
 */
class AdminGateRepositoryImplTest {

    private val authRepository = mockk<AuthRepository>()
    private lateinit var repository: AdminGateRepositoryImpl

    private fun setUp() {
        repository = AdminGateRepositoryImpl(authRepository)
    }

    // --- checkAccess ------------------------------------------------------

    @Test
    fun `checkAccess with no local session returns DENIED_GUEST without calling refreshCurrentUser`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns null

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.DENIED_GUEST), result)
    }

    @Test
    fun `checkAccess with a local guest session returns DENIED_GUEST without calling refreshCurrentUser`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_GUEST, isGuest = true)

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.DENIED_GUEST), result)
    }

    @Test
    fun `checkAccess refresh success with admin role returns ALLOWED`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_GENERAL, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns
            ApiResult.Success(session(role = Session.ROLE_ADMIN, isGuest = false))

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.ALLOWED), result)
    }

    @Test
    fun `checkAccess refresh success with non-admin role returns DENIED_NOT_ADMIN`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_GENERAL, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns
            ApiResult.Success(session(role = Session.ROLE_GENERAL, isGuest = false))

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.DENIED_NOT_ADMIN), result)
    }

    @Test
    fun `checkAccess refresh failing with Unauthorized returns SESSION_EXPIRED`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_ADMIN, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns ApiResult.Failure(DomainError.Unauthorized)

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.SESSION_EXPIRED), result)
    }

    @Test
    fun `checkAccess refresh failing with Forbidden returns DENIED_NOT_ADMIN`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_ADMIN, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns ApiResult.Failure(DomainError.Forbidden)

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.DENIED_NOT_ADMIN), result)
    }

    @Test
    fun `checkAccess refresh failing with an unrelated error returns ERROR`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_ADMIN, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns ApiResult.Failure(DomainError.NetworkUnavailable)

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.ERROR), result)
    }

    @Test
    fun `checkAccess refresh failing with InvalidInstance returns DENIED_GUEST`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } returns session(role = Session.ROLE_ADMIN, isGuest = false)
        coEvery { authRepository.refreshCurrentUser(INSTANCE_ID) } returns ApiResult.Failure(DomainError.InvalidInstance)

        val result = repository.checkAccess(INSTANCE_ID)

        assertEquals(ApiResult.Success(AdminAccessState.DENIED_GUEST), result)
    }

    @Test
    fun `checkAccess propagates ApiResult Failure for a truly unexpected exception`() = runTest {
        setUp()
        coEvery { authRepository.getSession(INSTANCE_ID) } throws IllegalStateException("boom")

        val result = repository.checkAccess(INSTANCE_ID)

        assert(result is ApiResult.Failure)
        assert((result as ApiResult.Failure).error is DomainError.Unknown)
    }

    // --- observeAccess ------------------------------------------------------

    @Test
    fun `observeAccess emits CHECKING first then ALLOWED for an admin session`() = runTest {
        setUp()
        every { authRepository.observeSession(INSTANCE_ID) } returns
            flowOf(session(role = Session.ROLE_ADMIN, isGuest = false))

        val emissions = collectAll(repository.observeAccess(INSTANCE_ID))

        assertEquals(listOf(AdminAccessState.CHECKING, AdminAccessState.ALLOWED), emissions)
    }

    @Test
    fun `observeAccess emits DENIED_NOT_ADMIN for a non-admin session`() = runTest {
        setUp()
        every { authRepository.observeSession(INSTANCE_ID) } returns
            flowOf(session(role = Session.ROLE_GENERAL, isGuest = false))

        val emissions = collectAll(repository.observeAccess(INSTANCE_ID))

        assertEquals(listOf(AdminAccessState.CHECKING, AdminAccessState.DENIED_NOT_ADMIN), emissions)
    }

    @Test
    fun `observeAccess emits DENIED_GUEST when there is no session at all`() = runTest {
        setUp()
        every { authRepository.observeSession(INSTANCE_ID) } returns flowOf(null)

        val emissions = collectAll(repository.observeAccess(INSTANCE_ID))

        assertEquals(listOf(AdminAccessState.CHECKING, AdminAccessState.DENIED_GUEST), emissions)
    }

    @Test
    fun `observeAccess reports SESSION_EXPIRED when an ALLOWED session is invalidated mid-stream`() = runTest {
        setUp()
        every { authRepository.observeSession(INSTANCE_ID) } returns
            flowOf(session(role = Session.ROLE_ADMIN, isGuest = false), null)

        val emissions = collectAll(repository.observeAccess(INSTANCE_ID))

        assertEquals(
            listOf(AdminAccessState.CHECKING, AdminAccessState.ALLOWED, AdminAccessState.SESSION_EXPIRED),
            emissions,
        )
    }

    private suspend fun collectAll(flow: kotlinx.coroutines.flow.Flow<AdminAccessState>): List<AdminAccessState> {
        val result = mutableListOf<AdminAccessState>()
        flow.collect { result.add(it) }
        return result
    }

    private fun session(role: Int, isGuest: Boolean) = Session(
        instanceId = INSTANCE_ID,
        authType = if (isGuest) AuthType.GUEST else AuthType.PASSWORD,
        username = if (isGuest) null else "admin",
        role = role,
        permission = 0,
        isGuest = isGuest,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val INSTANCE_ID = "inst-1"
    }
}
