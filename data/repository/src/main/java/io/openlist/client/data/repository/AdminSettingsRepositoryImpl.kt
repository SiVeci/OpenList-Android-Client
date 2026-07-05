package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.AdminSettingsRepository
import io.openlist.client.core.model.AdminSettingItem
import javax.inject.Inject
import javax.inject.Singleton

/** S1 placeholder — real implementation (list/default/grouping/private-value
 * masking) lands in S7 (v0.5_EXECUTION_PLAN.md §11 S7-T1). */
@Singleton
class AdminSettingsRepositoryImpl @Inject constructor() : AdminSettingsRepository {

    override suspend fun getSettings(
        instanceId: String,
        group: Int?,
        forceRefresh: Boolean,
    ): ApiResult<List<AdminSettingItem>> = ApiResult.Failure(DomainError.Unknown(null))

    override suspend fun getDefaultSettings(instanceId: String, group: Int?): ApiResult<List<AdminSettingItem>> =
        ApiResult.Failure(DomainError.Unknown(null))
}
