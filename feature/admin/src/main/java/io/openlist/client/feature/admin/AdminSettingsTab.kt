package io.openlist.client.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.model.AdminSettingItem
import io.openlist.client.core.model.WebFallbackTarget

/**
 * Settings Tab content (v0.5_EXECUTION_PLAN.md §11 S7-T2, PRD §12.7).
 * Read-only list of `AdminSettingItem`, grouped by [AdminSettingItem.group]
 * with a simple section-header-per-group layout (brief allows either
 * grouping or a filter-chip row; grouping is chosen since settings are
 * naturally many-and-flat rather than a small mutually-exclusive choice set
 * like [AdminTaskTab]'s type filter). A two-way [FilterChip] toggle at the
 * top switches between the live settings list and the read-only defaults
 * list (PRD §12.7 "默认设置列表"). Private values render as a masked
 * placeholder and never the real value, even transiently -- masking already
 * happened at the repository layer ([AdminSettingItem.value] is `null`
 * whenever [AdminSettingItem.isPrivate] is `true`), so this Tab has nothing
 * to redact itself, only to render a placeholder string when [AdminSettingItem
 * .value] is `null` for a private item.
 *
 * No save/delete/reset-token affordance exists anywhere on this screen (PRD
 * §12.7 "不提供原生保存、删除、重置 Token") -- there is no editable field, no
 * button that could trigger a write call.
 *
 * A "在 Web 端编辑" fallback row sits at the bottom of the list -- it loads its
 * own [WebFallbackTarget] via [onLoadWebFallback] (fed [webFallback] by the
 * caller, same [AdminCardState] the Advanced tab's [WebFallbackCard] renders)
 * and, once loaded, launches the same [openInExternalBrowser] external-browser
 * Intent that card uses, independently of it (this Tab doesn't render
 * [WebFallbackCard] itself -- a lighter single-row treatment fits better at
 * the bottom of a settings list, matching the Overview tab's "lightweight
 * entry" vs. the Advanced tab's "full card" split called out in the brief).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsTab(
    instanceId: String,
    webFallback: AdminCardState<WebFallbackTarget>,
    onLoadWebFallback: () -> Unit,
    viewModel: AdminSettingsListViewModel = hiltViewModel(),
) {
    LaunchedEffect(instanceId) { viewModel.bind(instanceId) }
    LaunchedEffect(Unit) { onLoadWebFallback() }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = uiState.view == AdminSettingsView.CURRENT,
                onClick = { viewModel.selectView(AdminSettingsView.CURRENT) },
                label = { Text("当前设置") },
            )
            FilterChip(
                selected = uiState.view == AdminSettingsView.DEFAULT,
                onClick = { viewModel.selectView(AdminSettingsView.DEFAULT) },
                label = { Text("默认设置") },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.errorMessage != null && uiState.items.isEmpty() -> EmptyState(
                    title = "加载失败",
                    description = uiState.errorMessage,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.items.isEmpty() -> EmptyState(
                    title = "暂无设置项",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    uiState.errorMessage?.let { message ->
                        ErrorBar(message = message, onRetry = { viewModel.refresh() })
                    }
                    val grouped = uiState.items.groupBy { it.group }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        grouped.toSortedMap(compareBy { it ?: Int.MAX_VALUE }).forEach { (group, items) ->
                            item {
                                Text(
                                    text = groupLabel(group),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                )
                            }
                            items(items, key = { "${it.group}:${it.key}" }) { item ->
                                AdminSettingRow(item = item)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        item {
                            AdminWebFallbackRow(webFallback = webFallback)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSettingRow(item: AdminSettingItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(item.key, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (item.isPrivate) MASKED_VALUE else item.value?.takeIf { it.isNotBlank() } ?: "（空）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "在 Web 端编辑" row (PRD §12.7): once [webFallback] resolves, tapping the
 * row launches the same [openInExternalBrowser] Intent [WebFallbackCard]
 * uses; on [android.content.ActivityNotFoundException] it shows an inline
 * error plus a "复制链接" fallback, same as that card (PRD "打开失败时的复制
 * URL 兜底"). Disabled (non-clickable) while [webFallback] is still loading
 * or failed to load, rather than crashing on a null URL.
 */
@Composable
private fun AdminWebFallbackRow(webFallback: AdminCardState<WebFallbackTarget>) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var launchError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md)) {
        when (webFallback) {
            is AdminCardState.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("在 Web 端编辑", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is AdminCardState.Failed -> {
                Text("在 Web 端编辑", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(webFallback.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            is AdminCardState.Loaded -> {
                val target = webFallback.data
                Column(
                    modifier = Modifier.fillMaxWidth().clickable {
                        launchError = openInExternalBrowser(context, target.url)
                    },
                ) {
                    Text("在 Web 端编辑", style = MaterialTheme.typography.bodyLarge, color = OpenListPalette.LinkBlue)
                    Text(
                        text = "设置的新增/修改/重置 Token 等操作需要在浏览器中的 Web 管理台完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                launchError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        text = "复制链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpenListPalette.LinkBlue,
                        modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(target.url)) },
                    )
                }
            }
        }
    }
}

private const val MASKED_VALUE = "••••••••"

private fun groupLabel(group: Int?): String = when (group) {
    0 -> "单项设置"
    1 -> "站点"
    2 -> "样式"
    3 -> "预览"
    4 -> "全局"
    5 -> "离线下载"
    6 -> "索引"
    7 -> "单点登录"
    8 -> "LDAP"
    9 -> "S3"
    10 -> "FTP"
    11 -> "流量"
    else -> "其他"
}

