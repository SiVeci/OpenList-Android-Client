package io.openlist.client.feature.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.designsystem.components.AppTopBar
import io.openlist.client.core.designsystem.components.ConfirmDialog
import io.openlist.client.core.designsystem.components.DangerActionButton
import io.openlist.client.core.designsystem.components.ErrorBar
import io.openlist.client.core.designsystem.components.ListRowItem
import io.openlist.client.core.designsystem.components.LoadingState
import io.openlist.client.core.designsystem.components.SecondaryButton
import io.openlist.client.core.designsystem.components.ShareFormSheet
import io.openlist.client.core.designsystem.components.ShareLinkActions
import io.openlist.client.core.designsystem.components.ShareStatusBadge
import io.openlist.client.core.model.PreviewKindResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDetailScreen(
    onBack: () -> Unit,
    // Directory rows in the file list below navigate to the *ordinary*
    // Routes.fileList browsing screen -- this is the share creator's own
    // normal instance permissions (V-405), not any shared-link/guest-scoped
    // browsing. There is intentionally no "browse this share as a guest
    // would see it" feature here or anywhere else in v0.4 (D-06/§5.3
    // explicitly excludes full share-mode directory browsing).
    onOpenDirectory: (path: String) -> Unit,
    onOpenFile: (path: String) -> Unit,
    onOpenFileDetail: (path: String) -> Unit,
    viewModel: ShareDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissSnackbar()
    }

    Scaffold(
        topBar = { AppTopBar(title = "分享详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding).fillMaxSize())
            uiState.share == null -> ErrorBar(
                message = uiState.errorMessage ?: "分享不存在或已被删除",
                modifier = Modifier.padding(padding).fillMaxWidth(),
            )
            else -> {
                val share = uiState.share!!
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(share.displayName(), style = MaterialTheme.typography.titleLarge)
                        ShareStatusBadge(share.cardStatus())
                    }
                    DetailRow(label = "分享路径", value = share.pathSummary())
                    DetailRow(label = "分享链接", value = uiState.shareUrl ?: "生成中…")
                    DetailRow(label = "密码", value = share.password?.ifBlank { null } ?: "无")
                    DetailRow(label = "过期时间", value = share.expiresAt?.let(::formatDate) ?: "永久")
                    DetailRow(label = "访问次数", value = "${share.accessed}" + if (share.maxAccessed > 0) " / ${share.maxAccessed}" else "")

                    if (uiState.shareUrl != null) {
                        ShareLinkActions(
                            onCopyLink = { clipboardManager.setText(AnnotatedString(uiState.shareUrl!!)) },
                            onCopyPassword = share.password?.takeIf { it.isNotBlank() }?.let { pwd ->
                                { clipboardManager.setText(AnnotatedString(pwd)) }
                            },
                            onCopyFullText = {
                                clipboardManager.setText(AnnotatedString(buildShareText(share.displayName(), uiState.shareUrl!!, share.password)))
                            },
                            onSystemShare = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, buildShareText(share.displayName(), uiState.shareUrl!!, share.password))
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = if (share.enabled) "禁用" else "启用",
                            onClick = { viewModel.toggleEnabled() },
                            enabled = !uiState.toggling,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "编辑", onClick = { viewModel.openEditSheet() }, modifier = Modifier.weight(1f))
                    }
                    DangerActionButton(
                        text = "删除分享",
                        onClick = { viewModel.openDeleteConfirm() },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ShareFileEntriesSection(
                        entries = uiState.fileEntries,
                        onOpenDirectory = onOpenDirectory,
                        onOpenFile = onOpenFile,
                        onOpenFileDetail = onOpenFileDetail,
                    )
                }
            }
        }
    }

    if (uiState.showDeleteConfirm) {
        ConfirmDialog(
            title = "删除分享",
            message = "确定删除这个分享吗？分享链接将立即失效，此操作无法撤销。",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteConfirm() },
            confirmText = "删除",
            danger = true,
            loading = uiState.deleting,
        )
    }

    uiState.editSheet?.let { edit ->
        ShareFormSheet(
            title = "编辑分享",
            pathSummary = uiState.share?.pathSummary().orEmpty(),
            name = edit.name,
            onNameChange = viewModel::updateEditName,
            password = edit.password,
            onPasswordChange = viewModel::updateEditPassword,
            expiryOption = edit.expiryOption,
            onExpiryOptionChange = viewModel::updateEditExpiryOption,
            enabled = edit.enabled,
            onEnabledChange = viewModel::updateEditEnabled,
            onDismiss = { viewModel.dismissEditSheet() },
            onSubmit = { viewModel.submitEdit() },
            submitText = "保存",
            submitting = edit.submitting,
            errorMessage = edit.errorMessage,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "分享文件" section (S6-T3, P-406): renders every [ShareFileEntry] resolved
 * from `Share.paths` by [ShareDetailViewModel.loadFileEntries]. A handful of
 * rows per V-405's documented assumption, so a plain (non-lazy) [Column] of
 * [ListRowItem]s is used here, consistent with how few entries a share
 * realistically has -- no [androidx.compose.foundation.lazy.LazyColumn]
 * needed for this small, bounded list.
 *
 * Row behavior:
 *  - [ShareFileEntry.loadError]: plain non-clickable text row with the
 *    "文件可能已被移动或删除" fallback copy (V-405) -- never navigates anywhere.
 *  - Directory: [onOpenDirectory] navigates to the *ordinary* file-list route
 *    for this instance (the share creator's own normal browsing permissions,
 *    not a shared/guest-scoped view -- see [ShareDetailScreen]'s own KDoc for
 *    why this distinction matters and must not be conflated with "share-mode
 *    browsing", which v0.4 explicitly does not implement).
 *  - File: routed through [PreviewKindResolver.isInAppPreviewable] exactly
 *    like every other v0.4 entry point (file list / search results / task
 *    center), to [onOpenFile] or [onOpenFileDetail].
 */
@Composable
private fun ShareFileEntriesSection(
    entries: List<ShareFileEntry>,
    onOpenDirectory: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFileDetail: (String) -> Unit,
) {
    if (entries.isEmpty()) return

    Column {
        Text(
            text = "分享文件",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        entries.forEachIndexed { index, entry ->
            if (entry.loadError) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "文件可能已被移动或删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                ListRowItem(
                    name = entry.name,
                    isDir = entry.isDir,
                    onClick = {
                        when {
                            entry.isDir -> onOpenDirectory(entry.path)
                            PreviewKindResolver.isInAppPreviewable(PreviewKindResolver.resolve(entry.name)) -> onOpenFile(entry.path)
                            else -> onOpenFileDetail(entry.path)
                        }
                    },
                )
            }
            if (index != entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

internal fun buildShareText(name: String, url: String, password: String?): String = buildString {
    append("文件分享：$name\n")
    append("链接：$url")
    if (!password.isNullOrBlank()) append("\n密码：$password")
}
