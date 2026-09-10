package app.votify.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

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

/** Accent themes: the OLED dark hierarchy with a colored primary (play buttons, chips, slider). */
private fun accentPalette(primary: Color, onPrimary: Color, container: Color): VotifyPalette =
    OledBlackPalette.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = primary,
        secondary = primary,
        secondaryContainer = container,
    )

val VioletPalette = accentPalette(Color(0xFFA78BFA), Color(0xFF151024), Color(0xFF322A4D))
val AzurePalette = accentPalette(Color(0xFF6AA6FF), Color(0xFF0D1B2E), Color(0xFF27364B))
val EmeraldPalette = accentPalette(Color(0xFF4CD7A0), Color(0xFF06231A), Color(0xFF1F3D33))
val AmberPalette = accentPalette(Color(0xFFF5C044), Color(0xFF241B04), Color(0xFF3F3418))
val RosePalette = accentPalette(Color(0xFFF9708B), Color(0xFF2A0912), Color(0xFF45242E))

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

// ---------------------------------------------------------------------------
// Акцентные заливки
// ---------------------------------------------------------------------------

private fun channelMix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Простая примесь в sRGB: для приглушённых подложек точность цветопередачи не важна. */
private fun mixOf(a: Color, b: Color, t: Float): Color = Color(
    red = channelMix(a.red, b.red, t),
    green = channelMix(a.green, b.green, t),
    blue = channelMix(a.blue, b.blue, t),
    alpha = 1f,
)

/** Насыщенность: 0 для белого/серого/чёрного, заметно больше 0 для цветного акцента. */
private val Color.chroma: Float get() = maxOf(red, green, blue) - minOf(red, green, blue)

private fun Color.brightness(): Float = red * 0.299f + green * 0.587f + blue * 0.114f

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

    /**
     * Заливка крупной круглой кнопки-акцента. Монохромные темы (OLED/графит/светлая) дают
     * белый или тёмный диск, как в исходном дизайне; цветной акцент — приглушённую подложку
     * вместо плашки чистым цветом, которая на OLED выглядит инородным пятном.
     */
    val AccentFill: Color
        @Composable @ReadOnlyComposable get() {
            val primary = p.primary
            return if (primary.chroma < 0.06f) p.textPrimary else mixOf(p.surfaceBase, primary, 0.30f)
        }

    /** Иконка внутри [AccentFill]: сам акцент, осветлённый, если он слишком тёмный. */
    val AccentContent: Color
        @Composable @ReadOnlyComposable get() {
            val primary = p.primary
            if (primary.chroma < 0.06f) return p.surfaceBase
            return if (primary.brightness() < 0.35f) mixOf(primary, Color.White, 0.45f) else primary
        }
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

// ---------------------------------------------------------------------------
// Workshop themes (мастерская тем)
// ---------------------------------------------------------------------------

/** Subset of the PC workshop theme schema (firestore.rules) that mobile applies. */
@kotlinx.serialization.Serializable
data class WorkshopThemeSpec(
    val primary: String = "#FFFFFF",
    val background: String = "#121212",
    val text: String = "#FFFFFF",
    val cards: String = "#1E1E1E",
    val borders: String = "#2A2A2A",
    val focus: String = "#48484A",
    val mode: String = "dark",
    val backgroundPreset: String = "default",
    val backgroundUrl: String = "",
    val cornerRadius: Int = 16,
    val uiTransparency: Int = 100,
    val backgroundBlur: Int = 0,
    val particles: String = "none",
    val fontFamily: String = "inter",
)

private fun parseColorOrNull(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex.trim()))
}.getOrNull()

/** Contrast-safe foreground for an arbitrary accent (workshop themes). */
private fun onColorFor(c: Color): Color =
    if (c.red * 0.299 + c.green * 0.587 + c.blue * 0.114 > 0.55) Color(0xFF101013) else Color(0xFFFFFFFF)

/** Maps a workshop theme onto the Votify palette; falls back to OLED Black on bad values. */
fun WorkshopThemeSpec.toPalette(): VotifyPalette {
    val bg = parseColorOrNull(background) ?: Color(0xFF121212)
    val cards = parseColorOrNull(cards) ?: Color(0xFF1E1E1E)
    val text = parseColorOrNull(text) ?: Color(0xFFFFFFFF)
    val borders = parseColorOrNull(borders) ?: Color(0xFF2A2A2A)
    val focus = parseColorOrNull(focus) ?: Color(0xFF48484A)
    val accent = parseColorOrNull(primary) ?: Color(0xFFFFFFFF)
    val light = mode == "light" || (mode == "system" && false)
    val dark = !light
    // Keep text readable over the author's background.
    val textPrimary = if (dark && text == Color(0xFF000000)) Color(0xFFFFFFFF) else text
    return OledBlackPalette.copy(
        isDark = dark,
        pitchBlack = bg,
        surfaceBase = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = cards,
        surfaceContainer = cards,
        surfaceContainerHigh = focus,
        surfaceContainerHighest = focus,
        surfaceBright = focus,
        borderSubtle = borders,
        borderProminent = focus,
        textPrimary = textPrimary,
        textSecondary = textPrimary.copy(alpha = 0.88f).compositeOver(bg),
        textMuted = textPrimary.copy(alpha = 0.55f).compositeOver(bg),
        primary = accent,
        onPrimary = onColorFor(accent),
        primaryContainer = accent.copy(alpha = 0.22f).compositeOver(bg),
        onPrimaryContainer = accent,
        secondary = accent,
        secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(bg),
    )
}
