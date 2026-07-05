package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminSettingItem

/**
 * Read-only settings viewing (PRD §10.7). Never supports save/delete/reset-
 * token/tool-config writes (out of v0.5 scope, PRD §9.2). Private values are
 * masked by the implementation before reaching the domain model — see
 * [AdminSettingItem.isPrivate] KDoc (`core:model`).
 */
interface AdminSettingsRepository {
    /** [group] null means "all groups" (PRD §13.1.5: `admin_cache` TTL 5 min;
     * private items' `value` must never be cached — see AdminSettingItem KDoc). */
    suspend fun getSettings(instanceId: String, group: Int? = null, forceRefresh: Boolean = false): ApiResult<List<AdminSettingItem>>

    suspend fun getDefaultSettings(instanceId: String, group: Int? = null): ApiResult<List<AdminSettingItem>>
}
