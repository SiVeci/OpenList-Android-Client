package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.openlist.client.core.designsystem.Spacing

/**
 * Bottom sheet offering the fallback actions for a file the app can't render
 * in-app (v0.4_EXECUTION_PLAN.md §11 S4-T2: PDF/OFFICE/UNKNOWN preview
 * `openMode`s EXTERNAL_APP/UNSUPPORTED). Lives in `:core:designsystem` rather
 * than `:feature:preview` — like [FileActionSheet], it's a pure UI shell with
 * no Intent/Repository logic of its own, so any future entry point (search
 * results, share links, task center) can reuse the exact same three-action
 * layout without depending on the preview feature module. All Intent
 * construction and download orchestration is the caller's responsibility via
 * the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalOpenSheet(
    canDownload: Boolean,
    onOpenExternal: () -> Unit,
    onDownload: () -> Unit,
    onOpenWeb: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "该文件暂不支持应用内查看",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "可以尝试用其他应用打开、下载到本地，或在浏览器中查看",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = "外部打开",
                onClick = onOpenExternal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (canDownload) {
                SecondaryButton(
                    text = "下载",
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SecondaryButton(
                text = "网页打开",
                onClick = onOpenWeb,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
