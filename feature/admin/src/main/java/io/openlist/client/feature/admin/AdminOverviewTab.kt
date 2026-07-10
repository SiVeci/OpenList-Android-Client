package io.openlist.client.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.WebFallbackTarget

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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            AdminSummaryCard(
                title = "存储",
                icon = Icons.Outlined.Storage,
                state = uiState.storageSummary,
                onRetry = onRetryStorage,
                modifier = Modifier.weight(1f),
            ) { data ->
                SummaryValue(value = data.enabledCount.toString(), label = "启用存储")
                StatusBadge(text = "停用 ${data.disabledCount}", tone = StatusTone.NEUTRAL)
            }
            AdminSummaryCard(
                title = "任务",
                icon = Icons.Outlined.TaskAlt,
                state = uiState.taskSummary,
                onRetry = onRetryTask,
                modifier = Modifier.weight(1f),
            ) { data ->
                SummaryValue(value = data.runningCount.toString(), label = "运行中")
                StatusBadge(text = "后台任务", tone = if (data.runningCount > 0) StatusTone.RUNNING else StatusTone.NEUTRAL)
            }
        }
        AdminSummaryCard(
            title = "索引",
            icon = Icons.Outlined.ManageSearch,
            state = uiState.indexSummary,
            onRetry = onRetryIndex,
        ) { data ->
            SummaryValue(value = data.objCount.toString(), label = "索引对象")
            StatusBadge(
                text = if (data.isRunning) "运行中" else "已停止",
                tone = if (data.isRunning) StatusTone.RUNNING else StatusTone.NEUTRAL,
            )
        }
        AdminWebFallbackEntryCard(state = uiState.overviewWebFallback)
    }
}

@Composable
private fun AdminInstanceInfoCard(info: AdminInstanceInfo?) {
    OverviewCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("管理台概览", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = info?.instanceName ?: "加载中...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        if (info != null) {
            InfoRow(label = "实例地址", value = info.baseUrl)
            InfoRow(label = "管理员", value = info.adminUsername ?: "未知")
        }
    }
}

@Composable
private fun <T> AdminSummaryCard(
    title: String,
    icon: ImageVector,
    state: AdminCardState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    OverviewCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(Spacing.xs))
        when (state) {
            is AdminCardState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Spacing.xs))
            is AdminCardState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content(state.data) }
            is AdminCardState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
private fun AdminWebFallbackEntryCard(state: AdminCardState<WebFallbackTarget>) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var launchError by remember { mutableStateOf<String?>(null) }

    OverviewCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Web 管理台", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "浏览器兜底入口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.xs))
        when (state) {
            is AdminCardState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Spacing.xs))
            is AdminCardState.Failed -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is AdminCardState.Loaded -> {
                val target = state.data
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { launchError = openInExternalBrowser(context, target.url) }
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Links are link-blue per DESIGN.md button-link — never the purple CTA color.
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, tint = OpenListPalette.LinkBlue)
                    Text(
                        text = "在浏览器中打开",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenListPalette.LinkBlue,
                        modifier = Modifier.weight(1f),
                    )
                }
                launchError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(
                        modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(target.url)) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = OpenListPalette.LinkBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("复制链接", style = MaterialTheme.typography.bodySmall, color = OpenListPalette.LinkBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String) {
    Text(text = value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), content = content)
    }
}
