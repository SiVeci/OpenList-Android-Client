package io.openlist.client.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminStorageStatus
import io.openlist.client.core.model.AdminStorageSummary

/**
 * Storage Tab content (v0.5_EXECUTION_PLAN.md §11 S3-T4/S4-T3/S4-T4).
 * List: mount path/driver/status badge/remark, plus a "重新加载全部存储" entry
 * at the top (PRD §12.4.7). Detail sheet: full fields + mountDetails
 * sub-section + real enable/disable actions (S4, replacing S3's disabled
 * visual placeholders) + a "查看驱动信息" row (PRD §12.4.8) opening a second,
 * independent bottom sheet with the dynamic driver config rendered as plain
 * key/value text rows (no rich renderer, per PRD's "结构复杂时只展示驱动名+关键摘要"
 * fallback).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStorageTab(
    instanceId: String,
    viewModel: AdminStorageListViewModel = hiltViewModel(),
) {
    LaunchedEffect(instanceId) { viewModel.bind(instanceId) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.errorMessage != null && uiState.storages.isEmpty() -> EmptyState(
                    title = "加载失败",
                    description = uiState.errorMessage,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.storages.isEmpty() -> EmptyState(
                    title = "暂无存储",
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
                        item {
                            ReloadAllRow(onClick = viewModel::requestReloadAll)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        items(uiState.storages, key = { it.id }) { storage ->
                            AdminStorageRow(storage = storage, onClick = { viewModel.selectStorage(storage) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    uiState.selectedStorage?.let { storage ->
        AdminStorageDetailSheet(
            storage = storage,
            onDismiss = { viewModel.dismissStorageDetail() },
            onEnable = { viewModel.requestEnable(storage) },
            onDisable = { viewModel.requestDisable(storage) },
            onViewDriverInfo = { viewModel.viewDriverInfo(storage.driver) },
        )
    }

    uiState.driverInfo?.let { driverInfo ->
        AdminDriverInfoSheet(driverInfo = driverInfo, onDismiss = { viewModel.dismissDriverInfo() })
    }

    AdminStorageConfirmDialog(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun ReloadAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text("重新加载全部存储", style = MaterialTheme.typography.bodyLarge, color = OpenListPalette.LinkBlue)
    }
}

@Composable
private fun AdminStorageConfirmDialog(uiState: AdminStorageListUiState, viewModel: AdminStorageListViewModel) {
    when (val dialog = uiState.dialog) {
        is AdminStorageDialog.EnableConfirm -> ConfirmDialog(
            title = "启用存储",
            message = "确定启用「${dialog.storage.mountPath}」吗？",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "启用",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        is AdminStorageDialog.DisableConfirm -> ConfirmDialog(
            title = "禁用存储",
            message = "确定禁用「${dialog.storage.mountPath}」吗？禁用后该挂载路径下的文件将暂时不可访问。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "禁用",
            danger = true,
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        AdminStorageDialog.ReloadAllConfirm -> ConfirmDialog(
            title = "重新加载全部存储",
            message = "确定重新加载全部存储吗？该操作将在后台进行，可能需要一些时间。",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            confirmText = "重新加载",
            loading = uiState.dialogLoading,
            errorMessage = uiState.dialogError,
        )
        null -> Unit
    }
}

@Composable
private fun AdminStorageRow(storage: AdminStorageSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(storage.mountPath, style = MaterialTheme.typography.bodyLarge)
            StatusBadge(text = storage.status.label(), tone = storage.status.tone())
        }
        Text(
            text = listOfNotNull(storage.driver, storage.remark?.takeIf { it.isNotBlank() }).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Storage detail bottom sheet (PRD §12.3/§9.3.3, P-502). Enable/disable now
 * call real [onEnable]/[onDisable] callbacks (S4, replacing S3's disabled
 * visual placeholders) -- the actual confirm dialog/network call lives in
 * [AdminStorageListViewModel], this composable only opens the request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminStorageDetailSheet(
    storage: AdminStorageSummary,
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onViewDriverInfo: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(storage.mountPath, style = MaterialTheme.typography.titleLarge)
                StatusBadge(text = storage.status.label(), tone = storage.status.tone())
            }
            DetailRow(label = "驱动", value = storage.driver)
            DetailRow(label = "排序", value = storage.order?.toString() ?: "未知")
            DetailRow(label = "备注", value = storage.remark?.takeIf { it.isNotBlank() } ?: "无")
            DetailRow(label = "状态", value = if (storage.disabled) "已禁用" else "已启用")

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("挂载详情", style = MaterialTheme.typography.titleSmall)
            val details = storage.mountDetails
            if (details == null) {
                Text(
                    text = "详情不可用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DetailRow(label = "总容量", value = formatBytes(details.totalSpace))
                DetailRow(label = "已用容量", value = formatBytes(details.usedSpace))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onViewDriverInfo),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("查看驱动信息", style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                if (storage.disabled) {
                    Button(onClick = onEnable, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Text("启用")
                    }
                } else {
                    OutlinedButton(onClick = onDisable, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Text("禁用")
                    }
                }
            }
            Spacer(Modifier.size(Spacing.xs))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Read-only driver-info sheet (PRD §12.4.8, S4-T4) -- dynamic key/value map
 * rendered as plain text rows, no structured parsing (PRD "结构复杂时只展示驱动名+
 * 关键摘要"). Loading/failed states mirror [AdminCardState] the same way the
 * Overview Tab's summary cards do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDriverInfoSheet(driverInfo: AdminDriverInfoUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(driverInfo.driverName, style = MaterialTheme.typography.titleLarge)
            when (val state = driverInfo.state) {
                is AdminCardState.Loading -> LoadingState(modifier = Modifier.fillMaxWidth())
                is AdminCardState.Failed -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is AdminCardState.Loaded -> if (state.data.isEmpty()) {
                    Text(
                        text = "无附加信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.data.forEach { (key, value) ->
                        DetailRow(label = key, value = value?.toString() ?: "-")
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
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

private fun AdminStorageStatus.label(): String = when (this) {
    AdminStorageStatus.ENABLED -> "运行中"
    AdminStorageStatus.DISABLED -> "已禁用"
    AdminStorageStatus.ERROR -> "异常"
    AdminStorageStatus.UNKNOWN -> "未知"
}

private fun AdminStorageStatus.tone(): StatusTone = when (this) {
    AdminStorageStatus.ENABLED -> StatusTone.SUCCESS
    AdminStorageStatus.DISABLED -> StatusTone.NEUTRAL
    AdminStorageStatus.ERROR -> StatusTone.ERROR
    AdminStorageStatus.UNKNOWN -> StatusTone.NEUTRAL
}

/** Local formatter -- no shared byte-formatting utility exists in
 * core:designsystem/core:common (each of feature/files, feature/search
 * currently duplicates its own private copy), so this follows that same
 * existing precedent rather than introducing a new shared util as an
 * unrelated drive-by change. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}
