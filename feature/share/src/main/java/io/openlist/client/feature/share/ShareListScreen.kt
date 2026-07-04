package io.openlist.client.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.EmptyState
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.ShareCard
import io.openlist.client.core.designsystem.components.ShareCardStatus
import io.openlist.client.core.model.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareListScreen(
    onBack: () -> Unit,
    onOpenShareDetail: (shareId: String) -> Unit,
    viewModel: ShareListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "我的分享", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(uiState.shares, key = { it.id }) { share ->
                            ShareCard(
                                name = share.displayName(),
                                pathSummary = share.pathSummary(),
                                status = share.cardStatus(),
                                hasPassword = !share.password.isNullOrEmpty(),
                                expiresText = share.expiresAt?.let { "过期时间 ${formatDate(it)}" },
                                onClick = { onOpenShareDetail(share.id) },
                            )
                        }
                    }
                }
            }
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
