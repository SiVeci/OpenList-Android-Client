package io.openlist.client.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.model.TextPreviewContent

/**
 * Real in-app plain-text preview (v0.4_EXECUTION_PLAN.md §11 S3-T2). A
 * single scrollable, selectable [Text] is enough for plain text — no line
 * virtualization/LazyColumn-per-line is needed at the 512KB soft cap this
 * content is already bounded to (P-408), and a single Text composable keeps
 * text selection contiguous across the whole body.
 *
 * Uses the long-stable [LocalClipboardManager]/[AnnotatedString] clipboard
 * API (not the newer suspend-based `LocalClipboard`/`ClipEntry` API added
 * around Compose UI 1.7/1.8) since this project's compose-bom (2024.12.01)
 * predates that API settling, and this call is a simple synchronous
 * "copy all text" button, not a multi-format clipboard write.
 *
 * [onDownload] (v0.4_EXECUTION_PLAN.md §11 S4-T3, PRD §12.7 point 3) sits
 * next to "复制全部文本" — this was missed in S3 and is added here so a
 * truncated or otherwise-incomplete text preview always has a way to fetch
 * the full file.
 */
@Composable
fun TextPreviewSurface(
    content: TextPreviewContent,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier.fillMaxSize()) {
        if (content.isTruncated) {
            TruncatedNotice(
                message = "仅显示前 512KB 内容，下载查看完整文件",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { clipboardManager.setText(AnnotatedString(content.text)) }) {
                Text("复制全部文本")
            }
            TextButton(onClick = onDownload) {
                Text("下载")
            }
        }
    }
}

/** Shared truncation banner style for text/markdown previews — a neutral
 * (not error-toned) inline notice, distinct from [io.openlist.client.core.designsystem.components.ErrorBar]
 * since a truncated-but-successful read is not an error. */
@Composable
internal fun TruncatedNotice(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
