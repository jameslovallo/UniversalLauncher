package com.example.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperSeedTest {

    @Test
    fun `a colorful wallpaper yields an accent in the same hue family`() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(24, 64, 200))

        val seed = seedColorFromBitmap(bitmap)
        assertNotNull("A saturated blue wallpaper should produce a seed", seed)

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed!!.toArgb(), hsl)
        assertTrue("Expected a blue hue, got ${hsl[0]}", hsl[0] in 200f..250f)
        assertTrue("Expected a saturated accent, got ${hsl[1]}", hsl[1] > 0.5f)
    }

    @Test
    fun `a greyscale wallpaper falls back instead of inventing a hue`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(128, 128, 128))

        assertNull(seedColorFromBitmap(bitmap))
    }
}
