package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.openlist.client.core.designsystem.Spacing

/**
 * One row of a [FileActionSheet]. [danger] applies the error-tone styling
 * (v0.2_EXECUTION_PLAN.md §13.3 — delete must be visually distinct from every
 * other action here); [enabled] lets the caller grey out actions the current
 * permission/session state doesn't allow without removing them from the list.
 */
data class FileActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Bottom sheet for a single file/directory's actions (download/detail/rename/
 * move/copy/delete/...). Reused as-is for every entry point that needs a file
 * action menu — v0.3's share and v0.4's preview graduate into this same list
 * rather than a new component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    actions: List<FileActionItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        actions.forEach { action ->
            FileActionRow(action)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun FileActionRow(action: FileActionItem) {
    val tint = when {
        !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        action.danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics { contentDescription = action.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(imageVector = action.icon, contentDescription = null, tint = tint)
        Text(text = action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
