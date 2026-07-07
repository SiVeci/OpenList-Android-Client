package io.openlist.client.navigation

enum class MainNavigationTab {
    HOME,
    FILES,
    TASKS,
    MINE,
}

data class MainNavigationState(
    val showBar: Boolean,
    val selectedTab: MainNavigationTab?,
)

fun resolveMainNavigationState(route: String?): MainNavigationState {
    val selectedTab = when (route) {
        Routes.INSTANCE_LIST -> MainNavigationTab.HOME
        Routes.FILE_LIST,
        Routes.SEARCH -> MainNavigationTab.FILES
        Routes.TASK_CENTER -> MainNavigationTab.TASKS
        Routes.SETTINGS,
        Routes.ADMIN -> MainNavigationTab.MINE
        else -> null
    }
    return MainNavigationState(
        showBar = selectedTab != null,
        selectedTab = selectedTab,
    )
}
