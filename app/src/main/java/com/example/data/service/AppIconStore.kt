package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders installed-app icons to PNG files in app-private storage.
 *
 * Most modern app icons are adaptive-icon XML. The old `android.resource://` URLs handed
 * to Coil could not decode those, so tiles in the Games and Apps tabs stayed blank or fell
 * back to a letter. Rendering the drawable once and caching it as a file gives every
 * surface a stable `file://` URL that Coil can decode, memory-cache and disk-cache like
 * any other image.
 *
 * Files are keyed by package and `lastUpdateTime`, so an app update produces a new file
 * and the stale one is removed.
 */
object AppIconStore {
    private const val DIR = "app_icons"
    private const val ICON_SIZE_PX = 192

    suspend fun iconUrl(
        context: Context,
        packageName: String,
        versionStamp: Long
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val safePackage = sanitize(packageName)
            val file = File(dir, "$safePackage-$versionStamp.png")

            if (!file.exists() || file.length() == 0L) {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(
                    ICON_SIZE_PX,
                    ICON_SIZE_PX,
                    Bitmap.Config.ARGB_8888
                )
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            }

            // Icons cached for a previous version of this package are no longer referenced.
            dir.listFiles { candidate ->
                candidate.name.startsWith("$safePackage-") && candidate != file
            }?.forEach { it.delete() }

            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
