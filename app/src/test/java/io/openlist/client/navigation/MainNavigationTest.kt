package io.openlist.client.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationTest {

    @Test
    fun `shows home tab on instance list`() {
        val state = resolveMainNavigationState(Routes.INSTANCE_LIST)

        assertTrue(state.showBar)
        assertEquals(MainNavigationTab.HOME, state.selectedTab)
    }

    @Test
    fun `maps file and search routes to files tab`() {
        assertEquals(MainNavigationTab.FILES, resolveMainNavigationState(Routes.FILE_LIST).selectedTab)
        assertEquals(MainNavigationTab.FILES, resolveMainNavigationState(Routes.SEARCH).selectedTab)
    }

    @Test
    fun `maps task center to tasks tab`() {
        val state = resolveMainNavigationState(Routes.TASK_CENTER)

        assertTrue(state.showBar)
        assertEquals(MainNavigationTab.TASKS, state.selectedTab)
    }

    @Test
    fun `maps settings and admin to mine tab`() {
        assertEquals(MainNavigationTab.MINE, resolveMainNavigationState(Routes.SETTINGS).selectedTab)
        assertEquals(MainNavigationTab.MINE, resolveMainNavigationState(Routes.ADMIN).selectedTab)
    }

    @Test
    fun `hides on non top level and transient routes`() {
        listOf(
            null,
            Routes.SPLASH,
            Routes.ADD_INSTANCE,
            Routes.LOGIN,
            Routes.FILE_DETAIL,
            Routes.SHARE_LIST,
            Routes.SHARE_OPEN,
            Routes.SHARE_DETAIL,
            Routes.PREVIEW,
            Routes.MEDIA_PLAYER,
        ).forEach { route ->
            val state = resolveMainNavigationState(route)
            assertFalse("route=$route", state.showBar)
            assertEquals("route=$route", null, state.selectedTab)
        }
    }
}
