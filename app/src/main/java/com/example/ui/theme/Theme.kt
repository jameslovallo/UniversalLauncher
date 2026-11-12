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

@Composable
fun MyApplicationTheme(
    // A lean-back launcher reads best on a dark Material surface, and it guarantees the
    // Games view fades to a dark background instead of a light one.
    darkTheme: Boolean = true,
    style: ThemeStyle = ThemeStyle.WALLPAPER,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = remember(style, darkTheme) {
        when {
            style == ThemeStyle.WALLPAPER &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // System-level Material You.
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            style == ThemeStyle.WALLPAPER -> {
                // No system Monet below Android 12: build a scheme from the wallpaper
                // color instead, falling back to monochrome when unavailable.
                wallpaperColorScheme(context, darkTheme)
                    ?: schemeFromHue(hue = 0f, saturation = 0f, dark = darkTheme)
            }
            else -> paletteColorScheme(style, darkTheme)
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
    // Tame very saturated or nearly grey wallpapers so text contrast stays readable.
    return schemeFromHue(
        hue = hsl[0],
        saturation = hsl[1].coerceIn(0.18f, 0.65f),
        dark = darkTheme
    )
}

/** Builds one of the predefined Material palettes from its seed color. */
private fun paletteColorScheme(style: ThemeStyle, darkTheme: Boolean): ColorScheme {
    if (style == ThemeStyle.MONOCHROME) {
        return schemeFromHue(hue = 0f, saturation = 0f, dark = darkTheme)
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(style.seedColor?.toInt() ?: 0xFF76777A.toInt(), hsl)
    return schemeFromHue(hue = hsl[0], saturation = hsl[1], dark = darkTheme)
}

/**
 * Maps a hue/saturation onto the Material 3 color roles. Lightness is fixed per role,
 * which keeps contrast stable across every palette (including the fully desaturated
 * monochrome one).
 */
private fun schemeFromHue(hue: Float, saturation: Float, dark: Boolean): ColorScheme {
    val sat = saturation.coerceIn(0f, 1f)

    fun tone(lightness: Float, saturationScale: Float = 1f, hueOffset: Float = 0f): Color {
        val shiftedHue = ((hue + hueOffset) % 360f + 360f) % 360f
        val color = ColorUtils.HSLToColor(
            floatArrayOf(
                shiftedHue,
                (sat * saturationScale).coerceIn(0f, 1f),
                lightness.coerceIn(0f, 1f)
            )
        )
        return Color(color)
    }

    return if (dark) {
        val surface = tone(0.06f, 0.25f)
        val onSurface = tone(0.92f, 0.08f)
        darkColorScheme(
            primary = tone(0.80f),
            onPrimary = tone(0.18f, 0.70f),
            primaryContainer = tone(0.32f),
            onPrimaryContainer = tone(0.92f, 0.55f),
            inversePrimary = tone(0.40f),
            secondary = tone(0.75f, 0.45f),
            onSecondary = tone(0.18f, 0.45f),
            secondaryContainer = tone(0.30f, 0.55f),
            onSecondaryContainer = tone(0.92f, 0.35f),
            tertiary = tone(0.78f, 0.55f, 60f),
            onTertiary = tone(0.18f, 0.50f, 60f),
            tertiaryContainer = tone(0.31f, 0.55f, 60f),
            onTertiaryContainer = tone(0.92f, 0.40f, 60f),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = tone(0.24f, 0.35f),
            onSurfaceVariant = tone(0.80f, 0.25f),
            surfaceTint = tone(0.80f),
            inverseSurface = tone(0.92f, 0.08f),
            inverseOnSurface = tone(0.12f, 0.08f),
            outline = tone(0.58f, 0.30f),
            outlineVariant = tone(0.34f, 0.25f),
            scrim = Color.Black,
            surfaceBright = tone(0.24f, 0.25f),
            surfaceDim = tone(0.08f, 0.20f),
            surfaceContainerLowest = tone(0.05f, 0.20f),
            surfaceContainerLow = tone(0.10f, 0.25f),
            surfaceContainer = tone(0.14f, 0.28f),
            surfaceContainerHigh = tone(0.17f, 0.30f),
            surfaceContainerHighest = tone(0.21f, 0.32f)
        )
    } else {
        val surface = tone(0.985f, 0.08f)
        val onSurface = tone(0.12f, 0.10f)
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f, 0.60f),
            onPrimaryContainer = tone(0.16f, 0.80f),
            inversePrimary = tone(0.78f),
            secondary = tone(0.42f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.90f, 0.40f),
            onSecondaryContainer = tone(0.18f, 0.60f),
            tertiary = tone(0.40f, 0.50f, 60f),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.90f, 0.40f, 60f),
            onTertiaryContainer = tone(0.18f, 0.55f, 60f),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = tone(0.90f, 0.35f),
            onSurfaceVariant = tone(0.30f, 0.40f),
            surfaceTint = tone(0.40f),
            inverseSurface = tone(0.18f, 0.10f),
            inverseOnSurface = tone(0.95f, 0.08f),
            outline = tone(0.50f, 0.30f),
            outlineVariant = tone(0.78f, 0.25f),
            scrim = Color.Black,
            surfaceBright = tone(0.99f, 0.06f),
            surfaceDim = tone(0.88f, 0.10f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.97f, 0.10f),
            surfaceContainer = tone(0.94f, 0.14f),
            surfaceContainerHigh = tone(0.92f, 0.18f),
            surfaceContainerHighest = tone(0.90f, 0.22f)
        )
    }
}
