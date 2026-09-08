package app.votify.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MonochromeScheme = darkColorScheme(
    primary = VotifyColors.Primary,
    onPrimary = VotifyColors.OnPrimary,
    primaryContainer = VotifyColors.PrimaryContainer,
    onPrimaryContainer = VotifyColors.OnPrimaryContainer,
    inversePrimary = VotifyColors.OnPrimaryContainer,

    secondary = VotifyColors.Secondary,
    onSecondary = VotifyColors.OnSecondary,
    secondaryContainer = VotifyColors.SecondaryContainer,
    onSecondaryContainer = VotifyColors.OnSecondaryContainer,

    tertiary = VotifyColors.Primary,
    onTertiary = VotifyColors.OnPrimary,
    tertiaryContainer = VotifyColors.PrimaryContainer,
    onTertiaryContainer = VotifyColors.OnPrimaryContainer,

    background = VotifyColors.SurfaceBase,
    onBackground = VotifyColors.TextSecondary,
    surface = VotifyColors.SurfaceBase,
    onSurface = VotifyColors.TextSecondary,
    surfaceVariant = VotifyColors.SurfaceContainerHigh,
    onSurfaceVariant = VotifyColors.TextMuted,
    surfaceTint = VotifyColors.Secondary,

    surfaceDim = VotifyColors.SurfaceBase,
    surfaceBright = VotifyColors.SurfaceBright,
    surfaceContainerLowest = VotifyColors.SurfaceContainerLowest,
    surfaceContainerLow = VotifyColors.SurfaceContainerLow,
    surfaceContainer = VotifyColors.SurfaceContainer,
    surfaceContainerHigh = VotifyColors.SurfaceContainerHigh,
    surfaceContainerHighest = VotifyColors.SurfaceContainerHighest,

    inverseSurface = VotifyColors.InverseSurface,
    inverseOnSurface = VotifyColors.InverseOnSurface,

    outline = VotifyColors.Outline,
    outlineVariant = VotifyColors.BorderSubtle,

    error = VotifyColors.Error,
    onError = VotifyColors.OnError,
    errorContainer = VotifyColors.ErrorContainer,
    onErrorContainer = VotifyColors.OnErrorContainer,

    scrim = VotifyColors.PitchBlack,
)

/** Corner radius system: sub-elements 8, cards 16, sheets/mini-player 24, pills = full. */
val VotifyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun VotifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonochromeScheme,
        typography = VotifyTypography,
        shapes = VotifyShapes,
        content = content,
    )
}
