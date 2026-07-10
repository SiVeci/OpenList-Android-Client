package io.openlist.client.feature.share

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTextField
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.GroupCard
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.designsystem.components.StatusBadge
import io.openlist.client.core.designsystem.components.StatusTone
import io.openlist.client.core.model.ShareInboundInfo
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
            ShareOpenHeader()
            uiState.clipboardSuggestion?.let { suggestion ->
                ClipboardSuggestionCard(
                    suggestion = suggestion,
                    onUse = viewModel::useClipboardSuggestion,
                    onDismiss = viewModel::dismissClipboardSuggestion,
                )
            }

            when (val status = uiState.status) {
                is ShareOpenStatus.Idle -> LinkInputPanel(
                    inputUrl = uiState.inputUrl,
                    errorMessage = uiState.errorMessage,
                    isSubmitting = uiState.isSubmitting,
                    onUrlChange = viewModel::onUrlChange,
                    onSubmit = viewModel::submit,
                )
                is ShareOpenStatus.NeedsPassword -> PasswordPanel(
                    passwordInput = uiState.passwordInput,
                    errorMessage = uiState.errorMessage,
                    isSubmitting = uiState.isSubmitting,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSubmitPassword = viewModel::submitPassword,
                    onReset = viewModel::reset,
                )
                is ShareOpenStatus.Resolved -> ResolvedPanel(
                    info = status.info,
                    sourceUrl = status.target.sourceUrl,
                    onCopyLink = { clipboardManager.setText(AnnotatedString(status.target.sourceUrl)) },
                    onOpenBrowser = { openExternally(context, status.target.sourceUrl) },
                    onOpenFileExternally = { openExternally(context, status.info.rawUrl) },
                    onReset = viewModel::reset,
                )
            }
        }
    }
}

@Composable
private fun ShareOpenHeader() {
    GroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "解析同实例分享",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "粘贴 /@s/ 分享链接，必要时输入分享密码后打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClipboardSuggestionCard(
    suggestion: String,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text("检测到剪贴板中的分享链接", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "使用", onClick = onUse)
                TextButton(onClick = onDismiss) { Text("忽略") }
            }
        }
    }
}

@Composable
private fun LinkInputPanel(
    inputUrl: String,
    errorMessage: String?,
    isSubmitting: Boolean,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    GroupCard {
        AppTextField(
            value = inputUrl,
            onValueChange = onUrlChange,
            label = "分享链接",
            modifier = Modifier.fillMaxWidth(),
            supportingText = "粘贴同实例的 /@s/ 分享链接",
        )
        errorMessage?.let {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ErrorBar(message = it)
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        PrimaryButton(
            text = "解析",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = inputUrl.isNotBlank() && !isSubmitting,
            loading = isSubmitting,
        )
    }
}

@Composable
private fun PasswordPanel(
    passwordInput: String,
    errorMessage: String?,
    isSubmitting: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onReset: () -> Unit,
) {
    GroupCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Password, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("该分享需要密码", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            label = "分享密码",
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        errorMessage?.let {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ErrorBar(message = it)
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        PrimaryButton(
            text = "确认",
            onClick = onSubmitPassword,
            modifier = Modifier.fillMaxWidth(),
            enabled = passwordInput.isNotBlank() && !isSubmitting,
            loading = isSubmitting,
        )
        TextButton(onClick = onReset) { Text("重新输入链接") }
    }
}

@Composable
private fun ResolvedPanel(
    info: ShareInboundInfo,
    sourceUrl: String,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFileExternally: () -> Unit,
    onReset: () -> Unit,
) {
    GroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (info.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (info.isDir) "目录分享" else "文件 · ${formatSize(info.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(text = if (info.isDir) "目录" else "文件", tone = StatusTone.NEUTRAL)
        }
        if (info.isDir) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "v1.0 暂不支持在 App 内浏览分享目录，请使用下方入口在浏览器中查看完整内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    GroupCard {
        ActionRow(
            icon = Icons.Outlined.ContentCopy,
            title = "复制链接",
            subtitle = sourceUrl,
            actionText = "复制",
            onClick = onCopyLink,
        )
        ActionRow(
            icon = Icons.Outlined.OpenInBrowser,
            title = "浏览器打开",
            subtitle = "使用外部浏览器查看分享页",
            actionText = "打开",
            onClick = onOpenBrowser,
        )
        if (!info.isDir && info.rawUrl.isNotBlank()) {
            ActionRow(
                icon = Icons.Outlined.OpenInNew,
                title = "外部应用打开文件",
                subtitle = "使用签名文件链接交给系统处理",
                actionText = "打开",
                onClick = onOpenFileExternally,
            )
        }
    }
    TextButton(onClick = onReset) { Text("打开另一个链接") }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SecondaryButton(text = actionText, onClick = onClick)
    }
}

/** Same independent-per-feature pattern as `AdminAdvancedTab.openInExternalBrowser`
 * (DEC-503): `:feature:share` has no dependency on `:feature:admin`/`:feature:preview`. */
private fun openExternally(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No installed app/browser can handle it; the link is still available
        // through the explicit copy action above.
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroup.toDouble())
    return if (digitGroup == 0) "$bytes ${units[0]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroup])
}
