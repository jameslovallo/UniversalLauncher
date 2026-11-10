package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ConsoleColorScheme = darkColorScheme(
    primary = NeonGfnGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = NeonGfnGreenLight,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E7490),
    onSecondaryContainer = ElectricCyanLight,
    tertiary = AccentPurple,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = SlateBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardHighlight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep immersive gaming console palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ConsoleColorScheme,
        typography = Typography,
        content = content
    )
}
