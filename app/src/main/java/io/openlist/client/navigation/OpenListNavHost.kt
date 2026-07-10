package io.openlist.client.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.navigation.NavBackStackEntry
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

// --- Navigation motion (2026-07) ---
// One spatial rule instead of the navigation-compose default (a 700ms
// symmetric crossfade for every navigation): new content comes from the side
// its target sits on.
//  * The four main tabs form an ordered horizontal band (home 0 · files 1 ·
//    tasks 2 · mine 3): moving between them is a full-width lateral slide
//    whose direction is the tab-index delta — regardless of whether the move
//    came from the bottom bar, a home quick entry, or a system back.
//  * Hierarchical push/pop uses shared-axis X (30% slide + fade), visually
//    one level "floatier" than the tab band so depth reads differently from
//    lateral movement.
//  * Full-screen media routes scale+fade on the Z axis (lightbox feel).
// Splash hands off with a quick fade so cold start doesn't sit in a crossfade.
// In-page switches reuse the same direction rule via DirectionalContent.

private val mainTabOrder = listOf(
    Routes.INSTANCE_LIST,
    Routes.FILE_LIST,
    Routes.TASK_CENTER,
    Routes.SETTINGS,
)
private val mediaRoutes = setOf(Routes.PREVIEW, Routes.MEDIA_PLAYER)

private const val MotionDurationMs = 300
private const val MotionFadeOutMs = 90
private const val MotionFadeInMs = 210

/** Tab-index delta for this transition, or null when either end is not a main tab. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDelta(): Int? {
    val from = mainTabOrder.indexOf(initialState.destination.route)
    val to = mainTabOrder.indexOf(targetState.destination.route)
    if (from < 0 || to < 0) return null
    val delta = to - from
    // A FILE_LIST→FILE_LIST move (recent-path jump while already on the files
    // tab) still carries direction: deeper paths are forward, like the
    // in-screen directory slide.
    return if (delta == 0 && initialState.destination.route == Routes.FILE_LIST) {
        pathArgDepth(targetState) - pathArgDepth(initialState)
    } else {
        delta
    }
}

private fun pathArgDepth(entry: NavBackStackEntry): Int {
    val raw = entry.arguments?.getString("path") ?: "/"
    val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    return decoded.split('/').count { it.isNotEmpty() }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.lateralTabEnter(delta: Int): EnterTransition =
    when {
        delta > 0 -> slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            tween(MotionDurationMs, easing = FastOutSlowInEasing),
        )
        delta < 0 -> slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
            tween(MotionDurationMs, easing = FastOutSlowInEasing),
        )
        // Same-route jump (e.g. recent path while already on the files tab).
        else -> fadeIn(tween(MotionFadeInMs, delayMillis = MotionFadeOutMs))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.lateralTabExit(delta: Int): ExitTransition =
    when {
        delta > 0 -> slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            tween(MotionDurationMs, easing = FastOutSlowInEasing),
        )
        delta < 0 -> slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
            tween(MotionDurationMs, easing = FastOutSlowInEasing),
        )
        else -> fadeOut(tween(MotionFadeOutMs))
    }

private fun sharedAxisEnter(pop: Boolean): EnterTransition =
    slideInHorizontally(tween(MotionDurationMs, easing = FastOutSlowInEasing)) { fullWidth ->
        (if (pop) -fullWidth else fullWidth) / 3
    } + fadeIn(tween(MotionFadeInMs, delayMillis = MotionFadeOutMs))

private fun sharedAxisExit(pop: Boolean): ExitTransition =
    slideOutHorizontally(tween(MotionDurationMs, easing = FastOutSlowInEasing)) { fullWidth ->
        (if (pop) fullWidth else -fullWidth) / 3
    } + fadeOut(tween(MotionFadeOutMs))

private fun mediaEnter(): EnterTransition =
    fadeIn(tween(MotionDurationMs)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(MotionDurationMs, easing = FastOutSlowInEasing))

private fun mediaPopExit(): ExitTransition =
    fadeOut(tween(MotionFadeInMs)) +
        scaleOut(targetScale = 0.96f, animationSpec = tween(MotionFadeInMs, easing = FastOutSlowInEasing))

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
            // consumeWindowInsets marks the insets covered by innerPadding as
            // handled, so screens' own Scaffolds / statusBarsPadding resolve
            // to zero instead of stacking a second (or third) status-bar
            // height on top — the system-bar insets are applied exactly once,
            // here.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = {
                val tabDelta = tabDelta()
                when {
                    initialState.destination.route == Routes.SPLASH -> fadeIn(tween(150))
                    targetState.destination.route in mediaRoutes -> mediaEnter()
                    tabDelta != null -> lateralTabEnter(tabDelta)
                    else -> sharedAxisEnter(pop = false)
                }
            },
            exitTransition = {
                val tabDelta = tabDelta()
                when {
                    initialState.destination.route == Routes.SPLASH -> fadeOut(tween(150))
                    targetState.destination.route in mediaRoutes -> fadeOut(tween(MotionFadeInMs))
                    tabDelta != null -> lateralTabExit(tabDelta)
                    else -> sharedAxisExit(pop = false)
                }
            },
            popEnterTransition = {
                val tabDelta = tabDelta()
                when {
                    initialState.destination.route in mediaRoutes -> fadeIn(tween(MotionDurationMs))
                    tabDelta != null -> lateralTabEnter(tabDelta)
                    else -> sharedAxisEnter(pop = true)
                }
            },
            popExitTransition = {
                val tabDelta = tabDelta()
                when {
                    initialState.destination.route in mediaRoutes -> mediaPopExit()
                    tabDelta != null -> lateralTabExit(tabDelta)
                    else -> sharedAxisExit(pop = true)
                }
            },
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
                onOpenRecentPath = { instanceId, path -> navController.navigate(Routes.fileList(instanceId, path)) },
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
                onOpenShareList = { instanceId -> navController.navigate(Routes.shareList(instanceId)) },
                onOpenShareLink = { navController.navigate(Routes.SHARE_OPEN) },
                onAddInstance = { navController.navigate(Routes.ADD_INSTANCE) },
                onLoggedOut = { instanceId ->
                    navController.navigate(Routes.login(instanceId)) { popUpTo(0) }
                },
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
                onOpenAdminIndex = { navController.navigate(Routes.admin(instanceId, "INDEX")) },
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
