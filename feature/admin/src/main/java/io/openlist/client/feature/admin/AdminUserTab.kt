package io.openlist.client.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminUserSummary

/**
 * Users Tab content (v0.5_EXECUTION_PLAN.md §11 S3-T2). Paginated list
 * (username/role/disabled badge/permission summary) + pull-to-refresh
 * (=forceRefresh) + three-state via [LoadingState]/[EmptyState]/[ErrorBar].
 * Tapping a row opens a read-only [AdminUserDetailSheet] -- no edit affordance
 * anywhere, matching [AdminUserSummary] having no writable/sensitive fields to
 * begin with (PRD §9.2 out-of-scope: create/update/delete/2FA-reset/password
 * reset).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserTab(
    instanceId: String,
    viewModel: AdminUserListViewModel = hiltViewModel(),
) {
    LaunchedEffect(instanceId) { viewModel.bind(instanceId) }
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
            uiState.errorMessage != null && uiState.users.isEmpty() -> EmptyState(
                title = "加载失败",
                description = uiState.errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
            uiState.users.isEmpty() -> EmptyState(
                title = "暂无用户",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                uiState.errorMessage?.let { message ->
                    ErrorBar(message = message, onRetry = { viewModel.refresh() })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        AdminUserRow(user = user, onClick = { viewModel.selectUser(user) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (viewModel.hasNextPage) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(
                                    onClick = { viewModel.loadNextPage() },
                                    enabled = !uiState.isLoadingMore,
                                ) {
                                    Text(if (uiState.isLoadingMore) "加载中…" else "加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedUser?.let { user ->
        AdminUserDetailSheet(user = user, onDismiss = { viewModel.dismissUserDetail() })
    }
}

@Composable
private fun AdminUserRow(user: AdminUserSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(user.username, style = MaterialTheme.typography.bodyLarge)
            StatusBadge(text = user.roleLabel, tone = if (user.role == 2) StatusTone.PRIMARY else StatusTone.NEUTRAL)
            if (user.disabled) StatusBadge(text = "已禁用", tone = StatusTone.ERROR)
        }
        Text(
            text = permissionSummary(user),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun permissionSummary(user: AdminUserSummary): String {
    val basePath = user.basePath?.takeIf { it.isNotBlank() } ?: "/"
    val permission = user.permission
    return if (permission == null) "基础路径 $basePath" else "基础路径 $basePath · 权限位 $permission"
}

/**
 * Read-only user detail bottom sheet (PRD §12.3 "用户详情只读页或底部 Sheet",
 * P-502 "详情用 BottomSheet 不加路由"). No project precedent for a purely
 * read-only detail sheet exists yet (existing sheets are all action menus/
 * forms) -- this is a plain static [Text]/[Row] layout inside [ModalBottomSheet],
 * deliberately with zero clickable/editable rows. Only ever fed an
 * [AdminUserSummary], which structurally cannot carry `password`/hash/salt/
 * otp-secret, so there is no separate "hide sensitive field" step needed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminUserDetailSheet(user: AdminUserSummary, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(user.username, style = MaterialTheme.typography.titleLarge)
                StatusBadge(text = user.roleLabel, tone = if (user.role == 2) StatusTone.PRIMARY else StatusTone.NEUTRAL)
            }
            DetailRow(label = "用户 ID", value = user.id.toString())
            DetailRow(label = "基础路径", value = user.basePath?.takeIf { it.isNotBlank() } ?: "/")
            DetailRow(label = "状态", value = if (user.disabled) "已禁用" else "已启用")
            DetailRow(label = "权限位", value = user.permission?.toString() ?: "未知")
            DetailRow(label = "双重验证", value = user.otpEnabled?.let { if (it) "已启用" else "未启用" } ?: "未知")
            androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
