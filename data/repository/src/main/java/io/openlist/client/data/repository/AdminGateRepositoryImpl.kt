package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminGateRepository
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.model.AdminAccessState
import io.openlist.client.core.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real S2 gating logic (v0.5_EXECUTION_PLAN.md §11 S2-T1). [checkAccess] force
 * refreshes `/api/me` (via [AuthRepository.refreshCurrentUser]) so a stale
 * locally-cached role can never grant [AdminAccessState.ALLOWED]; [observeAccess]
 * is a lighter-weight reactive view derived from the already-persisted
 * [Session] (no extra network call per emission) for reacting to a 401
 * invalidating the session while the admin console is already open.
 */
@Singleton
class AdminGateRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
) : AdminGateRepository {

    override suspend fun checkAccess(instanceId: String): ApiResult<AdminAccessState> {
        return try {
            // "No instance/no session" is resolved from the locally-persisted
            // Session *before* ever calling refreshCurrentUser: AuthRepositoryImpl
            // .refreshCurrentUser() itself maps "no existing session row" to
            // DomainError.Unauthorized, which would otherwise be indistinguishable
            // from a *previously valid* session that just expired (SESSION_EXPIRED).
            // Checking locally first lets "never logged in" -> DENIED_GUEST and
            // "was logged in, now invalid" -> SESSION_EXPIRED stay genuinely
            // distinct, and also means a guest/never-authenticated instance never
            // triggers a network call here, matching the "游客态不调用...接口"
            // spirit (PRD §8.1.2) even though `/api/me` isn't itself an
            // `/api/admin/*` endpoint.
            val session = authRepository.getSession(instanceId)
            if (session == null || session.isGuest) {
                return ApiResult.Success(AdminAccessState.DENIED_GUEST)
            }
            when (val result = authRepository.refreshCurrentUser(instanceId)) {
                is ApiResult.Success -> ApiResult.Success(
                    if (result.data.isAdmin) AdminAccessState.ALLOWED else AdminAccessState.DENIED_NOT_ADMIN,
                )
                is ApiResult.Failure -> ApiResult.Success(result.error.toAccessState())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Truly unexpected exception (not modeled by the six-state machine
            // above) -- the interface's KDoc explicitly allows Failure to
            // propagate for this case; every "expected" outcome above is
            // encoded as a Success(<state>) instead, since AdminAccessState is
            // meant to be the single value UI branches on.
            ApiResult.Failure(DomainError.Unknown(e))
        }
    }

    /**
     * Purely local/reactive: derives [AdminAccessState] from [AuthRepository
     * .observeSession] without an extra network round-trip per emission (this
     * is a "last known state" view per the interface KDoc, not a re-validation
     * -- [checkAccess] remains the only path that force-refreshes `/api/me`).
     *
     * Uses [scan] so the emitted seed value ([AdminAccessState.CHECKING]) is
     * always first, and so a session disappearing (401 invalidation elsewhere)
     * can be told apart from "never had a session": if the *previous* derived
     * state indicated a real (non-guest) session existed (ALLOWED or
     * DENIED_NOT_ADMIN) and the session then becomes null/guest, that specific
     * transition is reported as [AdminAccessState.SESSION_EXPIRED] rather than
     * [AdminAccessState.DENIED_GUEST].
     */
    override fun observeAccess(instanceId: String): Flow<AdminAccessState> =
        authRepository.observeSession(instanceId)
            .scan(AdminAccessState.CHECKING) { previous, session -> deriveFromSession(previous, session) }

    private fun deriveFromSession(previous: AdminAccessState, session: Session?): AdminAccessState = when {
        session != null && !session.isGuest -> if (session.isAdmin) AdminAccessState.ALLOWED else AdminAccessState.DENIED_NOT_ADMIN
        previous == AdminAccessState.ALLOWED || previous == AdminAccessState.DENIED_NOT_ADMIN -> AdminAccessState.SESSION_EXPIRED
        else -> AdminAccessState.DENIED_GUEST
    }

    private fun DomainError.toAccessState(): AdminAccessState = when (this) {
        DomainError.Unauthorized -> AdminAccessState.SESSION_EXPIRED
        DomainError.Forbidden, DomainError.AdminAccessDenied -> AdminAccessState.DENIED_NOT_ADMIN
        // No instance found for this id (e.g. deleted concurrently) reads the
        // same as "no session" to the gate UI.
        DomainError.InvalidInstance -> AdminAccessState.DENIED_GUEST
        else -> AdminAccessState.ERROR
    }
}
