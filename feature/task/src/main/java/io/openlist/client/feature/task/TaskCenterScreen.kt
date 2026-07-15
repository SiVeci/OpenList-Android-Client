package io.openlist.client.feature.task

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListTheme
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.DirectoryPickerSheet
import io.openlist.client.core.designsystem.components.DirectionalContent
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.OfflineDownloadSheet
import io.openlist.client.core.designsystem.components.TaskTabRow
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.model.SystemDocumentRecoveryAction
import io.openlist.client.core.network.OpenListPathCodec
import kotlinx.coroutines.delay

private const val DRAFT_RETENTION_REFRESH_MS = 60_000L

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
    val exportDraftLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> viewModel.completeDraftExport(uri?.toString()) }

    LaunchedEffect(uiState.exportDraftTask?.id) {
        uiState.exportDraftTask?.let { exportDraftLauncher.launch(it.title.substringAfter(": ", it.title)) }
    }

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
            AppTopBar(
                title = "任务中心",
                onBack = onBack,
                bottomRow = {
                    TaskTabRow(
                        tabs = listOf("上传", "下载", "远程", "失败"),
                        selectedIndex = TaskTab.entries.indexOf(uiState.selectedTab),
                        onTabSelected = { index -> viewModel.selectTab(TaskTab.entries[index]) },
                    )
                },
            )
        },
        floatingActionButton = {
            // The screen's dominant CTA — the one place that carries the
            // signature purple (M3's default primaryContainer FAB does not).
            FloatingActionButton(
                onClick = { viewModel.openOfflineDownloadSheet() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
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
                // Snapshot per tab so the outgoing pane of the slide shows the
                // tab the user is leaving, not the shared live state (which has
                // already switched to the new tab's groups).
                val tabSnapshots = remember { mutableStateMapOf<Int, List<TaskGroup>>() }
                val selectedIndex = TaskTab.entries.indexOf(uiState.selectedTab)
                SideEffect {
                    if (tabSnapshots[selectedIndex] != uiState.taskGroups) {
                        tabSnapshots[selectedIndex] = uiState.taskGroups
                    }
                }
                DirectionalContent(
                    targetState = selectedIndex,
                    direction = { from, to -> to - from },
                    modifier = Modifier.fillMaxSize(),
                    label = "taskTabContent",
                ) { tabIndex ->
                    val groups = if (tabIndex == selectedIndex) uiState.taskGroups else tabSnapshots[tabIndex].orEmpty()
                    if (groups.isEmpty()) {
                        EmptyState(title = "暂无任务", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                                        onExportDraft = { viewModel.requestDraftExport(task) },
                                        onDeleteDraft = { viewModel.openDeleteDraftConfirm(task) },
                                        onOpenTarget = { viewModel.openTaskTarget(task, onOpenDirectory, onOpenFile) },
                                    )
                                }
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

    uiState.deleteDraftConfirmTask?.let { task ->
        ConfirmDialog(
            title = "删除草稿",
            message = "删除后无法恢复“${task.title}”的本地草稿。",
            onConfirm = viewModel::confirmDeleteDraft,
            onDismiss = viewModel::dismissDeleteDraftConfirm,
            confirmText = "删除草稿",
            danger = true,
            loading = uiState.deletingDraft,
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
    onExportDraft: () -> Unit,
    onDeleteDraft: () -> Unit,
    onOpenTarget: () -> Unit,
) {
    // v1.0: local downloads can now be cancelled too (v1.0_PRD §4.2.C.2) —
    // the LOCAL_DOWNLOAD exclusion that existed before TransferRepository had
    // a cancelDownload method is gone.
    val canCancel = task.status == UnifiedTaskStatus.RUNNING || task.status == UnifiedTaskStatus.PENDING
    // v1.0_PRD §4.2.C.1: only local uploads support retry.
    val canRetry = task.status == UnifiedTaskStatus.FAILED &&
        (task.source == io.openlist.client.core.model.TaskSource.LOCAL_UPLOAD ||
            SystemDocumentRecoveryAction.RETRY_SAVE in task.recoveryActions)
    val canExportDraft = SystemDocumentRecoveryAction.EXPORT_COPY in task.recoveryActions
    val canDeleteDraft = SystemDocumentRecoveryAction.DELETE_DRAFT in task.recoveryActions
    // Unchanged condition (S6-T4 DoD: "canOpenFolder"'s own gating logic is
    // not touched, only what a click on it does) -- still gates on
    // SUCCESS+non-null path; the icon's name/"跳转目录" label stays even
    // though S6-T4 may now open a file preview instead of a folder, since
    // deciding which of the two happens is now [TaskCenterViewModel.openTaskTarget]'s
    // job, resolved only once tapped (not known upfront without a network call).
    val canOpenFolder = task.status == UnifiedTaskStatus.SUCCESS && task.path != null
    val progress = task.progress

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canOpenFolder, onClick = onOpenTarget)
                .padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskIconTile(task = task)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (task.source == TaskSource.SYSTEM_DOCUMENT) {
                    SystemDraftContext(task)
                }
                task.path?.takeIf { task.source != TaskSource.SYSTEM_DOCUMENT }?.let { path ->
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.errorMessage?.takeIf { task.status == UnifiedTaskStatus.FAILED }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (task.status == UnifiedTaskStatus.RUNNING && progress != null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.xxs),
                    )
                }
            }
            TaskStatusAndActions(
                task = task,
                canOpenFolder = canOpenFolder,
                canRetry = canRetry,
                canCancel = canCancel,
                onOpenTarget = onOpenTarget,
                onRetry = onRetry,
                onCancel = onCancel,
                canExportDraft = canExportDraft,
                canDeleteDraft = canDeleteDraft,
                onExportDraft = onExportDraft,
                onDeleteDraft = onDeleteDraft,
            )
        }
    }
}

