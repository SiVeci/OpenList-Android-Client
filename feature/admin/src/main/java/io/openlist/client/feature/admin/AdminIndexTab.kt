package io.openlist.client.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.DirectoryPickerSheet
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState

/**
 * Index Tab content (v0.5_EXECUTION_PLAN.md §11 S6-T2/T3, PRD §12.6/§8.5).
 * [IndexProgressPanel] shows the live state; four operation buttons (build/
 * update/stop/clear) each require a two-step confirm, with clear alone using
 * the danger style (PRD §8.5/§15.2 "清空索引...应使用危险操作样式" -- the
 * *only* index operation called out for danger styling; build/update/stop
 * stay normal-style, mirroring [AdminStorageTab]'s enable/reload-all vs.
 * disable precedent). No directory-picker for "更新索引" -- DEC-504 defaults
 * cover the only path v0.5 needs (`/`), the picker itself being the PRD's
 * explicitly-optional "时间允许再做" enhancement.
 *
 * Polling (S6-T3): started in [DisposableEffect]/stopped `onDispose`, keyed on
 * `instanceId`, independent of [AdminTaskTab]'s own polling lifecycle
 * (separate ViewModel, separate Job) -- mirrors that Tab's exact shape without
 * sharing code, per brief.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminIndexTab(
    instanceId: String,
    viewModel: AdminIndexListViewModel = hiltViewModel(),
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
        val progress = uiState.progress
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.errorMessage != null && progress == null -> EmptyState(
                title = "加载失败",
                description = uiState.errorMessage,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            progress == null -> EmptyState(
                title = "暂无索引信息",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    uiState.errorMessage?.let { message ->
                        ErrorBar(message = message, onRetry = { viewModel.refresh() })
                    }
                    uiState.pollErrorMessage?.let { message ->
                        ErrorBar(message = message)
                    }
                    IndexProgressPanel(progress = progress)
                    AdminUpdatePathRow(path = uiState.updatePath, onPickPath = viewModel::openPathPicker)
                    AdminIndexActionButtons(
                        onBuild = viewModel::requestBuild,
                        onUpdate = viewModel::requestUpdate,
                        onStop = viewModel::requestStop,
                        onClear = viewModel::requestClear,
                    )
                }
            }
        }
    }

    AdminIndexConfirmDialog(uiState = uiState, viewModel = viewModel)

    uiState.pickerPath?.let { pickerPath ->
        DirectoryPickerSheet(
            title = "选择更新索引的路径",
            breadcrumbSegments = listOf("根目录") + pickerPath.trim('/').split('/').filter { it.isNotEmpty() },
            content = uiState.pickerContent,
            onSegmentClick = { index -> viewModel.pickerNavigateToSegment(index) },
            onEnterDirectory = { entry -> viewModel.pickerEnterDirectory(entry) },
            onSelectCurrent = { viewModel.confirmPathPicker() },
            onRefresh = { viewModel.pickerRefresh() },
            onDismiss = { viewModel.dismissPathPicker() },
        )
    }
}

/** "更新索引" 的目标路径 + 选择入口（v1.0 S5-T1）。maxDepth 固定使用仓储默认值
 * "-1"（DEC-604/V-609 确认可靠，不提供 UI 控制），此处只说明其含义。 */
@Composable
private fun AdminUpdatePathRow(path: String, onPickPath: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("更新索引路径", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onPickPath) { Text("选择路径") }
        }
        Text(
            "深度：不限深度（默认覆盖所选路径下全部子目录）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminIndexActionButtons(
    onBuild: () -> Unit,
    onUpdate: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(onClick = onBuild, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("重建索引")
            }
            OutlinedButton(onClick = onUpdate, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("更新索引")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("停止索引")
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("清空索引")
            }
        }
    }
}

@Composable
private fun AdminIndexConfirmDialog(uiState: AdminIndexUiState, viewModel: AdminIndexListViewModel) {
    when (uiState.dialog) {
        AdminIndexDialog.BuildConfirm -> ConfirmDialog(
            title = "重建索引",
            message = "确定重建整个搜索索引吗？该操作将在后台进行，可能需要较长时间。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "重建",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        AdminIndexDialog.UpdateConfirm -> ConfirmDialog(
            title = "更新索引",
            message = "确定更新路径 “${uiState.updatePath}” 下的搜索索引吗？将不限深度覆盖其全部子目录，该操作将在后台进行。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "更新",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        AdminIndexDialog.StopConfirm -> ConfirmDialog(
            title = "停止索引",
            message = "确定停止当前正在进行的索引任务吗？",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "停止",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        AdminIndexDialog.ClearConfirm -> ConfirmDialog(
            title = "清空索引",
            message = "确定清空全部搜索索引吗？此操作不可撤销，清空后搜索功能将不可用，直至重新建立索引。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "清空",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        null -> Unit
    }
}
