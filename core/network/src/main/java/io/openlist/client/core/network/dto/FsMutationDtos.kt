package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/fs/mkdir request. Source: server/handles/fsmanage.go MkdirOrLinkReq. */
@Serializable
data class MkdirReq(
    val path: String,
)

/**
 * POST /api/fs/rename request. `name` must not contain `/` or `\`, and must
 * not be empty, `.`, or `..` (server: checkRelativePath). Source:
 * server/handles/fsmanage.go RenameReq.
 */
@Serializable
data class RenameReq(
    val path: String,
    val name: String,
    val overwrite: Boolean = false,
)

/**
 * POST /api/fs/remove request. `names` is a batch of entry names inside
 * [dir]; the server aborts the whole request on the first per-item failure
 * and returns no per-item result (v0.2_EXECUTION_PLAN.md decision C: the
 * client sends one name at a time and aggregates results itself). Source:
 * server/handles/fsmanage.go RemoveReq.
 */
@Serializable
data class RemoveReq(
    val dir: String,
    val names: List<String>,
)

/**
 * POST /api/fs/move and /api/fs/copy request (same shape). `merge` only
 * applies to copy (directory merge). Cross-storage moves/copies return an
 * async `tasks` payload rather than completing inline — the client treats a
 * 2xx response as "submitted", not necessarily "already visible" (P7).
 * Source: server/handles/fsmanage.go MoveCopyReq.
 */
@Serializable
data class MoveCopyReq(
    @SerialName("src_dir") val srcDir: String,
    @SerialName("dst_dir") val dstDir: String,
    val names: List<String>,
    val overwrite: Boolean = false,
    @SerialName("skip_existing") val skipExisting: Boolean = false,
    val merge: Boolean = false,
)