@Composable
private fun SystemDraftContext(task: UnifiedTask) {
    val remainingMillis by produceState(
        initialValue = task.expiresAt?.minus(System.currentTimeMillis()),
        key1 = task.expiresAt,
    ) {
        while (true) {
            value = task.expiresAt?.minus(System.currentTimeMillis())
            delay(DRAFT_RETENTION_REFRESH_MS)
        }
    }
    val context = buildList {
        task.instanceName?.takeIf { it.isNotBlank() }?.let { add("实例：$it") }
        task.directorySummary?.takeIf { it.isNotBlank() }?.let { add("目录：$it") }
        remainingMillis?.let { add(formatDraftRetention(it)) }
    }.joinToString(" · ")
    if (context.isNotBlank()) {
        Text(
            text = context,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatDraftRetention(remainingMillis: Long): String {
    if (remainingMillis <= 0) return "草稿即将清理"
    val totalMinutes = (remainingMillis + 59_999L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "草稿保留约 ${hours}小时${minutes}分钟"
        hours > 0 -> "草稿保留约 ${hours}小时"
        else -> "草稿保留约 ${minutes.coerceAtLeast(1)}分钟"
    }
}

@Composable
private fun TaskIconTile(task: UnifiedTask) {
    val tone = when (task.status) {
        UnifiedTaskStatus.FAILED -> MaterialTheme.colorScheme.error
        UnifiedTaskStatus.SUCCESS -> OpenListTheme.extendedColors.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.size(Spacing.sectionSm),
        shape = MaterialTheme.shapes.medium,
        color = tone.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = task.type.toIcon(),
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(Spacing.xl),
            )
        }
    }
}

@Composable
private fun TaskStatusAndActions(
    task: UnifiedTask,
    canOpenFolder: Boolean,
    canRetry: Boolean,
    canCancel: Boolean,
    canExportDraft: Boolean,
    canDeleteDraft: Boolean,
    onOpenTarget: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onExportDraft: () -> Unit,
    onDeleteDraft: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.statusLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = task.statusColor(),
        )
        if (canOpenFolder) {
            OutlinedButton(onClick = onOpenTarget) {
                Text("打开")
            }
        }
        if (canRetry) {
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
        }
        if (canExportDraft) {
            OutlinedButton(onClick = onExportDraft) { Text("恢复副本") }
        }
        if (canDeleteDraft) {
            OutlinedButton(onClick = onDeleteDraft) { Text("删除草稿") }
        }
        if (canCancel) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消")
            }
        } else if (task.status == UnifiedTaskStatus.SUCCESS || task.status == UnifiedTaskStatus.FAILED) {
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnifiedTask.statusColor() = when (status) {
    UnifiedTaskStatus.FAILED -> MaterialTheme.colorScheme.error
    UnifiedTaskStatus.SUCCESS -> OpenListTheme.extendedColors.success
    // Progress/activity keeps the single small primary accent; waiting is the
    // semantic warning orange (matches the PENDING badge).
    UnifiedTaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
    UnifiedTaskStatus.PENDING -> OpenListTheme.extendedColors.warning
    UnifiedTaskStatus.CANCELLED, UnifiedTaskStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun UnifiedTask.statusLabel(): String = when (status) {
    UnifiedTaskStatus.PENDING -> "等待中"
    UnifiedTaskStatus.RUNNING -> progress?.let { "$it%" } ?: "运行中"
    UnifiedTaskStatus.SUCCESS -> "已完成"
    UnifiedTaskStatus.FAILED -> "失败"
    UnifiedTaskStatus.CANCELLED -> "已取消"
    UnifiedTaskStatus.UNKNOWN -> "未知"
}

private fun TaskType.toIcon(): ImageVector = when (this) {
    TaskType.UPLOAD -> Icons.Filled.UploadFile
    TaskType.DOWNLOAD -> Icons.Filled.Download
    TaskType.OFFLINE_DOWNLOAD -> Icons.Filled.CloudDownload
    TaskType.COPY -> Icons.Filled.FileCopy
    TaskType.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
    TaskType.INDEX, TaskType.EXTRACT, TaskType.SYSTEM_SAVE, TaskType.UNKNOWN -> Icons.Filled.Task
}
