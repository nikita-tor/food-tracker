package com.example.greetingcard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// The app's own palette: leaf green, with citrus as the "goal met" accent (tertiary).
// Backgrounds match res/values*/colors.xml so the launch screen blends into the app.

val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B30),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1A9),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFFA86A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6FBF1),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF6FBF1),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EB),
    surfaceContainer = Color(0xFFEAEFE5),
    surfaceContainerHigh = Color(0xFFE5EAE0),
    surfaceContainerHighest = Color(0xFFDFE4DA),
    surfaceDim = Color(0xFFD7DBD2),
    surfaceBright = Color(0xFFF6FBF1),
    // Snackbars use the inverse colors; without these they fall back to Material's default purple.
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEDF2E8),
    inversePrimary = Color(0xFF96D58F),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF96D58F),
    onPrimary = Color(0xFF00390D),
    primaryContainer = Color(0xFF12521A),
    onPrimaryContainer = Color(0xFFB1F1A9),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFFFB951),
    onTertiary = Color(0xFF482A00),
    tertiaryContainer = Color(0xFF694300),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF10150F),
    onBackground = Color(0xFFDFE4DA),
    surface = Color(0xFF10150F),
    onSurface = Color(0xFFDFE4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF313630),
    surfaceDim = Color(0xFF10150F),
    surfaceBright = Color(0xFF353A34),
    inverseSurface = Color(0xFFDFE4DA),
    inverseOnSurface = Color(0xFF2D322C),
    inversePrimary = Color(0xFF2E6B30),
)

/** Cards: white on the light mint background, a step lighter than the background in dark mode. */
val ColorScheme.card: Color
    get() = if (background.luminance() > 0.5f) surfaceContainerLowest else surfaceContainer

/**
 * Ruby, for the backup buttons: a bright gem red that stands out from the greens on purpose, so the one
 * thing that protects your data is easy to find. White text on it passes contrast (5.5:1).
 */
val ColorScheme.ruby: Color get() = Color(0xFFD0103A)

/** Ruby for text and outlines on the background: the same red in light mode, lighter in dark mode to stay readable. */
val ColorScheme.rubyText: Color
    get() = if (background.luminance() > 0.5f) Color(0xFFD0103A) else Color(0xFFFF6B81)
