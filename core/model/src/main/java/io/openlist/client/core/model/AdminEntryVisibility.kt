package io.openlist.client.core.model

enum class AdminEntryVisibility {
    HIDDEN,
    DISABLED_UNAUTHENTICATED,
    DISABLED_NOT_ADMIN,
    ENABLED,
}

fun resolveAdminEntryVisibility(
    hasCurrentInstance: Boolean,
    session: Session?,
): AdminEntryVisibility = when {
    !hasCurrentInstance -> AdminEntryVisibility.HIDDEN
    session == null || session.isGuest -> AdminEntryVisibility.DISABLED_UNAUTHENTICATED
    session.isAdmin -> AdminEntryVisibility.ENABLED
    else -> AdminEntryVisibility.DISABLED_NOT_ADMIN
}
