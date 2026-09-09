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

/** Font family / size chosen in the Interface settings, applied on top of the base typography. */
private fun androidx.compose.material3.Typography.withPrefs(
    family: androidx.compose.ui.text.font.FontFamily?,
    scale: Float,
): androidx.compose.material3.Typography {
    fun androidx.compose.ui.text.TextStyle.t() =
        copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale, fontFamily = family ?: fontFamily)
    return androidx.compose.material3.Typography(
        displayLarge = displayLarge.t(), displayMedium = displayMedium.t(), displaySmall = displaySmall.t(),
        headlineLarge = headlineLarge.t(), headlineMedium = headlineMedium.t(), headlineSmall = headlineSmall.t(),
        titleLarge = titleLarge.t(), titleMedium = titleMedium.t(), titleSmall = titleSmall.t(),
        bodyLarge = bodyLarge.t(), bodyMedium = bodyMedium.t(), bodySmall = bodySmall.t(),
        labelLarge = labelLarge.t(), labelMedium = labelMedium.t(), labelSmall = labelSmall.t(),
    )
}

@Composable
fun VotifyTheme(
    theme: AppTheme = AppTheme.OledBlack,
    customThemeJson: String = "",
    prefs: app.votify.mobile.data.CustomPrefs = app.votify.mobile.data.CustomPrefs(),
    content: @Composable () -> Unit,
) {
    val palette = when (theme) {
        AppTheme.OledBlack -> OledBlackPalette
        AppTheme.Graphite -> GraphitePalette
        AppTheme.Violet -> VioletPalette
        AppTheme.Light -> LightPalette
        AppTheme.Azure -> AzurePalette
        AppTheme.Emerald -> EmeraldPalette
        AppTheme.Amber -> AmberPalette
        AppTheme.Rose -> RosePalette
        AppTheme.System -> if (isSystemInDarkTheme()) OledBlackPalette else LightPalette
        AppTheme.Workshop -> parseWorkshopSpec(customThemeJson).toPalette()
    }
    // Interface settings: translucent cards over a background image.
    val effectivePalette = if (prefs.transparentCards) palette.copy(
        surfaceContainerLowest = palette.surfaceContainerLowest.copy(alpha = 0.78f),
        surfaceContainerLow = palette.surfaceContainerLow.copy(alpha = 0.80f),
        surfaceContainer = palette.surfaceContainer.copy(alpha = 0.82f),
        surfaceContainerHigh = palette.surfaceContainerHigh.copy(alpha = 0.86f),
        surfaceContainerHighest = palette.surfaceContainerHighest.copy(alpha = 0.90f),
    ) else palette

    val family = when (prefs.fontFamily) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        "rounded" -> androidx.compose.ui.text.font.FontFamily.Cursive
        else -> null
    }
    val scale = when (prefs.fontScale) {
        "small" -> 0.92f
        "large" -> 1.08f
        else -> 1f
    }

    SystemBarsEffect(darkTheme = palette.isDark)
    CompositionLocalProvider(LocalVotifyPalette provides effectivePalette) {
        MaterialTheme(
            colorScheme = effectivePalette.toColorScheme(),
            typography = if (family == null && scale == 1f) VotifyTypography else VotifyTypography.withPrefs(family, scale),
            shapes = VotifyShapes,
            content = content,
        )
    }
}

/** Parses a stored workshop theme; any parse failure falls back to the stock dark palette. */
fun parseWorkshopSpec(json: String): WorkshopThemeSpec =
    if (json.isBlank()) WorkshopThemeSpec()
    else runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<WorkshopThemeSpec>(json)
    }.getOrDefault(WorkshopThemeSpec())

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
