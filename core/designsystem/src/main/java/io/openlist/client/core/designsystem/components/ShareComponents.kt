package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.Spacing

/** Mirrors the share's derived display state — never the raw enabled/expiresAt
 * fields, so this module doesn't need a `:core:model` dependency. */
enum class ShareCardStatus { ENABLED, DISABLED, EXPIRED }

@Composable
fun ShareStatusBadge(status: ShareCardStatus, modifier: Modifier = Modifier) {
    val (text, tone) = when (status) {
        ShareCardStatus.ENABLED -> "启用" to StatusTone.SUCCESS
        ShareCardStatus.DISABLED -> "禁用" to StatusTone.NEUTRAL
        ShareCardStatus.EXPIRED -> "已过期" to StatusTone.ERROR
    }
    StatusBadge(text = text, modifier = modifier, tone = tone)
}

/**
 * One row of the share list (v0.3_EXECUTION_PLAN.md §7.1/§13): card-base —
 * canvas background, 12dp corners, 1dp hairline border, spacing.md padding.
 * [pathSummary] is pre-formatted by the caller ("/movies" or "/movies 等 3 项").
 */
@Composable
fun ShareCard(
    name: String,
    pathSummary: String,
    status: ShareCardStatus,
    hasPassword: Boolean,
    expiresText: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasPassword) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "有密码",
                            modifier = Modifier.padding(top = 1.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = pathSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ShareStatusBadge(status)
                    if (expiresText != null) {
                        Text(
                            text = expiresText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            trailing()
        }
    }
}

/**
 * Copy link / copy password / copy full text / system share (v0.3_EXECUTION_PLAN.md
 * §9 item 9, §14). Reuses [PrimaryButton]/[SecondaryButton]; the "无密码不含密码行"
 * rule is the caller's — [onCopyPassword] simply isn't offered when null.
 */
@Composable
fun ShareLinkActions(
    onCopyLink: () -> Unit,
    onSystemShare: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyPassword: (() -> Unit)? = null,
    onCopyFullText: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(text = "复制链接", onClick = onCopyLink, modifier = Modifier.weight(1f))
            SecondaryButton(text = "系统分享", onClick = onSystemShare, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (onCopyPassword != null) {
                SecondaryButton(text = "复制密码", onClick = onCopyPassword, modifier = Modifier.weight(1f))
            }
            if (onCopyFullText != null) {
                SecondaryButton(text = "复制分享文案", onClick = onCopyFullText, modifier = Modifier.weight(1f))
            }
        }
    }
}
