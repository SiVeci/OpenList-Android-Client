package io.openlist.client.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palette tokens transcribed from DESIGN.md (colors.*). Only tokens are reused;
// the source document is a marketing-site analysis, its components are not.
object OpenListPalette {
    val Primary = Color(0xFF5645D4)
    val PrimaryPressed = Color(0xFF4534B3)
    val PrimaryDeep = Color(0xFF3A2A99)
    val OnPrimary = Color(0xFFFFFFFF)

    val Canvas = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF6F5F4)
    val SurfaceSoft = Color(0xFFFAFAF9)
    val Hairline = Color(0xFFE5E3DF)
    val HairlineSoft = Color(0xFFEDE9E4)
    val HairlineStrong = Color(0xFFC8C4BE)

    val InkDeep = Color(0xFF000000)
    val Ink = Color(0xFF1A1A1A)
    val Charcoal = Color(0xFF37352F)
    val Slate = Color(0xFF5D5B54)
    val Steel = Color(0xFF787671)
    val Stone = Color(0xFFA4A097)
    val Muted = Color(0xFFBBB8B1)

    val Success = Color(0xFF1AAE39)
    val Warning = Color(0xFFDD5B00)
    val Error = Color(0xFFE03131)

    // Dark-theme surface ramp: DESIGN.md has no dark tokens (documented Known Gap),
    // derived here from the same navy/charcoal family used for on-dark text.
    val DarkCanvas = Color(0xFF15161A)
    val DarkSurface = Color(0xFF1D1F24)
    val DarkSurfaceSoft = Color(0xFF24262C)
    val DarkHairline = Color(0xFF34363D)
    val DarkHairlineStrong = Color(0xFF44474F)
    val DarkInk = Color(0xFFF3F2F0)
    val DarkSlate = Color(0xFFB9B6AE)
    val DarkPrimary = Color(0xFF9B8CFF)
}

val LightColorScheme = lightColorScheme(
    primary = OpenListPalette.Primary,
    onPrimary = OpenListPalette.OnPrimary,
    primaryContainer = OpenListPalette.PrimaryDeep,
    onPrimaryContainer = OpenListPalette.OnPrimary,
    background = OpenListPalette.Canvas,
    onBackground = OpenListPalette.Ink,
    surface = OpenListPalette.Canvas,
    onSurface = OpenListPalette.Ink,
    surfaceVariant = OpenListPalette.Surface,
    onSurfaceVariant = OpenListPalette.Slate,
    outline = OpenListPalette.HairlineStrong,
    outlineVariant = OpenListPalette.Hairline,
    error = OpenListPalette.Error,
    onError = OpenListPalette.OnPrimary,
)

val DarkColorScheme = darkColorScheme(
    primary = OpenListPalette.DarkPrimary,
    onPrimary = OpenListPalette.InkDeep,
    primaryContainer = OpenListPalette.PrimaryDeep,
    onPrimaryContainer = OpenListPalette.OnPrimary,
    background = OpenListPalette.DarkCanvas,
    onBackground = OpenListPalette.DarkInk,
    surface = OpenListPalette.DarkSurface,
    onSurface = OpenListPalette.DarkInk,
    surfaceVariant = OpenListPalette.DarkSurfaceSoft,
    onSurfaceVariant = OpenListPalette.DarkSlate,
    outline = OpenListPalette.DarkHairlineStrong,
    outlineVariant = OpenListPalette.DarkHairline,
    error = OpenListPalette.Error,
    onError = OpenListPalette.OnPrimary,
)

/** Semantic colors outside Material3's default ColorScheme slots (success/warning). */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

val LightExtendedColors = ExtendedColors(
    success = OpenListPalette.Success,
    onSuccess = OpenListPalette.OnPrimary,
    warning = OpenListPalette.Warning,
    onWarning = OpenListPalette.OnPrimary,
)

val DarkExtendedColors = ExtendedColors(
    success = OpenListPalette.Success,
    onSuccess = OpenListPalette.InkDeep,
    warning = OpenListPalette.Warning,
    onWarning = OpenListPalette.InkDeep,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
