package io.openlist.client.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDarkTheme = staticCompositionLocalOf { false }

object OpenListTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current

    /** Whether the app theme (not necessarily the system) is dark — used by
     * pastel tint components to swap plate/ink roles on dark surfaces. */
    val isDark: Boolean
        @Composable
        get() = LocalDarkTheme.current
}

@Composable
fun OpenListClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic color is intentionally not used: DESIGN.md treats {colors.primary}
    // as the app's single recognizable brand signal, which per-device dynamic
    // theming would override.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OpenListTypography,
            shapes = OpenListShapes,
            content = content,
        )
    }
}
