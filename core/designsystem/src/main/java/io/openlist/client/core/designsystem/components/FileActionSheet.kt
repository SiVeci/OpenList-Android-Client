package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    headerTitle: String? = null,
    headerSubtitle: String? = null,
    headerMetadata: String? = null,
    headerIcon: ImageVector? = null,
    headerBadges: List<String> = emptyList(),
    primaryActions: List<FileActionItem> = emptyList(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (headerTitle != null) {
                FileActionHeader(
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    metadata = headerMetadata,
                    icon = headerIcon,
                    badges = headerBadges,
                )
            }
            if (primaryActions.isNotEmpty()) {
                FilePrimaryActions(primaryActions)
            }
            val secondaryActions = actions.filterNot { action -> primaryActions.any { it.label == action.label } }
            secondaryActions.forEach { action ->
                FileActionRow(action)
            }
            if (secondaryActions.any { it.danger }) {
                DeleteWarning()
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun FileActionHeader(
    title: String,
    subtitle: String?,
    metadata: String?,
    icon: ImageVector?,
    badges: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(Spacing.md)
                        .size(34.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metadata != null) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badges.isNotEmpty()) {
                CapabilityChips(labels = badges, tone = StatusTone.SUCCESS)
            }
        }
    }
}

@Composable
private fun FilePrimaryActions(actions: List<FileActionItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        actions.take(3).forEachIndexed { index, action ->
            val buttonModifier = Modifier.weight(1f)
            if (index == 0 && !action.danger) {
                Button(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                ) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                ) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = tint.copy(alpha = 0.72f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DeleteWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "删除前将再次确认",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
