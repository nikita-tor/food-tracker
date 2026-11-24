package com.example.greetingcard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Always the app's own green palette. Wallpaper-based (dynamic) color is deliberately not
 * used, so the app looks the same on every phone and matches its icon.
 */
@Composable
fun GreetingCardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
