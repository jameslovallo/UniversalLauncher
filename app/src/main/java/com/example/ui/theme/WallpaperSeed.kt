package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.abs

/** Longest edge used when sampling a wallpaper for its accent color. */
private const val SAMPLE_MAX_DIMENSION = 64

/** Number of hue buckets used to find the wallpaper's dominant accent. */
private const val HUE_BUCKETS = 24

/**
 * Samples the user-picked TV wallpaper and returns an accent color for the dynamic theme.
 *
 * Android TV has no system wallpaper provider, so [ThemeStyle.WALLPAPER] needs the launcher's
 * own wallpaper file to build a matching Material You palette. Returns null while the image is
 * loading, when no wallpaper is selected, or when the image is essentially greyscale (in which
 * case the monochrome fallback reads better).
 */
@Composable
fun rememberWallpaperSeedColor(wallpaperUrl: String?): Color? {
    val context = LocalContext.current
    var seed by remember(wallpaperUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(wallpaperUrl) {
        seed = if (wallpaperUrl.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { wallpaperSeedColor(context, wallpaperUrl) }
        }
    }

    return seed
}

/** Decodes [storedUrl] off the main thread and returns its dominant accent color. */
internal fun wallpaperSeedColor(context: Context, storedUrl: String): Color? =
    runCatching {
        val bitmap = decodeSampledWallpaper(context, storedUrl) ?: return null
        try {
            seedColorFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

private fun decodeSampledWallpaper(context: Context, storedUrl: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openWallpaperStream(context, storedUrl)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (largestEdge <= 0) return null

    var sampleSize = 1
    while (largestEdge / (sampleSize * 2) >= SAMPLE_MAX_DIMENSION) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return openWallpaperStream(context, storedUrl)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

private fun openWallpaperStream(context: Context, storedUrl: String): InputStream? =
    runCatching {
        val uri = Uri.parse(storedUrl)
        when {
            uri.scheme == "file" -> uri.path?.let { FileInputStream(File(it)) }
            uri.scheme.isNullOrEmpty() -> FileInputStream(File(storedUrl))
            else -> context.contentResolver.openInputStream(uri)
        }
    }.getOrNull()

/**
 * Finds the most prominent colorful hue in [bitmap] and returns the average color of that hue.
 *
 * Pixels are weighted by saturation and by how far their lightness is from black or white, so a
 * colourful subject wins over large dark or washed-out areas. Greyscale images return null.
 */
internal fun seedColorFromBitmap(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val stepX = (width / 48).coerceAtLeast(1)
    val stepY = (height / 48).coerceAtLeast(1)
    val weights = FloatArray(HUE_BUCKETS)
    val reds = FloatArray(HUE_BUCKETS)
    val greens = FloatArray(HUE_BUCKETS)
    val blues = FloatArray(HUE_BUCKETS)
    val hsl = FloatArray(3)

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) >= 16) {
                ColorUtils.colorToHSL(pixel, hsl)
                val saturation = hsl[1]
                val lightness = hsl[2]
                val lightnessWeight = 1f - abs(lightness - 0.5f) * 2f
                val weight = (saturation * 1.5f + 0.05f) * (0.25f + 0.75f * lightnessWeight)
                if (weight > 0f) {
                    val bucket = (hsl[0] / (360f / HUE_BUCKETS))
                        .toInt()
                        .coerceIn(0, HUE_BUCKETS - 1)
                    weights[bucket] += weight
                    reds[bucket] += android.graphics.Color.red(pixel) * weight
                    greens[bucket] += android.graphics.Color.green(pixel) * weight
                    blues[bucket] += android.graphics.Color.blue(pixel) * weight
                }
            }
            x += stepX
        }
        y += stepY
    }

    val bucket = weights.indices.maxByOrNull { weights[it] } ?: return null
    val totalWeight = weights[bucket]
    if (totalWeight <= 0f) return null

    val accent = Color(
        red = (reds[bucket] / totalWeight / 255f).coerceIn(0f, 1f),
        green = (greens[bucket] / totalWeight / 255f).coerceIn(0f, 1f),
        blue = (blues[bucket] / totalWeight / 255f).coerceIn(0f, 1f)
    )

    val accentHsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), accentHsl)
    return if (accentHsl[1] < 0.05f) null else accent
}
