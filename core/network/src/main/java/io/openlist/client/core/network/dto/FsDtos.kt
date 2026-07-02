package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/fs/list request. `page`/`per_page` paginate; `refresh` forces the
 * backend to bypass its own cache (requires write permission, so v0.1 keeps it false).
 * `password` is for password-protected directories (meta password).
 * Source: server/handles/fsread.go ListReq (+ embedded PageReq).
 */
@Serializable
data class FsListReq(
    val path: String,
    val password: String = "",
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    val refresh: Boolean = false,
)

/** POST /api/fs/get request. Source: server/handles/fsread.go FsGetReq. */
@Serializable
data class FsGetReq(
    val path: String,
    val password: String = "",
)

/**
 * A single file/directory entry. Source: server/handles/fsread.go ObjResp.
 * `modified`/`created` are RFC3339 timestamp strings; `type` is an OpenList
 * object-type int; `sign` is the signature to append (?sign=) to /d/ /p/ URLs
 * for signed storages; `thumb` is a thumbnail URL (may be empty).
 */
@Serializable
data class ObjResp(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
    val created: String = "",
    val sign: String = "",
    val thumb: String = "",
    val type: Int = 0,
    @SerialName("hashinfo") val hashInfo: String = "",
)

/** data payload of POST /api/fs/list. Source: fsread.go FsListResp. */
@Serializable
data class FsListResp(
    val content: List<ObjResp> = emptyList(),
    val total: Long = 0,
    val readme: String = "",
    val header: String = "",
    val write: Boolean = false,
    val provider: String = "",
)

/**
 * data payload of POST /api/fs/get. Embeds the object fields (flat JSON) plus
 * `raw_url` — the direct download URL, already accounting for proxy vs. direct
 * and signing. v0.1 downloads use raw_url directly.
 * Source: fsread.go FsGetResp (embeds ObjResp).
 */
@Serializable
data class FsGetResp(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
    val created: String = "",
    val sign: String = "",
    val thumb: String = "",
    val type: Int = 0,
    @SerialName("hashinfo") val hashInfo: String = "",
    @SerialName("raw_url") val rawUrl: String = "",
    val readme: String = "",
    val header: String = "",
    val provider: String = "",
    val related: List<ObjResp> = emptyList(),
)
