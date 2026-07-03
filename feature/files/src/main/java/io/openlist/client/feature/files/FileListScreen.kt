package io.openlist.client.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.Breadcrumb
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.DirectoryPickerSheet
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.FileActionItem
import io.openlist.client.core.designsystem.components.FileActionSheet
import io.openlist.client.core.designsystem.components.ListRowItem
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.TextInputDialog
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.network.OpenListPathCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    onOpenFileDetail: (path: String) -> Unit,
    onBackToInstances: () -> Unit,
    viewModel: FileListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = uiState.currentPath != "/") {
        viewModel.navigateToParent()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    title = uiState.instanceName,
                    onBack = onBackToInstances,
                    actions = {
                        if (uiState.canWrite) {
                            IconButton(onClick = { viewModel.openNewFolderDialog() }) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建目录")
                            }
                        }
                    },
                )
                Breadcrumb(
                    segments = listOf("根目录") + OpenListPathCodec.segments(uiState.currentPath),
                    onSegmentClick = { index -> viewModel.navigateToSegmentCount(index) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.fromCache) {
                Text(
                    text = "当前为本地缓存数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            uiState.errorMessage?.let { message ->
                ErrorBar(message = message, onRetry = { viewModel.refresh() })
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                    uiState.nodes.isEmpty() -> EmptyState(
                        title = "此目录为空",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.nodes, key = { it.path }) { node ->
                            ListRowItem(
                                name = node.name,
                                isDir = node.isDir,
                                sizeText = if (node.isDir) null else formatSize(node.size),
                                modifiedText = node.modifiedAt?.let(::formatDate),
                                onClick = {
                                    if (node.isDir) viewModel.navigateTo(node.path) else onOpenFileDetail(node.path)
                                },
                                trailing = {
                                    IconButton(onClick = { viewModel.openActionSheet(node) }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    uiState.actionSheetTarget?.let { node ->
        FileActionSheet(
            actions = buildFileActions(
                node = node,
                canWrite = uiState.canWrite,
                onOpenDetail = { onOpenFileDetail(node.path) },
                onRename = { viewModel.openRenameDialog(node) },
                onMove = { viewModel.openMovePicker(node) },
                onCopy = { viewModel.openCopyPicker(node) },
                onDelete = { viewModel.openDeleteConfirm(node) },
                onCopyPath = { clipboardManager.setText(AnnotatedString(node.path)) },
                onCopyName = { clipboardManager.setText(AnnotatedString(node.name)) },
            ),
            onDismiss = { viewModel.dismissActionSheet() },
        )
    }

    uiState.directoryPicker?.let { picker ->
        DirectoryPickerSheet(
            title = if (picker.purpose == DirectoryPickerPurpose.MOVE) "移动到" else "复制到",
            breadcrumbSegments = listOf("根目录") + OpenListPathCodec.segments(picker.currentPath),
            content = picker.content,
            onSegmentClick = { index -> viewModel.directoryPickerNavigateToSegment(index) },
            onEnterDirectory = { entry -> viewModel.directoryPickerEnter(entry) },
            onSelectCurrent = { viewModel.confirmDirectoryPicker() },
            onRefresh = { viewModel.directoryPickerRefresh() },
            onDismiss = { viewModel.dismissDirectoryPicker() },
            selecting = picker.isSubmitting,
        )
    }

    when (val dialog = uiState.dialog) {
        FileListDialog.NewFolder -> TextInputDialog(
            title = "新建目录",
            value = uiState.dialogInputValue,
            onValueChange = viewModel::onDialogValueChange,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            label = "目录名称",
            confirmEnabled = uiState.dialogInputValue.isNotBlank(),
            errorMessage = uiState.dialogError,
            loading = uiState.dialogLoading,
        )
        is FileListDialog.Rename -> TextInputDialog(
            title = "重命名",
            value = uiState.dialogInputValue,
            onValueChange = viewModel::onDialogValueChange,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            label = "名称",
            confirmEnabled = uiState.dialogInputValue.isNotBlank(),
            errorMessage = uiState.dialogError,
            loading = uiState.dialogLoading,
        )
        is FileListDialog.DeleteConfirm -> ConfirmDialog(
            title = "删除",
            message = if (dialog.node.isDir) {
                "确定删除目录「${dialog.node.name}」及其内容吗？此操作可能无法撤销。"
            } else {
                "确定删除「${dialog.node.name}」吗？此操作可能无法撤销。"
            },
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "删除",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        null -> Unit
    }
}

/** "下载" and "详情" both route to the file detail screen (v0.1's existing
 * download button lives there) rather than duplicating the download call
 * here — v0.1_EXECUTION_PLAN.md's download flow is reused as-is, not re-wired. */
private fun buildFileActions(
    node: FileNode,
    canWrite: Boolean,
    onOpenDetail: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyName: () -> Unit,
): List<FileActionItem> = buildList {
    if (!node.isDir) {
        add(FileActionItem(label = "下载", icon = Icons.Outlined.Download, onClick = onOpenDetail))
    }
    add(FileActionItem(label = "详情", icon = Icons.Filled.Info, onClick = onOpenDetail))
    if (canWrite) {
        add(FileActionItem(label = "重命名", icon = Icons.Filled.DriveFileRenameOutline, onClick = onRename))
        add(FileActionItem(label = "移动", icon = Icons.AutoMirrored.Filled.DriveFileMove, onClick = onMove))
        add(FileActionItem(label = "复制", icon = Icons.Filled.FileCopy, onClick = onCopy))
    }
    add(FileActionItem(label = "复制路径", icon = Icons.Filled.ContentCopy, onClick = onCopyPath))
    add(FileActionItem(label = "复制名称", icon = Icons.Filled.ContentCopy, onClick = onCopyName))
    if (canWrite) {
        add(FileActionItem(label = "删除", icon = Icons.Filled.Delete, onClick = onDelete, danger = true))
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroup)
    return if (digitGroup == 0) "$bytes ${units[0]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroup])
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
