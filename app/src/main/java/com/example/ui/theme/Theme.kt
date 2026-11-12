package com.example.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline
)

@Composable
fun MyApplicationTheme(
    // A lean-back launcher reads best on a dark Material surface, and it guarantees the
    // Games view fades to a dark background instead of a light one.
    darkTheme: Boolean = true,
    style: ThemeStyle = ThemeStyle.WALLPAPER,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = when {
        style == ThemeStyle.NEUTRAL -> if (darkTheme) DarkColorScheme else LightColorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // System-level Material You.
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            // No system Monet below Android 12: build a scheme from the wallpaper color
            // instead, falling back to the built-in neutral scheme when unavailable.
            val wallpaperScheme = remember(style, darkTheme) {
                wallpaperColorScheme(context, darkTheme)
            }
            wallpaperScheme ?: if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Builds a Material 3 color scheme from the dominant system-wallpaper color.
 *
 * Android only exposes wallpaper colors from API 27, and only exposes a ready-made
 * dynamic scheme from API 31. This bridges the gap for older devices by keeping the
 * wallpaper hue and mapping it onto a small set of tonal roles.
 */
private fun wallpaperColorScheme(context: Context, darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null

    val wallpaperColor: Int = runCatching {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }.getOrNull() ?: return null

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(wallpaperColor, hsl)
    val hue = hsl[0]
    // Tame very saturated or nearly grey wallpapers so text contrast stays readable.
    val saturation = hsl[1].coerceIn(0.28f, 0.65f)

    fun tone(lightness: Float, saturationScale: Float = 1f): Color {
        val color = ColorUtils.HSLToColor(
            floatArrayOf(
                hue,
                (saturation * saturationScale).coerceIn(0f, 1f),
                lightness.coerceIn(0f, 1f)
            )
        )
        return Color(color)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = tone(0.78f),
            onPrimary = tone(0.16f, 0.7f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f, 0.55f),
            secondary = tone(0.72f, 0.5f),
            onSecondary = tone(0.16f, 0.5f),
            secondaryContainer = tone(0.28f, 0.6f),
            onSecondaryContainer = tone(0.90f, 0.4f),
            surface = Color(0xFF111413),
            onSurface = Color(0xFFE1E3DF),
            surfaceVariant = tone(0.22f, 0.45f),
            onSurfaceVariant = tone(0.80f, 0.35f),
            outline = tone(0.58f, 0.4f)
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f, 0.6f),
            onPrimaryContainer = tone(0.16f, 0.8f),
            secondary = tone(0.42f, 0.4f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.90f, 0.4f),
            onSecondaryContainer = tone(0.18f, 0.6f),
            surface = Color(0xFFFBFDFA),
            onSurface = Color(0xFF191C1A),
            surfaceVariant = tone(0.90f, 0.35f),
            onSurfaceVariant = tone(0.30f, 0.4f),
            outline = tone(0.50f, 0.3f)
        )
    }
}
