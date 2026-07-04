package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/share/create and /update request (same shape). Source:
 * server/handles/sharing.go UpdateSharingReq — only the fields the client
 * actually sets are modeled; admin-only fields (creator/new_id/sort/readme/
 * header) are out of v0.3 scope (v0.3_EXECUTION_PLAN.md §4/§5) and omitted,
 * which is safe since Go's JSON binding zero-fills missing fields.
 */
@Serializable
data class UpdateSharingReq(
    val id: String = "",
    val files: List<String>,
    val expires: String? = null,
    val pwd: String = "",
    @SerialName("max_accessed") val maxAccessed: Int = 0,
    val disabled: Boolean = false,
    val remark: String = "",
)

/**
 * One share. Source: server/handles/sharing.go SharingResp (embeds
 * model.Sharing/SharingDB + creator/creator_role). [pwd] is returned in
 * plaintext by the backend itself (v0.3_EXECUTION_PLAN.md §6.1/P3).
 * [expires] is RFC3339 or null (never expires) — see V-06.
 */
@Serializable
data class SharingResp(
    val id: String = "",
    val files: List<String> = emptyList(),
    val pwd: String = "",
    val expires: String? = null,
    @SerialName("max_accessed") val maxAccessed: Int = 0,
    val disabled: Boolean = false,
    val remark: String = "",
    val accessed: Int = 0,
    val creator: String = "",
    @SerialName("creator_role") val creatorRole: Int = 0,
)

/** data payload of GET /api/share/list. Source: server/common.PageResp. */
@Serializable
data class SharePageResp(
    val content: List<SharingResp> = emptyList(),
    val total: Long = 0,
)
