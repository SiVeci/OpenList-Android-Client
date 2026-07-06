package io.openlist.client.feature.share

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareOpenScreen(
    onBack: () -> Unit,
    viewModel: ShareOpenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        val clip = clipboardManager.getText()?.text
        if (!clip.isNullOrBlank()) viewModel.onClipboardDetected(clip)
    }

    Scaffold(
        topBar = { AppTopBar(title = "打开分享链接", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            uiState.clipboardSuggestion?.let { suggestion ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("检测到剪贴板中的分享链接", style = MaterialTheme.typography.titleSmall)
                        Text(suggestion, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton(text = "使用", onClick = viewModel::useClipboardSuggestion)
                            TextButton(onClick = viewModel::dismissClipboardSuggestion) { Text("忽略") }
                        }
                    }
                }
            }

            when (val status = uiState.status) {
                is ShareOpenStatus.Idle -> {
                    AppTextField(
                        value = uiState.inputUrl,
                        onValueChange = viewModel::onUrlChange,
                        label = "分享链接",
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "粘贴同实例的 /@s/ 分享链接",
                    )
                    uiState.errorMessage?.let { ErrorBar(message = it) }
                    PrimaryButton(
                        text = "解析",
                        onClick = viewModel::submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.inputUrl.isNotBlank() && !uiState.isSubmitting,
                        loading = uiState.isSubmitting,
                    )
                }
                is ShareOpenStatus.NeedsPassword -> {
                    Text("该分享需要密码", style = MaterialTheme.typography.titleMedium)
                    AppTextField(
                        value = uiState.passwordInput,
                        onValueChange = viewModel::onPasswordChange,
                        label = "分享密码",
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    uiState.errorMessage?.let { ErrorBar(message = it) }
                    PrimaryButton(
                        text = "确认",
                        onClick = viewModel::submitPassword,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.passwordInput.isNotBlank() && !uiState.isSubmitting,
                        loading = uiState.isSubmitting,
                    )
                    TextButton(onClick = viewModel::reset) { Text("重新输入链接") }
                }
                is ShareOpenStatus.Resolved -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(status.info.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (status.info.isDir) "目录分享" else "文件 · ${formatSize(status.info.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (status.info.isDir) {
                                Text(
                                    "v1.0 暂不支持在 App 内浏览分享目录，请使用下方入口在浏览器中查看完整内容。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    SecondaryButton(
                        text = "复制链接",
                        onClick = { clipboardManager.setText(AnnotatedString(status.target.sourceUrl)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "外部浏览器打开",
                        onClick = { openExternally(context, status.target.sourceUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!status.info.isDir && status.info.rawUrl.isNotBlank()) {
                        PrimaryButton(
                            text = "外部应用打开文件",
                            onClick = { openExternally(context, status.info.rawUrl) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(onClick = viewModel::reset) { Text("打开另一个链接") }
                }
            }
        }
    }
}

/** Same independent-per-feature pattern as `AdminAdvancedTab.openInExternalBrowser`
 * (DEC-503) — `:feature:share` has no dependency on `:feature:admin`/`:feature:preview`. */
private fun openExternally(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No installed app/browser can handle it; the link is still on the
        // clipboard from "复制链接" if the user tapped that first. A toast
        // isn't wired here to keep this helper Compose-state-free, matching
        // the admin precedent's own scope.
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroup.toDouble())
    return if (digitGroup == 0) "$bytes ${units[0]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroup])
}
