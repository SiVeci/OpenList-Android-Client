package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.ExternalOpenRepository
import io.openlist.client.core.model.ExternalOpenTarget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.4 Sprint 1 placeholder (v0.4_EXECUTION_PLAN.md §11 S1-T4) — see
 * [PreviewRepositoryImpl]'s KDoc for the shared rationale. Real
 * external-app/web resolution logic lands in S6 (entry-point wiring).
 */
@Singleton
class ExternalOpenRepositoryImpl @Inject constructor() : ExternalOpenRepository {

    override suspend fun resolveExternalOpen(instanceId: String, path: String): ApiResult<ExternalOpenTarget> =
        ApiResult.Failure(DomainError.Unknown(null))
}
