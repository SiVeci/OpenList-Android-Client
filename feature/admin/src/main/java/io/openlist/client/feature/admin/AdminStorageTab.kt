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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.AdminStorageStatus
import io.openlist.client.core.model.AdminStorageSummary

/**
 * Storage Tab content, read-only part only (v0.5_EXECUTION_PLAN.md §11 S3-T4)
 * -- enable/disable/reload-all are S4 scope, and are rendered here as
 * visually-disabled placeholder buttons only (per the S3 brief: "render
 * disabled/greyed-out buttons as a visual preview if trivial ... just don't
 * wire them to anything real yet"). List: mount path/driver/status badge/
 * remark. Detail sheet: full fields + mountDetails in its own sub-section
 * with an independent "unavailable" state (PRD §9.3.3) -- since [getStorage]
 * (interface) already returns the whole [AdminStorageSummary] in one call,
 * "independent loading" here just means the mountDetails sub-section shows
 * "详情不可用" gracefully when null, not a second network round-trip/spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStorageTab(
    instanceId: String,
    viewModel: AdminStorageListViewModel = hiltViewModel(),
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
                    items(uiState.storages, key = { it.id }) { storage ->
                        AdminStorageRow(storage = storage, onClick = { viewModel.selectStorage(storage) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    uiState.selectedStorage?.let { storage ->
        AdminStorageDetailSheet(storage = storage, onDismiss = { viewModel.dismissStorageDetail() })
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
 * Read-only storage detail bottom sheet (PRD §12.3/§9.3.3, P-502). The
 * enable/disable/"reload all" buttons here are intentionally disabled visual
 * previews only (S4 wires the real Repository calls) -- clicking them does
 * nothing this Sprint, and they carry no `onClick` side effect at all so
 * there's no risk of silently no-op'ing a user's real intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminStorageDetailSheet(storage: AdminStorageSummary, onDismiss: () -> Unit) {
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
            Text(
                text = "启用/禁用/重新加载将在后续版本提供",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(if (storage.disabled) "启用" else "禁用")
                }
            }
            Spacer(Modifier.size(Spacing.xs))
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
