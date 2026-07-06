package io.openlist.client.core.model

/**
 * Directory-level write capability for the currently listed path
 * (v1.0_PRD §9.2 Permission.1, v1.0_EXECUTION_PLAN.md V-604).
 *
 * `fs/list`'s `write` field alone is **not** sufficient to gate an upload
 * entry point: server source (`server/handles/fsread.go`) shows it only
 * reflects a meta-level ACL whitelist (`common.CanWrite`), while the actual
 * upload gate additionally requires the session's `CanWriteContent`
 * permission bit. [canWrite] here is that combined result.
 *
 * `null` means unknown — e.g. a cache-only listing shown before the network
 * response lands. UI must treat `null` as optimistic/low-risk (PRD §4.2.B.6),
 * never as `true`. This is a display hint only, not a security boundary: the
 * backend's 403 response is always the final word (PRD §9.2 Permission.2).
 */
data class DirectoryCapability(
    val canWrite: Boolean?,
) {
    companion object {
        val UNKNOWN = DirectoryCapability(canWrite = null)
    }
}
