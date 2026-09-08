package app.votify.mobile.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.votify.mobile.data.AppTheme

private fun VotifyPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = surfaceBase,
        onBackground = textSecondary,
        surface = surfaceBase,
        onSurface = textSecondary,
        surfaceVariant = surfaceContainerHigh,
        onSurfaceVariant = textMuted,
        surfaceTint = secondary,
        surfaceDim = surfaceBase,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        outline = outline,
        outlineVariant = borderSubtle,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        scrim = pitchBlack,
    )
}

/** Corner radius system: sub-elements 8, cards 16, sheets/mini-player 24, pills = full. */
val VotifyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun VotifyTheme(theme: AppTheme = AppTheme.OledBlack, content: @Composable () -> Unit) {
    val palette = when (theme) {
        AppTheme.OledBlack -> OledBlackPalette
        AppTheme.Graphite -> GraphitePalette
        AppTheme.System -> if (isSystemInDarkTheme()) OledBlackPalette else LightPalette
    }
    SystemBarsEffect(darkTheme = palette.isDark)
    CompositionLocalProvider(LocalVotifyPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = VotifyTypography,
            shapes = VotifyShapes,
            content = content,
        )
    }
}

/** Keeps status/navigation bar icons readable when the palette flips between dark and light. */
@Composable
private fun SystemBarsEffect(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull() ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
