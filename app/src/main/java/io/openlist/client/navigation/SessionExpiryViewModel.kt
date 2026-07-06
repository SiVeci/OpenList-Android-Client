package io.openlist.client.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.domain.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * App-level session-expiry watch (v1.0 S6 fix): before this, only the admin
 * console navigated back to Login on a 401 (`AdminHostScreen`'s own
 * `AdminAccessState.SESSION_EXPIRED` handling) -- every other screen just left
 * the user stuck on an inline "登录已失效" error banner with no way back to
 * Login short of Settings -> 实例管理 -> 进入. `v0.2_ACCEPTANCE_REPORT.md` had
 * signed this off as done by reusing `SessionManager`, but `SessionManager
 * .invalidate` only deletes the DB row; it never navigated anywhere.
 *
 * This reuses the exact mechanism the admin gate already relies on --
 * [AuthRepository.observeSession] is backed by a Room `Flow`, so it reacts to
 * *any* repository's `sessionManager.invalidate(instanceId)` regardless of
 * which module made the call -- generalized to any screen, not just Admin.
 */
@HiltViewModel
class SessionExpiryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Emits once each time [instanceId]'s persisted session transitions from
     * present (authenticated or guest) to absent. Never emits for "never had
     * a session yet" (e.g. the brief window before login completes) -- only
     * for a genuine expiry of a session that existed a moment ago.
     */
    fun observeExpiry(instanceId: String): Flow<Unit> = flow {
        var hadSession = false
        authRepository.observeSession(instanceId).collect { session ->
            val hasSession = session != null
            if (hadSession && !hasSession) emit(Unit)
            hadSession = hasSession
        }
    }
}
