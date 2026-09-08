package app.votify.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Votify palette — see design/DESIGN-monochrome.md.
 * Strictly achromatic: depth comes from tonal steps + hairline borders, never shadows.
 */
data class VotifyPalette(
    val isDark: Boolean,
    val pitchBlack: Color,
    val surfaceBase: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceBright: Color,
    val borderSubtle: Color,
    val borderProminent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
)

/** "OLED Black" — the primary palette from the Stitch design system. */
val OledBlackPalette = VotifyPalette(
    isDark = true,
    pitchBlack = Color(0xFF000000),
    surfaceBase = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1C1B1B),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF383838),
    surfaceBright = Color(0xFF393939),
    borderSubtle = Color(0xFF2A2A2A),
    borderProminent = Color(0xFF48484A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFE5E5EA),
    textMuted = Color(0xFF8E8E93),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF636565),
    secondary = Color(0xFFC6C6CB),
    onSecondary = Color(0xFF2F3034),
    secondaryContainer = Color(0xFF46464B),
    onSecondaryContainer = Color(0xFFB5B4BA),
    outline = Color(0xFF8E9192),
    outlineVariant = Color(0xFF444748),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE5E2E1),
    inverseOnSurface = Color(0xFF313030),
)

/** "Графит" — same hierarchy, lifted one tonal step so nothing is pure black. */
val GraphitePalette = OledBlackPalette.copy(
    pitchBlack = Color(0xFF0F0F10),
    surfaceBase = Color(0xFF1A1A1C),
    surfaceContainerLowest = Color(0xFF151517),
    surfaceContainerLow = Color(0xFF202023),
    surfaceContainer = Color(0xFF242427),
    surfaceContainerHigh = Color(0xFF2F2F33),
    surfaceContainerHighest = Color(0xFF3B3B40),
    surfaceBright = Color(0xFF454549),
    borderSubtle = Color(0xFF2F2F33),
    borderProminent = Color(0xFF4E4E54),
    textMuted = Color(0xFF9A9AA0),
)

/** Light counterpart used when the theme follows the system and the system is light. */
val LightPalette = VotifyPalette(
    isDark = false,
    pitchBlack = Color(0xFFFFFFFF),
    surfaceBase = Color(0xFFF7F7F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F3),
    surfaceContainer = Color(0xFFEDEDEF),
    surfaceContainerHigh = Color(0xFFE3E3E6),
    surfaceContainerHighest = Color(0xFFD8D8DC),
    surfaceBright = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFE1E1E4),
    borderProminent = Color(0xFFC4C4C9),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF2C2C30),
    textMuted = Color(0xFF6E6E75),
    primary = Color(0xFF111113),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2C2C30),
    onPrimaryContainer = Color(0xFFE3E3E6),
    secondary = Color(0xFF5A5A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDADAE0),
    onSecondaryContainer = Color(0xFF3A3A40),
    outline = Color(0xFF75777A),
    outlineVariant = Color(0xFFC4C6C9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E3132),
    inverseOnSurface = Color(0xFFF0F1F2),
)

val LocalVotifyPalette = staticCompositionLocalOf { OledBlackPalette }

/**
 * Theme-aware color accessors. Keeps the `VotifyColors.TextMuted` call-sites that were written
 * against the original static palette, but now resolves against the active theme.
 */
object VotifyColors {
    private val p: VotifyPalette
        @Composable @ReadOnlyComposable get() = LocalVotifyPalette.current

    val PitchBlack: Color @Composable @ReadOnlyComposable get() = p.pitchBlack
    val SurfaceBase: Color @Composable @ReadOnlyComposable get() = p.surfaceBase
    val SurfaceContainerLowest: Color @Composable @ReadOnlyComposable get() = p.surfaceContainerLowest
    val SurfaceContainerLow: Color @Composable @ReadOnlyComposable get() = p.surfaceContainerLow
    val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = p.surfaceContainer
    val SurfaceContainerHigh: Color @Composable @ReadOnlyComposable get() = p.surfaceContainerHigh
    val SurfaceContainerHighest: Color @Composable @ReadOnlyComposable get() = p.surfaceContainerHighest
    val SurfaceBright: Color @Composable @ReadOnlyComposable get() = p.surfaceBright
    val BorderSubtle: Color @Composable @ReadOnlyComposable get() = p.borderSubtle
    val BorderProminent: Color @Composable @ReadOnlyComposable get() = p.borderProminent
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = p.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = p.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = p.textMuted
    val Primary: Color @Composable @ReadOnlyComposable get() = p.primary
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = p.onPrimary
    val PrimaryContainer: Color @Composable @ReadOnlyComposable get() = p.primaryContainer
    val OnPrimaryContainer: Color @Composable @ReadOnlyComposable get() = p.onPrimaryContainer
    val Secondary: Color @Composable @ReadOnlyComposable get() = p.secondary
    val OnSecondary: Color @Composable @ReadOnlyComposable get() = p.onSecondary
    val SecondaryContainer: Color @Composable @ReadOnlyComposable get() = p.secondaryContainer
    val OnSecondaryContainer: Color @Composable @ReadOnlyComposable get() = p.onSecondaryContainer
    val Outline: Color @Composable @ReadOnlyComposable get() = p.outline
    val OutlineVariant: Color @Composable @ReadOnlyComposable get() = p.outlineVariant
    val Error: Color @Composable @ReadOnlyComposable get() = p.error
    val OnError: Color @Composable @ReadOnlyComposable get() = p.onError
    val ErrorContainer: Color @Composable @ReadOnlyComposable get() = p.errorContainer
    val OnErrorContainer: Color @Composable @ReadOnlyComposable get() = p.onErrorContainer
    val InverseSurface: Color @Composable @ReadOnlyComposable get() = p.inverseSurface
    val InverseOnSurface: Color @Composable @ReadOnlyComposable get() = p.inverseOnSurface
}
