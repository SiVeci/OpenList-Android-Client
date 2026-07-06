package io.openlist.client.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.TaskCard
import io.openlist.client.core.designsystem.components.TaskCardStatus
import io.openlist.client.core.model.AdminTask
import io.openlist.client.core.model.UnifiedTaskStatus

/**
 * Admin Tasks Tab content (v0.5_EXECUTION_PLAN.md §11 S5-T2/T3, PRD §12.5).
 * A filter-chip row picks one of the 7 backend types or "全部" (all types,
 * undone-only view -- brief allows either a filter-chip row or a dropdown;
 * chips are chosen here since [io.openlist.client.feature.search.SearchScreen]
 * already establishes `FilterChip` as this project's precedent for a small
 * fixed set of mutually-exclusive choices, and 7 types + "全部" fits on one
 * horizontally-scrolling row without needing a dropdown's extra tap). A
 * second row toggles undone/done (reusing the same `FilterChip` component,
 * not [io.openlist.client.core.designsystem.components.TaskTabRow]/
 * `AdminTabRow`, since this is a binary toggle nested *inside* one Tab's
 * content, not a peer set of top-level Tabs).
 *
 * Polling (S5-T3): started in [LaunchedEffect]/stopped in [DisposableEffect],
 * both keyed on `instanceId` so it restarts correctly if the instance changes
 * without the Tab itself leaving composition, and stopped unconditionally
 * `onDispose` (leaving the Tab/Composable's lifetime is the sole polling
 * gate -- independent of `:feature:task`'s own [io.openlist.client.feature
 * .task.TaskCenterScreen] polling lifecycle, per the module boundary this
 * Sprint's brief requires).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTaskTab(
    instanceId: String,
    viewModel: AdminTaskListViewModel = hiltViewModel(),
) {
    LaunchedEffect(instanceId) { viewModel.bind(instanceId) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(instanceId) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AdminTaskFilterRow(
                selectedType = uiState.selectedType,
                onTypeSelected = viewModel::selectType,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = uiState.bucket == AdminTaskBucket.UNDONE,
                    onClick = { viewModel.selectBucket(AdminTaskBucket.UNDONE) },
                    label = { Text("进行中") },
                )
                FilterChip(
                    selected = uiState.bucket == AdminTaskBucket.DONE,
                    onClick = { viewModel.selectBucket(AdminTaskBucket.DONE) },
                    label = { Text("已完成") },
                )
            }
            // Batch operations (v1.0 S5-T2/T3, DEC-603 subset A) only show
            // once a specific type is picked -- the backend endpoints are
            // per-type, there is no "all types" batch call to dispatch "全部" to.
            uiState.selectedType?.let { type ->
                AdminTaskBatchActionsRow(
                    typeLabel = type.label,
                    onClearDone = viewModel::requestClearDone,
                    onClearSucceeded = viewModel::requestClearSucceeded,
                    onRetryFailed = viewModel::requestRetryFailed,
                )
            }
            uiState.pollErrorMessage?.let { message ->
                ErrorBar(message = message, modifier = Modifier.fillMaxWidth())
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                val tasks = uiState.visibleTasks
                when {
                    uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                    uiState.errorMessage != null && tasks.isEmpty() -> EmptyState(
                        title = "加载失败",
                        description = uiState.errorMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                    tasks.isEmpty() -> EmptyState(
                        title = if (uiState.bucket == AdminTaskBucket.DONE) "暂无已完成任务" else "暂无进行中任务",
                        description = if (uiState.bucket == AdminTaskBucket.DONE && uiState.selectedType == null) {
                            "请先选择一个任务类型查看已完成记录"
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        uiState.errorMessage?.let { message ->
                            ErrorBar(message = message, onRetry = { viewModel.refresh() })
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            items(tasks, key = { "${it.taskType}:${it.id}" }) { task ->
                                AdminTaskRow(
                                    task = task,
                                    onCancel = { viewModel.requestCancel(task) },
                                    onRetry = { viewModel.requestRetry(task) },
                                    onDelete = { viewModel.requestDelete(task) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AdminTaskConfirmDialog(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun AdminTaskFilterRow(
    selectedType: AdminTaskType?,
    onTypeSelected: (AdminTaskType?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FilterChip(selected = selectedType == null, onClick = { onTypeSelected(null) }, label = { Text("全部") })
        AdminTaskType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.label) },
            )
        }
    }
}

/** Batch actions row (v1.0 S5-T2/T3): "清理已完成"/"清理已成功" use the danger
 * style (PRD §12.6/§11.3 "清理类用危险样式"), "重试全部失败" doesn't (retrying
 * is reversible/low-risk, same precedent as [io.openlist.client.feature.task
 * .TaskCenterViewModel.retryTask]'s no-confirmation choice — except here a
 * confirm is still required since it's a *batch*, not a single item). */
