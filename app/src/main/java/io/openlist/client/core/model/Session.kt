package io.openlist.client.core.model

/** Mirrors OpenList's model.User role constants (GENERAL=0, GUEST=1, ADMIN=2). */
enum class AuthType { PASSWORD, GUEST, TOKEN }

data class Session(
    val instanceId: String,
    val authType: AuthType,
    val username: String?,
    val role: Int,
    val isGuest: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_GENERAL = 0
        const val ROLE_GUEST = 1
        const val ROLE_ADMIN = 2
    }
}
