package io.openlist.client.feature.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.SearchBar
import io.openlist.client.core.designsystem.components.SegmentedSelector
import io.openlist.client.core.designsystem.components.ShareCardStatus
import io.openlist.client.core.designsystem.components.ShareStatusBadge
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareListScreen(
    onBack: () -> Unit,
    onOpenShareDetail: (shareId: String) -> Unit,
    onOpenShareLink: () -> Unit,
    viewModel: ShareListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "我的分享",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenShareLink) {
                        Icon(Icons.Filled.Link, contentDescription = "打开分享链接")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ShareListControls(
                searchQuery = uiState.searchQuery,
                statusFilter = uiState.statusFilter,
                totalCount = uiState.shares.size,
                visibleCount = uiState.visibleShares.size,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSearchClear = { viewModel.onSearchQueryChange("") },
                onStatusFilterChange = viewModel::onStatusFilterChange,
            )
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
                    uiState.shares.isEmpty() -> EmptyState(
                        title = "暂无分享",
                        description = "去文件页创建一个分享吧",
                        modifier = Modifier.fillMaxSize(),
                    )
                    uiState.visibleShares.isEmpty() -> EmptyState(
                        title = "没有匹配的分享",
                        description = "调整搜索关键词或状态筛选后再试",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(uiState.visibleShares, key = { it.id }) { share ->
                            ShareListRow(
                                share = share,
                                onClick = { onOpenShareDetail(share.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareListControls(
    searchQuery: String,
    statusFilter: ShareStatusFilter,
    totalCount: Int,
    visibleCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onStatusFilterChange: (ShareStatusFilter) -> Unit,
) {
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = {},
            onClear = onSearchClear,
            placeholder = "搜索分享名称、路径或 ID",
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        SegmentedSelector(
            options = ShareStatusFilter.entries.map { it.label },
            selectedIndex = ShareStatusFilter.entries.indexOf(statusFilter),
            onSelectedIndexChange = { index -> onStatusFilterChange(ShareStatusFilter.entries[index]) },
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = if (visibleCount == totalCount) "共 $totalCount 个分享" else "显示 $visibleCount / $totalCount 个分享",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareListRow(
    share: Share,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = share.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!share.password.isNullOrEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "有密码",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = share.pathSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    ShareStatusBadge(share.cardStatus())
                    if (share.maxAccessed > 0) {
                        StatusBadge(text = "${share.accessed}/${share.maxAccessed}", tone = StatusTone.NEUTRAL)
                    }
                    share.expiresAt?.let {
                        Text(
                            text = "过期时间 ${formatDate(it)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun Share.displayName(): String = name?.takeIf { it.isNotBlank() } ?: pathSummary()

internal fun Share.pathSummary(): String {
    val first = paths.firstOrNull().orEmpty()
    return if (paths.size > 1) "$first 等 ${paths.size} 项" else first
}

internal fun Share.cardStatus(): ShareCardStatus {
    val expiry = expiresAt
    return when {
        expiry != null && expiry < System.currentTimeMillis() -> ShareCardStatus.EXPIRED
        !enabled -> ShareCardStatus.DISABLED
        else -> ShareCardStatus.ENABLED
    }
}

internal fun formatDate(epochMillis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
