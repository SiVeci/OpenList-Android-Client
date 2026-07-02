package io.openlist.client.core.network.dto

import kotlinx.serialization.Serializable

/**
 * OpenList standard response envelope: every `/api` endpoint returns
 * `{ "code": 200, "message": "success", "data": ... }`.
 * `code == 200` means success; other codes (with HTTP status mirrored) are errors.
 * Source: server/common SuccessResp/ErrorResp (OpenList v4).
 */
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null,
)
