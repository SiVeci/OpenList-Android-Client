package io.openlist.client.core.model

/** Domain projection of `ShareEntity` (v0.3_EXECUTION_PLAN.md §11). [password]
 * is plaintext, matching the backend's own contract (P3). */
data class Share(
    val id: String,
    val instanceId: String,
    val paths: List<String>,
    val name: String?,
    val shareUrl: String?,
    val password: String?,
    val enabled: Boolean,
    val expiresAt: Long?,
    val accessed: Int,
    val maxAccessed: Int,
    val creator: String?,
)

/** Fields the client sets when creating/updating a share (P4: single path,
 * no custom share id, no admin-only fields). */
data class ShareWriteRequest(
    val paths: List<String>,
    val name: String?,
    val password: String?,
    val expiresAt: Long?,
    val maxAccessed: Int = 0,
    val disabled: Boolean = false,
)
