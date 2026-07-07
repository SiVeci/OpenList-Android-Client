package io.openlist.client.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminEntryVisibilityTest {

    @Test
    fun noCurrentInstance_hidesEntry() {
        assertEquals(
            AdminEntryVisibility.HIDDEN,
            resolveAdminEntryVisibility(hasCurrentInstance = false, session = adminSession()),
        )
    }

    @Test
    fun noSession_disablesAsUnauthenticated() {
        assertEquals(
            AdminEntryVisibility.DISABLED_UNAUTHENTICATED,
            resolveAdminEntryVisibility(hasCurrentInstance = true, session = null),
        )
    }

    @Test
    fun guestSession_disablesAsUnauthenticated() {
        assertEquals(
            AdminEntryVisibility.DISABLED_UNAUTHENTICATED,
            resolveAdminEntryVisibility(hasCurrentInstance = true, session = guestSession()),
        )
    }

    @Test
    fun nonAdminSession_disablesAsNotAdmin() {
        assertEquals(
            AdminEntryVisibility.DISABLED_NOT_ADMIN,
            resolveAdminEntryVisibility(hasCurrentInstance = true, session = userSession()),
        )
    }

    @Test
    fun adminSession_enablesEntry() {
        assertEquals(
            AdminEntryVisibility.ENABLED,
            resolveAdminEntryVisibility(hasCurrentInstance = true, session = adminSession()),
        )
    }

    private fun adminSession() = session(role = Session.ROLE_ADMIN, isGuest = false)

    private fun userSession() = session(role = Session.ROLE_GENERAL, isGuest = false)

    private fun guestSession() = session(role = Session.ROLE_GUEST, isGuest = true)

    private fun session(role: Int, isGuest: Boolean) = Session(
        instanceId = "instance-1",
        authType = if (isGuest) AuthType.GUEST else AuthType.PASSWORD,
        username = if (isGuest) null else "user",
        role = role,
        permission = 0,
        isGuest = isGuest,
        createdAt = 1L,
        updatedAt = 2L,
    )
}
