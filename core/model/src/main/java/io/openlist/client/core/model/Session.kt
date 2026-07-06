package io.openlist.client.core.model

/** Mirrors OpenList's model.User role constants (GENERAL=0, GUEST=1, ADMIN=2).
 * [LDAP] added in v1.0 (v1.0_EXECUTION_PLAN.md §8/DEC-601-adjacent V-601):
 * `SessionEntity.authType` is a String column (`AuthType.name`/`valueOf`), so
 * this is a zero-migration addition. */
enum class AuthType { PASSWORD, LDAP, GUEST, TOKEN }

data class Session(
    val instanceId: String,
    val authType: AuthType,
    val username: String?,
    val role: Int,
    /** Bitmask of OpenList's per-user capabilities (write/rename/move/copy/remove/...),
     * from `/api/me`'s `permission` field. Directory-level `FsListResp.write` takes
     * priority when both are available (v0.2_EXECUTION_PLAN.md P5); this is the
     * user-level fallback. */
    val permission: Int,
    val isGuest: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    /** True if bit [flag] is set in [permission] (OpenList's model.User permission bits). */
    fun canDo(flag: Int): Boolean = (permission shr flag) and 1 == 1

    companion object {
        const val ROLE_GENERAL = 0
        const val ROLE_GUEST = 1
        const val ROLE_ADMIN = 2

        // OpenList model.User permission bit indices (server: model/user.go).
        const val PERM_WRITE = 3
        const val PERM_RENAME = 4
        const val PERM_MOVE = 5
        const val PERM_COPY = 6
        const val PERM_REMOVE = 7
    }
}
