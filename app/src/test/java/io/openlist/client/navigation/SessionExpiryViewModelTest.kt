package io.openlist.client.navigation

import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.model.AuthType
import io.openlist.client.core.model.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.0 S6 fix: [SessionExpiryViewModel] is the app-level generalization of
 * the admin console's own session-expiry redirect (see its KDoc). The only
 * hand-written logic worth a dedicated test is the non-null-to-null edge
 * detection in [SessionExpiryViewModel.observeExpiry] -- the
 * `NavHost`/`navController.navigate` wiring around it mirrors
 * `AdminHostScreen`'s already real-device-proven mechanism verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiryViewModelTest {

    private val authRepository: AuthRepository = mockk()

    private fun session() = Session(
        instanceId = "inst",
        authType = AuthType.PASSWORD,
        username = "u",
        role = Session.ROLE_GENERAL,
        permission = 0,
        isGuest = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `emits once when an existing session disappears`() = runTest {
        val sessionFlow = MutableStateFlow<Session?>(session())
        every { authRepository.observeSession("inst") } returns sessionFlow
        val viewModel = SessionExpiryViewModel(authRepository)
        val emitted = mutableListOf<Unit>()
        val job = launch { viewModel.observeExpiry("inst").collect { emitted.add(it) } }

        runCurrent()
        assertEquals(0, emitted.size)

        sessionFlow.value = null
        runCurrent()
        assertEquals(1, emitted.size)

        job.cancel()
    }

    @Test
    fun `does not emit for a session that never existed`() = runTest {
        val sessionFlow = MutableStateFlow<Session?>(null)
        every { authRepository.observeSession("inst") } returns sessionFlow
        val viewModel = SessionExpiryViewModel(authRepository)
        val emitted = mutableListOf<Unit>()
        val job = launch { viewModel.observeExpiry("inst").collect { emitted.add(it) } }

        runCurrent()
        assertEquals(0, emitted.size)

        job.cancel()
    }

    @Test
    fun `emits again after re-login then a second expiry`() = runTest {
        val sessionFlow = MutableStateFlow<Session?>(session())
        every { authRepository.observeSession("inst") } returns sessionFlow
        val viewModel = SessionExpiryViewModel(authRepository)
        val emitted = mutableListOf<Unit>()
        val job = launch { viewModel.observeExpiry("inst").collect { emitted.add(it) } }
        runCurrent()

        sessionFlow.value = null
        runCurrent()
        assertEquals(1, emitted.size)

        sessionFlow.value = session()
        runCurrent()
        assertEquals(1, emitted.size)

        sessionFlow.value = null
        runCurrent()
        assertEquals(2, emitted.size)

        job.cancel()
    }
}
