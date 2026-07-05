package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminGateRepository
import io.openlist.client.core.model.AdminAccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1 placeholder — real gating logic (AuthRepository.refreshCurrentUser +
 * six-state machine + 401/403 mapping) lands in S2 (v0.5_EXECUTION_PLAN.md
 * §11 S2-T1). This stub exists only so `:core:domain`'s interface has a
 * concrete Hilt-bindable implementation for S1's module-wiring DoD; nothing
 * calls it yet (the S1 placeholder host screen makes no admin API/repository
 * calls at all).
 */
@Singleton
class AdminGateRepositoryImpl @Inject constructor() : AdminGateRepository {

    override suspend fun checkAccess(instanceId: String): ApiResult<AdminAccessState> =
        ApiResult.Failure(DomainError.Unknown(null))

    override fun observeAccess(instanceId: String): Flow<AdminAccessState> =
        flowOf(AdminAccessState.CHECKING)
}
