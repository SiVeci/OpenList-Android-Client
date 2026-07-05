package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminUserRepository
import io.openlist.client.core.model.AdminUserPage
import io.openlist.client.core.model.AdminUserSummary
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real implementation (network + `admin_cache` TTL +
 * sensitive-field filtering) lands in S3 (v0.5_EXECUTION_PLAN.md §11 S3-T1). */
@Singleton
class AdminUserRepositoryImpl @Inject constructor() : AdminUserRepository {

    override suspend fun getUsers(instanceId: String, page: Int, forceRefresh: Boolean): ApiResult<AdminUserPage> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getUser(instanceId: String, id: Int): ApiResult<AdminUserSummary> =
        ApiResult.Failure(DomainError.Unknown(null))
}
