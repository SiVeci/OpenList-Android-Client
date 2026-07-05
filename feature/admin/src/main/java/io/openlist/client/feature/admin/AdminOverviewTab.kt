package io.openlist.client.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing

/**
 * Overview Tab content (v0.5_EXECUTION_PLAN.md §11 S2-T4). Instance-info
 * block is zero-request (already loaded by [AdminViewModel.loadInstanceInfo]
 * during gating); the three summary cards each own an independent
 * [AdminCardState] slice so one card's failure/loading never blocks the
 * others or the instance-info block from rendering (PRD §16.3.4/P-512) --
 * `AdminStorageRepository`/`AdminTaskRepository`/`AdminIndexRepository` are
 * still S1 stubs, so all three legitimately show their error state this
 * Sprint (S4/S5/S6 "light up" real data later).
 */
@Composable
fun AdminOverviewTab(
    uiState: AdminUiState,
    onLoadOverviewCards: () -> Unit,
    onRetryStorage: () -> Unit,
    onRetryTask: () -> Unit,
    onRetryIndex: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoadOverviewCards() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AdminInstanceInfoCard(uiState.instanceInfo)
        AdminSummaryCard(
            title = "存储",
            state = uiState.storageSummary,
            onRetry = onRetryStorage,
        ) { data -> Text("启用 ${data.enabledCount} · 禁用 ${data.disabledCount}", style = MaterialTheme.typography.bodyMedium) }
        AdminSummaryCard(
            title = "任务",
            state = uiState.taskSummary,
            onRetry = onRetryTask,
        ) { data -> Text("运行中 ${data.runningCount}", style = MaterialTheme.typography.bodyMedium) }
        AdminSummaryCard(
            title = "索引",
            state = uiState.indexSummary,
            onRetry = onRetryIndex,
        ) { data ->
            Text(
                text = if (data.isRunning) "运行中 · 对象数 ${data.objCount}" else "已停止 · 对象数 ${data.objCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AdminWebFallbackPlaceholderCard()
    }
}

@Composable
private fun AdminInstanceInfoCard(info: AdminInstanceInfo?) {
    AdminCardContainer {
        Text("实例信息", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        if (info == null) {
            Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(info.instanceName, style = MaterialTheme.typography.bodyMedium)
            Text(info.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "管理员：${info.adminUsername ?: "未知"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> AdminSummaryCard(
    title: String,
    state: AdminCardState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    AdminCardContainer {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        when (state) {
            is AdminCardState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Spacing.xs))
            is AdminCardState.Loaded -> content(state.data)
            is AdminCardState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

/** Static placeholder card (S7 wires this to `AdminWebFallbackRepository`) --
 * intentionally not clickable/enabled this Sprint. */
@Composable
private fun AdminWebFallbackPlaceholderCard() {
    AdminCardContainer {
        Text("Web 管理台兜底入口", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        Text(
            text = "即将上线，敬请期待",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), content = content)
    }
}
