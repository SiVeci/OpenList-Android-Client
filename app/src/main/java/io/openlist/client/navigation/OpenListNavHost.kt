package io.openlist.client.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.openlist.client.feature.auth.LoginScreen
import io.openlist.client.feature.files.FileDetailScreen
import io.openlist.client.feature.files.FileListScreen
import io.openlist.client.feature.instance.AddInstanceScreen
import io.openlist.client.feature.instance.InstanceListScreen
import io.openlist.client.feature.preview.MediaPlayerScreen
import io.openlist.client.feature.preview.PreviewScreen
import io.openlist.client.feature.search.SearchScreen
import io.openlist.client.feature.settings.SettingsScreen
import io.openlist.client.feature.share.ShareDetailScreen
import io.openlist.client.feature.share.ShareListScreen
import io.openlist.client.feature.task.TaskCenterScreen

@Composable
fun OpenListNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onNavigate = { route, popSplash ->
                navController.navigate(route) {
                    if (popSplash) popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.INSTANCE_LIST) {
            InstanceListScreen(
                onAddInstance = { navController.navigate(Routes.ADD_INSTANCE) },
                onOpenInstance = { instanceId -> navController.navigate(Routes.login(instanceId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.ADD_INSTANCE) {
            AddInstanceScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                // Login may be the sole back-stack entry (reached directly from
                // Splash) or one of several (reached by tapping a row in
                // InstanceList), so both of these targets reset the stack via
                // popUpTo(0) instead of relying on a specific route being present.
                onSwitchInstance = {
                    navController.navigate(Routes.INSTANCE_LIST) { popUpTo(0) }
                },
                onAuthenticated = { instanceId ->
                    navController.navigate(Routes.fileList(instanceId)) { popUpTo(0) }
                },
            )
        }
        composable(Routes.FILE_LIST) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            FileListScreen(
                onOpenFileDetail = { path -> navController.navigate(Routes.fileDetail(instanceId, path)) },
                onOpenFile = { path -> navController.navigate(Routes.preview(instanceId, path)) },
                onBackToInstances = {
                    navController.navigate(Routes.INSTANCE_LIST) { popUpTo(0) }
                },
                onOpenShareList = { navController.navigate(Routes.shareList(instanceId)) },
                onOpenSearch = { path -> navController.navigate(Routes.search(instanceId, path)) },
                onOpenTaskCenter = { navController.navigate(Routes.taskCenter(instanceId)) },
            )
        }
        composable(Routes.FILE_DETAIL) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            FileDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = { path -> navController.navigate(Routes.preview(instanceId, path)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenInstances = { navController.navigate(Routes.INSTANCE_LIST) { popUpTo(0) } },
                onOpenTaskCenter = { instanceId -> navController.navigate(Routes.taskCenter(instanceId)) },
            )
        }
        composable(Routes.SHARE_LIST) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            ShareListScreen(
                onBack = { navController.popBackStack() },
                onOpenShareDetail = { shareId -> navController.navigate(Routes.shareDetail(instanceId, shareId)) },
            )
        }
        composable(Routes.SHARE_DETAIL) {
            ShareDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate(Routes.fileList(instanceId, path)) },
                onOpenFileDetail = { path -> navController.navigate(Routes.fileDetail(instanceId, path)) },
            )
        }
        composable(Routes.TASK_CENTER) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            TaskCenterScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate(Routes.fileList(instanceId, path)) },
            )
        }
        composable(Routes.PREVIEW) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            val path = backStackEntry.arguments?.getString("path")
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?: "/"
            PreviewScreen(
                instanceId = instanceId,
                path = path,
                onBack = { navController.popBackStack() },
                onOpenMediaPlayer = { mediaPath -> navController.navigate(Routes.mediaPlayer(instanceId, mediaPath)) },
            )
        }
        composable(Routes.MEDIA_PLAYER) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            val path = backStackEntry.arguments?.getString("path")
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?: "/"
            MediaPlayerScreen(
                instanceId = instanceId,
                path = path,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
