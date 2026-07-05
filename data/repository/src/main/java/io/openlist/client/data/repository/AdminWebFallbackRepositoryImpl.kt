package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminWebFallbackRepository
import io.openlist.client.core.model.AdminWebSection
import io.openlist.client.core.model.WebFallbackTarget
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real URL construction + domain validation lands in S7
 * (v0.5_EXECUTION_PLAN.md §11 S7-T3). */
@Singleton
class AdminWebFallbackRepositoryImpl @Inject constructor() : AdminWebFallbackRepository {

    override suspend fun buildAdminUrl(instanceId: String, section: AdminWebSection): ApiResult<WebFallbackTarget> =
        ApiResult.Failure(DomainError.Unknown(null))
}
