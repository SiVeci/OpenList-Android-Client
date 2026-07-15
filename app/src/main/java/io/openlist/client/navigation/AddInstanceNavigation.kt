package io.openlist.client.navigation

import androidx.navigation.NavHostController

/**
 * Completes the add-instance flow by opening the newly created instance's
 * login page as a fresh root. This works for both first-run and home entry.
 */
internal fun NavHostController.navigateAfterInstanceSaved(instanceId: String) {
    navigate(Routes.login(instanceId)) {
        popUpTo(0)
        launchSingleTop = true
    }
}

/**
 * Leaves add-instance normally when a parent exists. First-run removes Splash,
 * so an empty stack falls back to the existing empty home workspace.
 */
internal fun NavHostController.leaveAddInstance() {
    if (popBackStack()) return

    navigate(Routes.INSTANCE_LIST) {
        popUpTo(0)
        launchSingleTop = true
    }
}
