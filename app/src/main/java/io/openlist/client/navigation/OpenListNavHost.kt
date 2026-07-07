package io.openlist.client.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.openlist.client.core.designsystem.components.AppNavigationBar
import io.openlist.client.core.designsystem.components.AppNavigationItem
import io.openlist.client.feature.admin.AdminHostScreen
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
import io.openlist.client.feature.share.ShareOpenScreen
import io.openlist.client.feature.task.TaskCenterScreen

@Composable
fun OpenListNavHost(navController: NavHostController = rememberNavController()) {
    // v1.0 S6 fix: generalizes the admin console's own session-expiry
    // redirect (see AdminHostScreen's onSessionExpired) to every screen. Keyed
    // on the *currently displayed* instanceId (from the top back-stack
    // entry's nav args) so switching instances or landing on an
    // instance-agnostic route (Settings, Instance List) restarts/suspends the
    // watch accordingly; see SessionExpiryViewModel's KDoc for why this can't
    // double-fire while already on Login.
    val sessionExpiryViewModel: SessionExpiryViewModel = hiltViewModel()
    val mainNavViewModel: MainNavViewModel = hiltViewModel()
    val mainNavUiState by mainNavViewModel.uiState.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val mainNavigationState = resolveMainNavigationState(currentRoute)
    val currentInstanceId = currentBackStackEntry?.arguments?.getString("instanceId")
    val tabInstanceId = currentInstanceId ?: mainNavUiState.currentInstanceId
    LaunchedEffect(currentInstanceId) {
        if (currentInstanceId == null) return@LaunchedEffect
        sessionExpiryViewModel.observeExpiry(currentInstanceId).collect {
            if (navController.currentBackStackEntry?.destination?.route != Routes.LOGIN) {
                navController.navigate(Routes.login(currentInstanceId)) { popUpTo(0) }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (mainNavigationState.showBar) {
                AppNavigationBar(
                    items = mainNavigationItems(
                        state = mainNavigationState,
                        uiState = mainNavUiState,
                        instanceId = tabInstanceId,
                        onHome = {
                            navController.navigate(Routes.INSTANCE_LIST) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        },
                        onFiles = { instanceId ->
                            navController.navigateViaHome(Routes.fileList(instanceId))
                        },
                        onTasks = { instanceId ->
                            navController.navigateViaHome(Routes.taskCenter(instanceId))
                        },
                        onMine = {
                            navController.navigateViaHome(Routes.SETTINGS)
                        },
                    ),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
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
                onOpenFiles = { instanceId -> navController.navigate(Routes.fileList(instanceId)) },
                onOpenSearch = { instanceId -> navController.navigate(Routes.search(instanceId)) },
                onOpenTaskCenter = { instanceId -> navController.navigate(Routes.taskCenter(instanceId)) },
                onOpenShareList = { instanceId -> navController.navigate(Routes.shareList(instanceId)) },
                onOpenAdmin = { instanceId -> navController.navigate(Routes.admin(instanceId)) },
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
                onOpenAdmin = { instanceId -> navController.navigate(Routes.admin(instanceId)) },
            )
        }
        composable(Routes.SHARE_LIST) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            ShareListScreen(
                onBack = { navController.popBackStack() },
                onOpenShareDetail = { shareId -> navController.navigate(Routes.shareDetail(instanceId, shareId)) },
                onOpenShareLink = { navController.navigate(Routes.SHARE_OPEN) },
            )
        }
        composable(Routes.SHARE_OPEN) {
            ShareOpenScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SHARE_DETAIL) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            ShareDetailScreen(
                onBack = { navController.popBackStack() },
                // Ordinary Routes.fileList browsing (the share creator's own
                // normal permissions), not any share-mode/guest-scoped
                // directory view -- see ShareDetailScreen's KDoc.
                onOpenDirectory = { path -> navController.navigate(Routes.fileList(instanceId, path)) },
                onOpenFile = { path -> navController.navigate(Routes.preview(instanceId, path)) },
                onOpenFileDetail = { path -> navController.navigate(Routes.fileDetail(instanceId, path)) },
            )
        }
        composable(Routes.SEARCH) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate(Routes.fileList(instanceId, path)) },
                onOpenFile = { path -> navController.navigate(Routes.preview(instanceId, path)) },
                onOpenFileDetail = { path -> navController.navigate(Routes.fileDetail(instanceId, path)) },
            )
        }
        composable(Routes.TASK_CENTER) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            TaskCenterScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate(Routes.fileList(instanceId, path)) },
                onOpenFile = { path -> navController.navigate(Routes.preview(instanceId, path)) },
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
        composable(Routes.ADMIN) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            val tab = backStackEntry.arguments?.getString("tab")
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            AdminHostScreen(
                instanceId = instanceId,
                tab = tab,
                onBack = { navController.popBackStack() },
                onSessionExpired = { expiredInstanceId ->
                    navController.navigate(Routes.login(expiredInstanceId)) { popUpTo(0) }
                },
            )
        }
    }
    }
}

private fun NavHostController.navigateViaHome(route: String) {
    navigate(Routes.INSTANCE_LIST) {
        popUpTo(0)
        launchSingleTop = true
    }
    if (route != Routes.INSTANCE_LIST) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

@Composable
private fun mainNavigationItems(
    state: MainNavigationState,
    uiState: MainNavUiState,
    instanceId: String?,
    onHome: () -> Unit,
    onFiles: (String) -> Unit,
    onTasks: (String) -> Unit,
    onMine: () -> Unit,
): List<AppNavigationItem> {
    val instanceTabEnabled = uiState.hasInstances && instanceId != null
    return listOf(
        AppNavigationItem(
            label = "首页",
            icon = Icons.Outlined.Home,
            selected = state.selectedTab == MainNavigationTab.HOME,
            onClick = onHome,
        ),
        AppNavigationItem(
            label = "文件",
            icon = Icons.Outlined.Folder,
            selected = state.selectedTab == MainNavigationTab.FILES,
            enabled = instanceTabEnabled,
            onClick = { instanceId?.let(onFiles) },
        ),
        AppNavigationItem(
            label = "任务",
            icon = Icons.Outlined.Assignment,
            selected = state.selectedTab == MainNavigationTab.TASKS,
            enabled = instanceTabEnabled,
            badgeCount = uiState.activeTaskCount,
            onClick = { instanceId?.let(onTasks) },
        ),
        AppNavigationItem(
            label = "我的",
            icon = Icons.Outlined.Person,
            selected = state.selectedTab == MainNavigationTab.MINE,
            onClick = onMine,
        ),
    )
}
