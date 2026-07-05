package io.openlist.client.feature.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.components.AdminTabRow
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.model.AdminAccessState

/**
 * Real host for the `admin/{instanceId}?tab={tab}` route
 * (v0.5_EXECUTION_PLAN.md §11 S2-T3), replacing the S1 placeholder. This is
 * the S2 "earliest runnable node" (§13): gating must be demonstrably correct
 * here -- CHECKING shows a spinner, DENIED_ and ERROR states render an
 * explanation screen with **no Tab content composable ever invoked** (so
 * zero admin API calls happen in those states, by construction: the `when`
 * below simply never reaches the branch that would call [AdminOverviewTab]
 * or any other Tab), and SESSION_EXPIRED hands off to [onSessionExpired] to
 * reuse the app's existing login flow rather than inventing a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHostScreen(
    instanceId: String,
    tab: String?,
    onBack: () -> Unit,
    onSessionExpired: (instanceId: String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.accessState) {
        if (uiState.accessState == AdminAccessState.SESSION_EXPIRED) {
            onSessionExpired(instanceId)
        }
    }

    when (uiState.accessState) {
        AdminAccessState.CHECKING -> {
            Scaffold(topBar = { AppTopBar(title = "管理台", onBack = onBack) }) { padding ->
                LoadingState(modifier = Modifier.padding(padding))
            }
        }
        AdminAccessState.SESSION_EXPIRED -> {
            // onSessionExpired (LaunchedEffect above) navigates away; this is
            // only the brief frame shown while that navigation happens.
            Scaffold(topBar = { AppTopBar(title = "管理台", onBack = onBack) }) { padding ->
                LoadingState(modifier = Modifier.padding(padding))
            }
        }
        AdminAccessState.DENIED_GUEST, AdminAccessState.DENIED_NOT_ADMIN, AdminAccessState.ERROR -> {
            AdminAccessDeniedScreen(
                accessState = uiState.accessState,
                onBack = onBack,
            )
        }
        AdminAccessState.ALLOWED -> {
            AdminScaffold(
                instanceId = instanceId,
                uiState = uiState,
                onBack = onBack,
                onSelectTab = viewModel::selectTab,
                onLoadOverviewCards = viewModel::loadOverviewCardsIfNeeded,
                onRetryStorage = viewModel::refreshStorageSummary,
                onRetryTask = viewModel::refreshTaskSummary,
                onRetryIndex = viewModel::refreshIndexSummary,
            )
        }
    }
}

@Composable
private fun AdminAccessDeniedScreen(
    accessState: AdminAccessState,
    onBack: () -> Unit,
) {
    val (title, description) = when (accessState) {
        AdminAccessState.DENIED_GUEST -> "未登录" to "当前实例尚未登录，请先登录后再进入管理台"
        AdminAccessState.DENIED_NOT_ADMIN -> "无管理权限" to "当前账号不是管理员或无管理权限"
        else -> "无法进入管理台" to "发生未知错误，请稍后重试"
    }
    Scaffold(topBar = { AppTopBar(title = "管理台", onBack = onBack) }) { padding ->
        EmptyState(
            title = title,
            description = description,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * ALLOWED-only scaffold: top bar (shows current instance name per PRD §12.1
 * "避免跨实例误操作"), scrollable [AdminTabRow] (7 tabs), and the selected
 * tab's content. Only [AdminTab.OVERVIEW] has real content this Sprint; the
 * other 6 render [AdminComingSoon].
 */
@Composable
private fun AdminScaffold(
    instanceId: String,
    uiState: AdminUiState,
    onBack: () -> Unit,
    onSelectTab: (AdminTab) -> Unit,
    onLoadOverviewCards: () -> Unit,
    onRetryStorage: () -> Unit,
    onRetryTask: () -> Unit,
    onRetryIndex: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "管理台",
                subtitle = uiState.instanceInfo?.instanceName,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AdminTabRow(
                tabs = AdminTab.entries.map { it.label },
                selectedIndex = AdminTab.entries.indexOf(uiState.selectedTab),
                onTabSelected = { index -> onSelectTab(AdminTab.entries[index]) },
            )
            HorizontalDivider()
            when (uiState.selectedTab) {
                AdminTab.OVERVIEW -> AdminOverviewTab(
                    uiState = uiState,
                    onLoadOverviewCards = onLoadOverviewCards,
                    onRetryStorage = onRetryStorage,
                    onRetryTask = onRetryTask,
                    onRetryIndex = onRetryIndex,
                )
                AdminTab.USERS -> AdminUserTab(instanceId = instanceId)
                AdminTab.STORAGES -> AdminStorageTab(instanceId = instanceId)
                AdminTab.TASKS -> AdminTaskTab(instanceId = instanceId)
                else -> AdminComingSoon(tab = uiState.selectedTab)
            }
        }
    }
}

@Composable
private fun AdminComingSoon(tab: AdminTab) {
    EmptyState(
        title = "${tab.label} · 即将上线",
        modifier = Modifier.fillMaxSize(),
        description = "该功能计划在后续 Sprint 中提供",
    )
}
