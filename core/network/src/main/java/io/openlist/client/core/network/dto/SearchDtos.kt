package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/fs/search request. Source: server/handles/search.go SearchReq
 * (embeds model.SearchReq, not in the local reference checkout). `scope`
 * semantics (0=all/1=dir/2=file assumed) are pending real-device
 * verification — see v0.3_EXECUTION_PLAN.md V-03.
 */
@Serializable
data class SearchReq(
    val parent: String,
    val keywords: String,
    val scope: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 100,
    val password: String = "",
)

/**
 * One search hit. Source: server/handles/search.go SearchResp (embeds
 * model.SearchNode, not in the local reference checkout — this is a
 * best-effort field guess pending V-03). Every field has a default so an
 * unexpected tag degrades to a blank value instead of a decode failure,
 * per NetworkModule's ignoreUnknownKeys/coerceInputValues.
 */
@Serializable
data class SearchNodeResp(
    val parent: String = "",
    val name: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val type: Int = 0,
)

/** data payload of POST /api/fs/search. Source: server/common.PageResp. */
@Serializable
data class SearchPageResp(
    val content: List<SearchNodeResp> = emptyList(),
    val total: Long = 0,
)
