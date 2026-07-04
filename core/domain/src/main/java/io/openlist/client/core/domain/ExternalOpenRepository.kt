package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.ExternalOpenTarget

/**
 * Resolves a file path into an [ExternalOpenTarget] for handing off to
 * another installed app / a browser (v0.4_EXECUTION_PLAN.md §11, P-402;
 * covers [io.openlist.client.core.model.PreviewOpenMode.EXTERNAL_APP] and
 * `.WEB`). S1 scope: interface + Hilt wiring only, real resolution logic
 * lands in a later sprint (S6 per the execution plan's entry-point wiring).
 */
interface ExternalOpenRepository {
    suspend fun resolveExternalOpen(instanceId: String, path: String): ApiResult<ExternalOpenTarget>
}
