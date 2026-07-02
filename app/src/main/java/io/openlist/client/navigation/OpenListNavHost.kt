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
import io.openlist.client.feature.settings.SettingsScreen

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
                onBackToInstances = {
                    navController.navigate(Routes.INSTANCE_LIST) { popUpTo(0) }
                },
            )
        }
        composable(Routes.FILE_DETAIL) {
            FileDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenInstances = { navController.navigate(Routes.INSTANCE_LIST) { popUpTo(0) } },
            )
        }
    }
}
