package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminUserPage
import io.openlist.client.core.model.AdminUserSummary

/**
 * Read-only user administration (PRD §10.3). Never exposes password/hash/
 * salt/OTP-secret fields — filtering happens at the DTO->domain mapping
 * boundary in the implementation, before a value ever reaches this
 * interface's return types. Does not support create/update/delete/2FA-reset
 * (out of v0.5 scope, PRD §9.2 "不接入 create/update/delete...").
 */
interface AdminUserRepository {
    /** [forceRefresh] bypasses the `admin_cache` TTL (1 min, PRD §13.1) and
     * always hits the network — required for pull-to-refresh (PRD §13.1
     * "用户下拉刷新必须强制请求远程"). */
    suspend fun getUsers(instanceId: String, page: Int, forceRefresh: Boolean = false): ApiResult<AdminUserPage>

    suspend fun getUser(instanceId: String, id: Int): ApiResult<AdminUserSummary>
}
