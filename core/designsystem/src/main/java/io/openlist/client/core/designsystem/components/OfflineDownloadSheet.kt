package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing

/**
 * Offline-download submission form (v0.3_EXECUTION_PLAN.md §7.1/§13/§17):
 * URL (required, http/https/magnet validated by the caller), target directory
 * (delegates to [DirectoryPickerSheet] — [onPickDirectory] opens it), tool
 * picker as chips (the caller hides this row entirely when [tools] has a
 * single entry).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfflineDownloadSheet(
    url: String,
    onUrlChange: (String) -> Unit,
    targetDirText: String,
    onPickDirectory: () -> Unit,
    tools: List<String>,
    selectedTool: String?,
    onToolSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    errorMessage: String? = null,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("新增离线下载", style = MaterialTheme.typography.titleMedium)

            AppTextField(
                value = url,
                onValueChange = onUrlChange,
                label = "下载链接（http/https/magnet）",
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("保存目录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = onPickDirectory,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(targetDirText, modifier = Modifier.weight(1f))
                }
            }

            if (tools.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text("下载工具", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        tools.forEach { tool ->
                            FilterChip(
                                selected = tool == selectedTool,
                                onClick = { onToolSelected(tool) },
                                label = { Text(tool) },
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "提交", onClick = onSubmit, modifier = Modifier.weight(1f), loading = submitting)
            }
        }
    }
}
