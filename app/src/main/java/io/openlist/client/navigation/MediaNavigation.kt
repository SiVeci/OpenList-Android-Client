package io.openlist.client.navigation

import androidx.navigation.NavHostController

/**
 * Replaces the resolver-only preview destination with the media player so a
 * single back action returns to the screen that originally opened the file.
 */
internal fun NavHostController.navigateFromPreviewToMediaPlayer(
    instanceId: String,
    path: String,
) {
    navigate(Routes.mediaPlayer(instanceId, path)) {
        popUpTo(Routes.PREVIEW) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
