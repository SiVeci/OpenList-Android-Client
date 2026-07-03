package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListTheme
import io.openlist.client.core.designsystem.Spacing

/** Mirrors `UploadTaskEntity.status` (v0.2_EXECUTION_PLAN.md §11.1) for display. */
enum class UploadItemStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }

/**
 * One row of the upload panel (v0.2_EXECUTION_PLAN.md §13.8). [progress] is
 * `0f..1f`, or `null` for an indeterminate/unknown-size upload — the progress
 * bar switches tone by status (primary while running, error on failure,
 * success on completion) per DESIGN.md's semantic colors.
 */
@Composable
fun UploadProgressItem(
    fileName: String,
    sizeText: String,
    status: UploadItemStatus,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    errorMessage: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLine(status, sizeText, errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status == UploadItemStatus.PENDING || status == UploadItemStatus.RUNNING) {
                if (onCancel != null) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消上传")
                    }
                } else if (status == UploadItemStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (status == UploadItemStatus.RUNNING || status == UploadItemStatus.PENDING) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun statusColor(status: UploadItemStatus) = when (status) {
    UploadItemStatus.FAILED -> MaterialTheme.colorScheme.error
    UploadItemStatus.SUCCESS -> OpenListTheme.extendedColors.success
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLine(status: UploadItemStatus, sizeText: String, errorMessage: String?): String = when (status) {
    UploadItemStatus.PENDING -> "$sizeText · 等待上传"
    UploadItemStatus.RUNNING -> "$sizeText · 上传中"
    UploadItemStatus.SUCCESS -> "$sizeText · 已完成"
    UploadItemStatus.CANCELLED -> "$sizeText · 已取消"
    UploadItemStatus.FAILED -> errorMessage?.let { "$sizeText · 失败：$it" } ?: "$sizeText · 上传失败"
}
