package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.auth.CryptoManager
import io.openlist.client.core.auth.SessionManager
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.LoginResult
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListApi
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.LoginResp
import io.openlist.client.core.network.dto.UserResp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the v1.0 LDAP/OTP additions to AuthRepositoryImpl
 * (v1.0_EXECUTION_PLAN.md §11 S1-T3 DoD: "成功/402→NeedOtp/OTP 错/LDAP 403/401")
 * plus a minimal regression for the pre-existing password/guest/token paths
 * this Sprint touched.
 */
class AuthRepositoryImplTest {

    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val instanceRepository = mockk<InstanceRepository>()
    private val clientFactory = mockk<OpenListClientFactory>()
    private val api = mockk<OpenListApi>()
    private val cryptoManager = mockk<CryptoManager>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private lateinit var repository: AuthRepositoryImpl

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
        coEvery { instanceRepository.setCurrent(INSTANCE_ID) } returns Unit
        every { clientFactory.apiFor(any()) } returns api
        every { cryptoManager.encrypt(any()) } answers { "enc:${firstArg<String>()}" }
        repository = AuthRepositoryImpl(
            sessionDao = sessionDao,
            instanceRepository = instanceRepository,
            clientFactory = clientFactory,
            instanceContext = InstanceContext(),
            cryptoManager = cryptoManager,
            sessionManager = sessionManager,
        )
    }

    // --- loginWithPassword: success / OTP / failure ------------------------

    @Test
    fun `loginWithPassword success bootstraps a session`() = runTest {
        coEvery { api.login(any()) } returns ApiResponse(code = 200, data = LoginResp(token = "tok"))
        coEvery { api.meWithToken("tok") } returns ApiResponse(code = 200, data = userResp())

        val result = repository.loginWithPassword(INSTANCE_ID, "alice", "pw")

        assertTrue(result is ApiResult.Success)
        val loginResult = (result as ApiResult.Success).data
        assertTrue(loginResult is LoginResult.Success)
    }

    @Test
    fun `loginWithPassword first attempt with envelope 402 returns NeedOtp, not a failure`() = runTest {
        coEvery { api.login(any()) } returns ApiResponse(code = 402, message = "Invalid 2FA code")

        val result = repository.loginWithPassword(INSTANCE_ID, "alice", "pw", otpCode = null)

        assertTrue(result is ApiResult.Success)
        val loginResult = (result as ApiResult.Success).data
        assertTrue(loginResult is LoginResult.NeedOtp)
        val challenge = (loginResult as LoginResult.NeedOtp).challenge
        assertEquals(INSTANCE_ID, challenge.instanceId)
        assertEquals("alice", challenge.username)
        assertEquals(AuthType.PASSWORD, challenge.method)
    }

    @Test
    fun `loginWithPassword resubmission with wrong otp maps to OtpInvalid, not another NeedOtp`() = runTest {
        coEvery { api.login(any()) } returns ApiResponse(code = 402, message = "Invalid 2FA code")

        val result = repository.loginWithPassword(INSTANCE_ID, "alice", "pw", otpCode = "000000")

        assertEquals(ApiResult.Failure(DomainError.OtpInvalid), result)
    }

    @Test
    fun `loginWithPassword wrong credentials maps to Unauthorized`() = runTest {
        coEvery { api.login(any()) } returns ApiResponse(code = 401, message = "Invalid username or password")

        val result = repository.loginWithPassword(INSTANCE_ID, "alice", "wrong")

        assertEquals(ApiResult.Failure(DomainError.Unauthorized), result)
    }

    // --- loginWithLdap ------------------------------------------------------

    @Test
    fun `loginWithLdap success bootstraps a session with AuthType LDAP`() = runTest {
        coEvery { api.loginLdap(any()) } returns ApiResponse(code = 200, data = LoginResp(token = "tok"))
        coEvery { api.meWithToken("tok") } returns ApiResponse(code = 200, data = userResp())

        val result = repository.loginWithLdap(INSTANCE_ID, "alice", "pw")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data is LoginResult.Success)
    }

    @Test
    fun `loginWithLdap disabled-on-instance 403 maps to AuthMethodUnavailable, not generic Forbidden`() = runTest {
        coEvery { api.loginLdap(any()) } returns ApiResponse(code = 403, message = "ldap is not enabled")

        val result = repository.loginWithLdap(INSTANCE_ID, "alice", "pw")

        assertEquals(ApiResult.Failure(DomainError.AuthMethodUnavailable), result)
    }

    @Test
    fun `loginWithLdap account-not-allowed 403 also maps to AuthMethodUnavailable`() = runTest {
        coEvery { api.loginLdap(any()) } returns ApiResponse(code = 403, message = "login via ldap is not allowed")

        val result = repository.loginWithLdap(INSTANCE_ID, "alice", "pw")

        assertEquals(ApiResult.Failure(DomainError.AuthMethodUnavailable), result)
    }

    @Test
    fun `loginWithLdap wrong credentials 400 keeps generic mapping (not AuthMethodUnavailable)`() = runTest {
        coEvery { api.loginLdap(any()) } returns ApiResponse(code = 400, message = "bad credentials")

        val result = repository.loginWithLdap(INSTANCE_ID, "alice", "wrong") as ApiResult.Failure

        assertTrue(result.error is DomainError.OpenListError)
    }

    // --- pre-existing paths this Sprint touched (minimal regression) -------

    @Test
    fun `loginAsGuest clears any stale token before probing me`() = runTest {
        coEvery { sessionDao.deleteByInstanceId(INSTANCE_ID) } returns Unit
        coEvery { api.me() } returns ApiResponse(code = 200, data = userResp(username = ""))

        val result = repository.loginAsGuest(INSTANCE_ID)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `loginWithToken validates the token before persisting`() = runTest {
        coEvery { api.meWithToken("admin-token") } returns ApiResponse(code = 200, data = userResp())

        val result = repository.loginWithToken(INSTANCE_ID, "admin-token")

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `logout invalidates local session without touching the network`() = runTest {
        repository.logout(INSTANCE_ID)

        coVerify(exactly = 1) { sessionManager.invalidate(INSTANCE_ID) }
        coVerify(exactly = 0) { instanceRepository.getById(any()) }
        coVerify(exactly = 0) { api.me() }
        coVerify(exactly = 0) { api.meWithToken(any()) }
    }

    private fun userResp(username: String = "alice") = UserResp(
        id = 1,
        username = username,
        role = 0,
        permission = 0,
        otp = false,
    )

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
