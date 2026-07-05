package io.openlist.client.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.model.WebFallbackTarget

/**
 * Overview Tab content (v0.5_EXECUTION_PLAN.md §11 S2-T4). Instance-info
 * block is zero-request (already loaded by [AdminViewModel.loadInstanceInfo]
 * during gating); the three summary cards each own an independent
 * [AdminCardState] slice so one card's failure/loading never blocks the
 * others or the instance-info block from rendering (PRD §16.3.4/P-512) --
 * `AdminStorageRepository`/`AdminTaskRepository` were "lit up" with real data
 * in S4/S5, and `AdminIndexRepository` in S6 (this file itself needed no
 * changes for that -- see `AdminViewModel.refreshIndexSummary`, which already
 * called the real interface method starting S2).
 *
 * The Web-fallback entry card (S7-T4) is now real: it loads a
 * [WebFallbackTarget] the same way the Advanced tab's `WebFallbackCard` does
 * (shared [AdminUiState.overviewWebFallback] slice, loaded once per Tab visit
 * via [onLoadOverviewCards] -> [AdminViewModel.refreshOverviewWebFallback]),
 * but renders a lighter-weight single-button treatment here rather than that
 * card's full safety-note/copy-URL-fallback body -- this Overview entry is
 * meant as a quick jump-off point, not the primary interaction surface (that
 * lives on the Advanced tab).
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
        AdminWebFallbackEntryCard(state = uiState.overviewWebFallback)
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

/**
 * Lightweight Web-fallback entry (S7-T4, PRD §12.2) -- lit up from S2's
 * static/disabled-looking placeholder. Tapping the row (once loaded) launches
 * the same external-browser [openInExternalBrowser] Intent the Advanced tab's
 * full `WebFallbackCard` uses, with the same "打开失败 -> 复制链接" fallback,
 * just condensed into a single card here (no separate safety-note/
 * unsupported-capabilities body -- that full treatment is the Advanced tab's
 * job per the brief's "Overview has a lightweight entry, Advanced has the
 * full card" split).
 */
@Composable
private fun AdminWebFallbackEntryCard(state: AdminCardState<WebFallbackTarget>) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var launchError by remember { mutableStateOf<String?>(null) }

    AdminCardContainer {
        Text("Web 管理台兜底入口", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        when (state) {
            is AdminCardState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Spacing.xs))
            is AdminCardState.Failed -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is AdminCardState.Loaded -> {
                val target = state.data
                Text(
                    text = "在浏览器中打开 Web 管理台",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable {
                        launchError = openInExternalBrowser(context, target.url)
                    },
                )
                launchError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        text = "复制链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(target.url)) },
                    )
                }
            }
        }
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

