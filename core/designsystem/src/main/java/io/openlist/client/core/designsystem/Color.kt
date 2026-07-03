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

    // colors.card-tint-* — pastel plate/chip backgrounds. Paired with the deep
    // "badge-tag" partner inks below (DESIGN.md badge-tag-purple/-orange/-green
    // pattern, extended to the remaining tints with same-hue deep partners).
    val TintPeach = Color(0xFFFFE8D4)
    val TintRose = Color(0xFFFDE0EC)
    val TintMint = Color(0xFFD9F3E1)
    val TintLavender = Color(0xFFE6E0F5)
    val TintSky = Color(0xFFDCECFA)
    val TintYellow = Color(0xFFFEF7D6)
    val TintCream = Color(0xFFF8F5E8)
    val TintGray = Color(0xFFF0EEEC)

    val BrandOrangeDeep = Color(0xFF793400)
    val BrandPinkDeep = Color(0xFFA02E6D)
    val BrandPurple800 = Color(0xFF391C57)
    val BrandGreen = Color(0xFF1AAE39)
    val BrandBrown = Color(0xFF523410)
    val LinkBlueDeep = Color(0xFF005BAB)

    // colors.brand-navy / on-dark* — hero band family.
    val BrandNavy = Color(0xFF0A1530)
    val BrandNavyMid = Color(0xFF1A2A52)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xFFA4A097)

    // Brand color spectrum, used only as the hero band's sticky-note dot
    // decoration (DESIGN.md hero-band-dark), never as text or surfaces.
    val BrandPink = Color(0xFFFF64C8)
    val BrandOrange = Color(0xFFDD5B00)
    val BrandPurple = Color(0xFF7B3FF2)
    val BrandTeal = Color(0xFF2A9D99)
    val BrandYellow = Color(0xFFF5D75E)

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
