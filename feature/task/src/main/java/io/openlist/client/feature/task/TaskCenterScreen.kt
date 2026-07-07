package io.openlist.client.feature.task

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.DirectoryPickerSheet
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.OfflineDownloadSheet
import io.openlist.client.core.designsystem.components.TaskCard
import io.openlist.client.core.designsystem.components.TaskCardStatus
import io.openlist.client.core.designsystem.components.TaskTabRow
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.OpenListPathCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCenterScreen(
    onBack: () -> Unit,
    onOpenDirectory: (path: String) -> Unit,
    onOpenFile: (path: String) -> Unit,
    viewModel: TaskCenterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // P9's other half: refreshes local download status the moment
    // DownloadManager finishes one, on top of the 4s remote poll — registered
    // only while this screen is on screen, unregistered on leaving it.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                viewModel.refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissSnackbar()
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = "任务中心", onBack = onBack)
                TaskTabRow(
                    tabs = listOf("上传", "下载", "远程", "失败"),
                    selectedIndex = TaskTab.entries.indexOf(uiState.selectedTab),
                    onTabSelected = { index -> viewModel.selectTab(TaskTab.entries[index]) },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openOfflineDownloadSheet() }) {
                Icon(Icons.Filled.Add, contentDescription = "新增离线下载")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            uiState.remoteErrorMessage?.let { message ->
                ErrorBar(message = message, onRetry = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth())
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                val groups = uiState.taskGroups
                if (groups.isEmpty()) {
                    EmptyState(title = "暂无任务", modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        item {
                            TaskSummaryStrip(summary = uiState.summary)
                        }
                        groups.forEach { group ->
                            item(key = "group-${group.type}") {
                                TaskGroupHeader(
                                    title = group.title,
                                    actionLabel = group.type.actionLabel(),
                                    onAction = { viewModel.openGroupActionConfirm(group.type) },
                                )
                            }
                            items(group.tasks, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    onCancel = { viewModel.openCancelConfirm(task) },
                                    onRetry = { viewModel.retryTask(task) },
                                    onOpenTarget = { viewModel.openTaskTarget(task, onOpenDirectory, onOpenFile) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.cancelConfirmTarget?.let { task ->
        ConfirmDialog(
            title = "取消任务",
            message = "确定取消「${task.title}」吗？",
            onConfirm = { viewModel.confirmCancel() },
            onDismiss = { viewModel.dismissCancelConfirm() },
            confirmText = "取消任务",
            danger = true,
            loading = uiState.cancelling,
        )
    }

    uiState.groupActionConfirm?.let { confirm ->
        ConfirmDialog(
            title = confirm.dialogTitle(),
            message = confirm.dialogMessage(),
            onConfirm = { viewModel.confirmGroupAction() },
            onDismiss = { viewModel.dismissGroupActionConfirm() },
            confirmText = confirm.confirmText(),
            danger = confirm.type != TaskGroupActionType.CLEAR_FINISHED,
            loading = uiState.groupActionLoading,
        )
    }

    uiState.offlineDownload?.let { offlineState ->
        OfflineDownloadSheet(
            url = offlineState.url,
            onUrlChange = viewModel::updateOfflineDownloadUrl,
            targetDirText = offlineState.targetDir,
            onPickDirectory = { viewModel.openDirectoryPicker() },
            tools = offlineState.tools,
            selectedTool = offlineState.selectedTool,
            onToolSelected = viewModel::updateOfflineDownloadTool,
            onDismiss = { viewModel.dismissOfflineDownloadSheet() },
            onSubmit = { viewModel.submitOfflineDownload() },
            submitting = offlineState.submitting,
            errorMessage = offlineState.errorMessage,
        )

        offlineState.pickerPath?.let { pickerPath ->
            DirectoryPickerSheet(
                title = "选择保存目录",
                breadcrumbSegments = listOf("根目录") + OpenListPathCodec.segments(pickerPath),
                content = offlineState.pickerContent,
                onSegmentClick = { index -> viewModel.directoryPickerNavigateToSegment(index) },
                onEnterDirectory = { entry -> viewModel.directoryPickerEnter(entry) },
                onSelectCurrent = { viewModel.confirmDirectoryPicker() },
                onRefresh = { viewModel.directoryPickerRefresh() },
                onDismiss = { viewModel.dismissDirectoryPicker() },
            )
        }
    }
}

@Composable
private fun TaskSummaryStrip(summary: TaskSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TaskSummaryItem(label = "运行中", value = summary.activeCount)
            TaskSummaryDivider()
            TaskSummaryItem(label = "失败", value = summary.failedCount)
            TaskSummaryDivider()
            TaskSummaryItem(label = "已完成", value = summary.completedCount)
        }
    }
}

@Composable
private fun TaskSummaryItem(label: String, value: Int) {
    Text(
        text = "$value $label",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TaskSummaryDivider() {
    Text(
        text = "|",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun TaskGroupHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun TaskGroupType.actionLabel(): String = when (this) {
    TaskGroupType.RUNNING -> "全部取消"
    TaskGroupType.FAILED -> "清除失败"
    TaskGroupType.COMPLETED -> "清除已完成"
}

private fun TaskGroupActionConfirm.dialogTitle(): String = when (type) {
    TaskGroupActionType.CANCEL_ACTIVE -> "取消运行中任务"
    TaskGroupActionType.CLEAR_FAILED -> "清除失败任务"
    TaskGroupActionType.CLEAR_FINISHED -> "清除已完成任务"
}

private fun TaskGroupActionConfirm.dialogMessage(): String = when (type) {
    TaskGroupActionType.CANCEL_ACTIVE -> "确定取消当前分组中的 $count 个运行中任务吗？"
    TaskGroupActionType.CLEAR_FAILED -> "确定清除当前范围内的失败任务吗？"
    TaskGroupActionType.CLEAR_FINISHED -> "确定清除当前范围内的已完成任务吗？"
}

private fun TaskGroupActionConfirm.confirmText(): String = when (type) {
    TaskGroupActionType.CANCEL_ACTIVE -> "全部取消"
    TaskGroupActionType.CLEAR_FAILED -> "清除失败"
    TaskGroupActionType.CLEAR_FINISHED -> "清除已完成"
}

@Composable
private fun TaskRow(
    task: UnifiedTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenTarget: () -> Unit,
) {
    val cardStatus = task.status.toCardStatus()
    // v1.0: local downloads can now be cancelled too (v1.0_PRD §4.2.C.2) —
    // the LOCAL_DOWNLOAD exclusion that existed before TransferRepository had
    // a cancelDownload method is gone.
    val canCancel = task.status == UnifiedTaskStatus.RUNNING || task.status == UnifiedTaskStatus.PENDING
    // v1.0_PRD §4.2.C.1: only local uploads support retry.
    val canRetry = task.status == UnifiedTaskStatus.FAILED &&
        task.source == io.openlist.client.core.model.TaskSource.LOCAL_UPLOAD
    // Unchanged condition (S6-T4 DoD: "canOpenFolder"'s own gating logic is
    // not touched, only what a click on it does) -- still gates on
    // SUCCESS+non-null path; the icon's name/"跳转目录" label stays even
    // though S6-T4 may now open a file preview instead of a folder, since
    // deciding which of the two happens is now [TaskCenterViewModel.openTaskTarget]'s
    // job, resolved only once tapped (not known upfront without a network call).
    val canOpenFolder = task.status == UnifiedTaskStatus.SUCCESS && task.path != null

    TaskCard(
        icon = task.type.toIcon(),
        title = task.title,
        status = cardStatus,
        subtitle = task.path,
        progress = task.progress?.let { it / 100f },
        errorMessage = task.errorMessage,
        onClick = if (canOpenFolder) onOpenTarget else null,
        actions = {
            if (canOpenFolder) {
                IconButton(onClick = onOpenTarget) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "跳转目录")
                }
            }
            if (canRetry) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重试")
                }
            }
            if (canCancel) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "取消")
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

private fun TaskType.toIcon(): ImageVector = when (this) {
    TaskType.UPLOAD -> Icons.Filled.UploadFile
    TaskType.DOWNLOAD -> Icons.Filled.Download
    TaskType.OFFLINE_DOWNLOAD -> Icons.Filled.CloudDownload
    TaskType.COPY -> Icons.Filled.FileCopy
    TaskType.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
    TaskType.INDEX, TaskType.EXTRACT, TaskType.UNKNOWN -> Icons.Filled.Task
}
