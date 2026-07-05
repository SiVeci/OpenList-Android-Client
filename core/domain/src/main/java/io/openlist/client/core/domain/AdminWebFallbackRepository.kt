package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.AdminWebSection
import io.openlist.client.core.model.WebFallbackTarget

/**
 * Constructs (never fetches/navigates) a Web-console fallback URL for the
 * current instance (PRD §10.8/§15.3). Must reject any URL outside the
 * current instance's base URL + sub-path; must never embed a token/
 * Authorization value. Launching the resulting URL (Custom Tabs / external
 * browser / `ActivityNotFoundException` handling) is a `:feature:admin` UI
 * concern, not this repository's (same split as the existing
 * `ExternalOpenRepository` precedent).
 */
interface AdminWebFallbackRepository {
    suspend fun buildAdminUrl(instanceId: String, section: AdminWebSection): ApiResult<WebFallbackTarget>
}
