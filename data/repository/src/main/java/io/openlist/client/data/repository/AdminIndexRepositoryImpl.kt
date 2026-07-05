package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminIndexRepository
import io.openlist.client.core.model.AdminIndexProgress
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real implementation lands in S6
 * (v0.5_EXECUTION_PLAN.md §11 S6-T1). */
@Singleton
class AdminIndexRepositoryImpl @Inject constructor() : AdminIndexRepository {

    override suspend fun getProgress(instanceId: String): ApiResult<AdminIndexProgress> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun buildIndex(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun updateIndex(instanceId: String, paths: List<String>, maxDepth: Int): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun stopIndex(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun clearIndex(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))
}
