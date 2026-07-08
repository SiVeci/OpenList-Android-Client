package io.openlist.client.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.R
import io.openlist.client.core.designsystem.Spacing

enum class OpenListLogoSurface {
    Dark,
    Light,
}

@Composable
fun OpenListLogoMark(
    surface: OpenListLogoSurface,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(id = surface.logoResourceId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
fun OpenListBrandLockup(
    surface: OpenListLogoSurface,
    modifier: Modifier = Modifier,
    title: String = "OpenList",
    subtitle: String? = null,
    markSize: Dp = 44.dp,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val titleColor = surface.titleColor
    val subtitleColor = surface.subtitleColor

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OpenListLogoMark(
            surface = surface,
            size = markSize,
            contentDescription = null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val OpenListLogoSurface.logoResourceId: Int
    @DrawableRes
    get() = when (this) {
        OpenListLogoSurface.Dark -> R.drawable.openlist_mark_dark_surface
        OpenListLogoSurface.Light -> R.drawable.openlist_mark_light_surface
    }

private val OpenListLogoSurface.titleColor: Color
    @Composable
    get() = when (this) {
        OpenListLogoSurface.Dark -> OpenListPalette.OnDark
        OpenListLogoSurface.Light -> MaterialTheme.colorScheme.onBackground
    }

private val OpenListLogoSurface.subtitleColor: Color
    @Composable
    get() = when (this) {
        OpenListLogoSurface.Dark -> OpenListPalette.OnDarkMuted
        OpenListLogoSurface.Light -> MaterialTheme.colorScheme.onSurfaceVariant
    }
