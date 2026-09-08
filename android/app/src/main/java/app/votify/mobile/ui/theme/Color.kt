package app.votify.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Votify "Monochrome Audio" palette — see design/DESIGN-monochrome.md.
 * Strictly achromatic: depth comes from tonal steps + hairline borders, never shadows.
 */
object VotifyColors {
    val PitchBlack = Color(0xFF000000)
    val SurfaceBase = Color(0xFF121212)
    val SurfaceContainerLowest = Color(0xFF0E0E0E)
    val SurfaceContainerLow = Color(0xFF1C1B1B)
    val SurfaceContainer = Color(0xFF1E1E1E)
    val SurfaceContainerHigh = Color(0xFF2A2A2A)
    val SurfaceContainerHighest = Color(0xFF383838)
    val SurfaceBright = Color(0xFF393939)

    val BorderSubtle = Color(0xFF2A2A2A)
    val BorderProminent = Color(0xFF48484A)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFE5E5EA)
    val TextMuted = Color(0xFF8E8E93)

    val Primary = Color(0xFFFFFFFF)
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFFE2E2E2)
    val OnPrimaryContainer = Color(0xFF636565)

    val Secondary = Color(0xFFC6C6CB)
    val OnSecondary = Color(0xFF2F3034)
    val SecondaryContainer = Color(0xFF46464B)
    val OnSecondaryContainer = Color(0xFFB5B4BA)

    val Outline = Color(0xFF8E9192)
    val OutlineVariant = Color(0xFF444748)

    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)

    val InverseSurface = Color(0xFFE5E2E1)
    val InverseOnSurface = Color(0xFF313030)
}
