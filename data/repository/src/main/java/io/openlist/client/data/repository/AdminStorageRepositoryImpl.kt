package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.model.AdminStoragePage
import io.openlist.client.core.model.AdminStorageSummary
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real read-only implementation lands in S3
 * (v0.5_EXECUTION_PLAN.md §11 S3-T3), enable/disable/load_all + cache
 * linkage lands in S4 (S4-T1/T2). */
@Singleton
class AdminStorageRepositoryImpl @Inject constructor() : AdminStorageRepository {

    override suspend fun getStorages(instanceId: String, forceRefresh: Boolean): ApiResult<AdminStoragePage> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getStorage(instanceId: String, id: Int): ApiResult<AdminStorageSummary> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDrivers(instanceId: String): ApiResult<Map<String, Any?>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDriverNames(instanceId: String): ApiResult<List<String>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDriverInfo(instanceId: String, driver: String): ApiResult<Map<String, Any?>> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun enableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun disableStorage(instanceId: String, id: Int): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun reloadAllStorages(instanceId: String): ApiResult<Unit> =
        ApiResult.Failure(DomainError.Unknown(null))
}