@Composable
private fun AdminTaskBatchActionsRow(
    typeLabel: String,
    onClearDone: () -> Unit,
    onClearSucceeded: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Text(
            "批量操作（$typeLabel）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            androidx.compose.material3.OutlinedButton(onClick = onClearDone) { Text("清理已完成") }
            androidx.compose.material3.OutlinedButton(onClick = onClearSucceeded) { Text("清理已成功") }
            androidx.compose.material3.OutlinedButton(onClick = onRetryFailed) { Text("重试全部失败") }
        }
    }
}

@Composable
private fun AdminTaskConfirmDialog(uiState: AdminTaskListUiState, viewModel: AdminTaskListViewModel) {
    when (val dialog = uiState.dialog) {
        is AdminTaskDialog.CancelConfirm -> ConfirmDialog(
            title = "取消任务",
            message = "确定取消「${dialog.task.name}」吗？",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "取消任务",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminTaskDialog.RetryConfirm -> ConfirmDialog(
            title = "重试任务",
            message = "确定重试「${dialog.task.name}」吗？",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "重试",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminTaskDialog.DeleteConfirm -> ConfirmDialog(
            title = "删除任务记录",
            message = "确定删除「${dialog.task.name}」的记录吗？此操作不可撤销。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "删除",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminTaskDialog.ClearDoneConfirm -> ConfirmDialog(
            title = "清理已完成记录",
            message = "确定清理「${dialog.type.label}」全部已完成的任务记录吗？此操作不可撤销，影响范围为该类型下的所有已完成记录（成功/取消/失败）。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "清理",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminTaskDialog.ClearSucceededConfirm -> ConfirmDialog(
            title = "清理已成功记录",
            message = "确定清理「${dialog.type.label}」全部已成功的任务记录吗？此操作不可撤销，影响范围为该类型下的所有已成功记录。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "清理",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminTaskDialog.RetryFailedConfirm -> ConfirmDialog(
            title = "重试全部失败任务",
            message = "确定重试「${dialog.type.label}」全部失败的任务吗？影响范围为该类型下当前所有失败状态的任务。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "重试",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        null -> Unit
    }
}

/**
 * One admin task row, built on the shared [TaskCard] (no thin wrapper needed
 * -- [TaskCard]'s existing `subtitle` slot already accommodates the
 * creator/type summary this Sprint needs, per S1's component-mapping
 * conclusion). Cancel/retry/delete buttons are pre-disabled by the
 * currently-*displayed* state as a UX nicety only (V-505/brief): the
 * repository call these buttons trigger is never itself gated by that same
 * guess, only the visual affordance is.
 */
@Composable
private fun AdminTaskRow(
    task: AdminTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val canCancel = !task.isDone && (task.state == UnifiedTaskStatus.RUNNING || task.state == UnifiedTaskStatus.PENDING)
    val canRetry = task.state == UnifiedTaskStatus.FAILED
    val canDelete = task.isDone

    TaskCard(
        icon = task.taskType.toIcon(),
        title = task.name,
        status = task.state.toCardStatus(),
        subtitle = listOfNotNull(task.creator?.let { "创建者 $it" }, task.statusText).joinToString("  ·  ").ifBlank { null },
        progress = task.progress,
        errorMessage = task.error,
        actions = {
            if (canRetry) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重试")
                }
            }
            if (canCancel) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = "取消")
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除记录")
                }
            }
        },
    )
}

private fun UnifiedTaskStatus.toCardStatus(): TaskCardStatus = when (this) {
    UnifiedTaskStatus.PENDING -> TaskCardStatus.PENDING
    UnifiedTaskStatus.RUNNING -> TaskCardStatus.RUNNING
    UnifiedTaskStatus.SUCCESS -> TaskCardStatus.SUCCESS
    UnifiedTaskStatus.FAILED -> TaskCardStatus.FAILED
    UnifiedTaskStatus.CANCELLED -> TaskCardStatus.CANCELLED
    UnifiedTaskStatus.UNKNOWN -> TaskCardStatus.UNKNOWN
}

private fun String.toIcon(): ImageVector = when (this) {
    "upload" -> Icons.Filled.UploadFile
    "copy" -> Icons.Filled.FileCopy
    "move" -> Icons.AutoMirrored.Filled.DriveFileMove
    "offline_download", "offline_download_transfer" -> Icons.Filled.CloudDownload
    else -> Icons.Filled.Task // decompress/decompress_upload -- no dedicated icon precedent elsewhere in the app
}
