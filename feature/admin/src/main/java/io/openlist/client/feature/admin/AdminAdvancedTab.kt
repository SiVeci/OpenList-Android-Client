package io.openlist.client.feature.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.PrimaryButton
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.model.WebFallbackTarget

/**
 * Advanced Tab content (v0.5_EXECUTION_PLAN.md §11 S7-T4, PRD §12.8). Two
 * sections: [WebFallbackCard] (the full Web-console fallback entry, with the
 * safety note and an "open failed -> copy URL" fallback per PRD) and a static
 * list of the native-uncovered capabilities (informational text only, no
 * interactivity).
 *
 * Wired the same way [AdminOverviewTab] is -- fed [AdminUiState] +
 * plain callbacks from [AdminHostScreen]'s `AdminScaffold`, not a ViewModel
 * reference, so this composable stays trivially previewable/testable.
 *
 * Launching the Web URL mirrors [io.openlist.client.feature.preview
 * .PreviewScreen]'s `openInBrowser` pattern exactly (DEC-503: plain
 * `ACTION_VIEW` external-browser Intent, no Custom Tabs/WebView dependency) --
 * `:feature:admin` has no dependency on `:feature:preview`, so this is an
 * independent, structurally-identical implementation, not a shared call.
 */
@Composable
fun AdminAdvancedTab(
    webFallback: AdminCardState<WebFallbackTarget>,
    onLoadWebFallback: () -> Unit,
    onRetryWebFallback: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoadWebFallback() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        WebFallbackCard(state = webFallback, onRetry = onRetryWebFallback)
        UnsupportedCapabilitiesCard()
    }
}

/**
 * The full Web-console fallback card (PRD §12.8): shows the constructed URL,
 * a "打开 Web 管理台" button, the "可能需要在 Web 端重新登录" safety note (per
 * [WebFallbackTarget.requiresWebLogin], which [AdminWebFallbackRepositoryImpl]
 * always sets `true`), and -- if the external-browser Intent fails to resolve
 * (`ActivityNotFoundException`) -- an inline error plus a "复制链接" fallback
 * button (PRD "打开失败时的复制 URL 兜底").
 */
@Composable
internal fun WebFallbackCard(
    state: AdminCardState<WebFallbackTarget>,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var launchError by remember { mutableStateOf<String?>(null) }

    AdvancedCardContainer {
        Text("Web 管理台", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        when (state) {
            is AdminCardState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Spacing.xs))
            is AdminCardState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            }
            is AdminCardState.Loaded -> {
                val target = state.data
                Text(
                    text = target.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (target.requiresWebLogin) {
                    Text(
                        text = "可能需要在 Web 端重新登录，原生登录状态与 Web 端会话不互通",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(Spacing.xs))
                PrimaryButton(
                    text = "打开 Web 管理台",
                    onClick = {
                        launchError = openInExternalBrowser(context, target.url)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                launchError?.let { message ->
                    Spacer(Modifier.size(Spacing.xs))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    SecondaryButton(
                        text = "复制链接",
                        onClick = { clipboardManager.setText(AnnotatedString(target.url)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * `Intent.ACTION_VIEW` with no MIME type, mirroring [io.openlist.client
 * .feature.preview.PreviewScreen]'s `openInBrowser` exactly (DEC-503) --
 * independently implemented since `:feature:admin` has no dependency on
 * `:feature:preview`. Returns a user-facing error message on
 * [ActivityNotFoundException], `null` on success, so callers can drive their
 * own "show a copy-URL fallback" UI state without this function owning any
 * Compose state itself.
 */
internal fun openInExternalBrowser(context: android.content.Context, url: String): String? {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "未找到可打开链接的浏览器"
    }
}

/** Static, non-interactive list of v0.5's native-uncovered admin capabilities
 * (PRD §12.8): user/storage CRUD, dynamic driver config forms, settings
 * editing, Token reset, meta/message/scan management -- all of these remain
 * Web-console-only in this version. */
@Composable
private fun UnsupportedCapabilitiesCard() {
    AdvancedCardContainer {
        Text("v0.5 原生不覆盖的管理能力", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Spacing.xxs))
        UNSUPPORTED_CAPABILITIES.forEach { capability ->
            Text(
                text = "· $capability",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val UNSUPPORTED_CAPABILITIES = listOf(
    "用户 / 存储的新增、编辑、删除",
    "驱动动态配置表单",
    "设置项的编辑",
    "Token 重置",
    "元信息（Meta）管理",
    "站内消息管理",
    "文件扫描 / 校验管理",
)

@Composable
private fun AdvancedCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), content = content)
    }
}
