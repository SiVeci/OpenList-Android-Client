package io.openlist.client.feature.files

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.ExpiryOption
import io.openlist.client.core.designsystem.components.ShareFormSheet
import io.openlist.client.core.designsystem.components.ShareLinkActions

/**
 * Wraps [ShareFormSheet]; once [ShareCreateState.createdShare] is set it
 * swaps to the post-create success state — link + copy + system share, per
 * PRD §6.5 — inline in the same sheet rather than navigating away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCreateSheet(
    state: ShareCreateState,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onExpiryOptionChange: (ExpiryOption) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.createdShare != null && state.shareUrl != null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            val clipboardManager = LocalClipboardManager.current
            val context = LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).navigationBarsPadding(),
            ) {
                Text("分享创建成功", style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Spacing.xs))
                Text(state.shareUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Spacing.md))
                ShareLinkActions(
                    onCopyLink = { clipboardManager.setText(AnnotatedString(state.shareUrl)) },
                    onCopyPassword = state.createdShare.password?.takeIf { it.isNotBlank() }?.let { pwd ->
                        { clipboardManager.setText(AnnotatedString(pwd)) }
                    },
                    onCopyFullText = {
                        val text = buildShareShareText(state.createdShare.name ?: state.targetPath, state.shareUrl, state.createdShare.password)
                        clipboardManager.setText(AnnotatedString(text))
                    },
                    onSystemShare = {
                        val text = buildShareShareText(state.createdShare.name ?: state.targetPath, state.shareUrl, state.createdShare.password)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                )
            }
        }
    } else {
        ShareFormSheet(
            title = "创建分享",
            pathSummary = state.targetPath,
            name = state.name,
            onNameChange = onNameChange,
            password = state.password,
            onPasswordChange = onPasswordChange,
            expiryOption = state.expiryOption,
            onExpiryOptionChange = onExpiryOptionChange,
            enabled = state.enabled,
            onEnabledChange = onEnabledChange,
            onDismiss = onDismiss,
            onSubmit = onSubmit,
            submitText = "创建分享",
            submitting = state.submitting,
            errorMessage = state.errorMessage,
        )
    }
}

private fun buildShareShareText(name: String, url: String, password: String?): String = buildString {
    append("文件分享：$name\n")
    append("链接：$url")
    if (!password.isNullOrBlank()) append("\n密码：$password")
}
