package io.openlist.client.feature.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.FileTypeBadge
import io.openlist.client.core.designsystem.components.FileTypeIconPlate
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.fileKindOf
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailScreen(
    onBack: () -> Unit,
    viewModel: FileDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.download() } // proceed regardless of grant result — the
    // download itself doesn't require it, only the completion notification does

    fun startDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.download()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "文件详情", subtitle = uiState.instanceName, onBack = onBack) },
    ) { padding ->
        val detail = uiState.detail
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding).fillMaxSize())
            detail == null -> ErrorBar(
                message = uiState.errorMessage ?: "加载失败",
                modifier = Modifier.padding(padding).fillMaxWidth(),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val kind = fileKindOf(detail.name, detail.isDir)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileTypeIconPlate(kind = kind, size = 48.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(text = detail.name, style = MaterialTheme.typography.headlineSmall)
                            FileTypeBadge(
                                text = if (detail.isDir) "目录" else detail.name.substringAfterLast('.', "").uppercase().ifEmpty { "文件" },
                                kind = kind,
                            )
                        }
                    }
                    DetailRow(label = "路径", value = detail.path)
                    if (!detail.isDir) {
                        DetailRow(label = "大小", value = formatSize(detail.size))
                    }
                    detail.modifiedAt?.let { DetailRow(label = "修改时间", value = formatDate(it)) }
                    DetailRow(label = "类型", value = if (detail.isDir) "目录" else "文件")

                    when (val downloadState = uiState.downloadState) {
                        DownloadUiState.Idle -> Unit
                        DownloadUiState.Enqueued -> StatusBadge(text = "已加入下载队列，请在通知栏查看进度", tone = StatusTone.SUCCESS)
                        is DownloadUiState.Failed -> ErrorBar(message = downloadState.message, onRetry = ::startDownload)
                    }

                    if (!detail.isDir) {
                        PrimaryButton(
                            text = "下载",
                            onClick = ::startDownload,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = detail.rawUrl.isNotBlank(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SecondaryButton(
                            text = "复制路径",
                            onClick = { clipboardManager.setText(AnnotatedString(detail.path)) },
                        )
                        SecondaryButton(
                            text = "复制链接",
                            onClick = { clipboardManager.setText(AnnotatedString(detail.rawUrl)) },
                            enabled = detail.rawUrl.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroup.toDouble())
    return if (digitGroup == 0) "$bytes ${units[0]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroup])
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
