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
    private const val BANNERS_DIR = "app_banners"
    private const val ICON_SIZE_PX = 192
    private const val BANNER_WIDTH_PX = 480
    private const val BANNER_HEIGHT_PX = 270

    /**
     * Snapshot of the icon and banner cache directories.
     *
     * A full installed-app scan asks for one icon and one banner per package. Listing the
     * directories inside every call made the scan quadratic in the number of installed
     * apps, which dominated cold start on TV boxes. Callers that resolve many packages in
     * one pass take a single snapshot and pass it down, so stale-file cleanup works from
     * memory instead of re-reading the whole directory per package.
     */
    class DirectorySnapshot internal constructor(
        private val iconFiles: List<File>,
        private val bannerFiles: List<File>
    ) {
        internal fun iconCandidates(prefix: String): List<File> =
            iconFiles.filter { it.name.startsWith(prefix) }

        internal fun bannerCandidates(prefix: String): List<File> =
            bannerFiles.filter { it.name.startsWith(prefix) }
    }

    /** Captures the current cache contents. Take one per installed-app scan. */
    suspend fun snapshot(context: Context): DirectorySnapshot = withContext(Dispatchers.IO) {
        runCatching {
            DirectorySnapshot(
                iconFiles = File(context.filesDir, DIR).apply { mkdirs() }
                    .listFiles()?.toList().orEmpty(),
                bannerFiles = File(context.filesDir, BANNERS_DIR).apply { mkdirs() }
                    .listFiles()?.toList().orEmpty()
            )
        }.getOrElse { DirectorySnapshot(emptyList(), emptyList()) }
    }

    suspend fun iconUrl(
        context: Context,
        packageName: String,
        versionStamp: Long,
        snapshot: DirectorySnapshot? = null
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

                // Icons cached for a previous version of this package are no longer
                // referenced. Only look for them when a new file was actually written, so
                // the steady-state scan touches nothing.
                staleIconFiles(snapshot, dir, safePackage)
                    .filter { it != file }
                    .forEach { it.delete() }
            }

            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    /**
     * Resolves and caches a 16:9 Android TV banner PNG for [packageName] when the package
     * declares a Leanback/activity/application banner. Returns null when the app has no TV
     * banner (does not fall back to the square app icon).
     */
    suspend fun bannerUrl(
        context: Context,
        packageName: String,
        versionStamp: Long,
        preferredActivityName: String? = null,
        snapshot: DirectorySnapshot? = null
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, BANNERS_DIR).apply { mkdirs() }
            val safePackage = sanitize(packageName)
            val file = File(dir, "$safePackage-$versionStamp.png")

            if (!file.exists() || file.length() == 0L) {
                val drawable = resolveTvBannerDrawable(
                    packageManager = context.packageManager,
                    packageName = packageName,
                    preferredActivityName = preferredActivityName
                )
                if (drawable == null) {
                    staleBannerFiles(snapshot, dir, safePackage).forEach { it.delete() }
                    return@runCatching null
                }
                val bitmap = drawable.toBitmap(
                    BANNER_WIDTH_PX,
                    BANNER_HEIGHT_PX,
                    Bitmap.Config.ARGB_8888
                )
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                staleBannerFiles(snapshot, dir, safePackage)
                    .filter { it != file }
                    .forEach { it.delete() }
            }

            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun staleIconFiles(
        snapshot: DirectorySnapshot?,
        dir: File,
        safePackage: String
    ): List<File> = snapshot?.iconCandidates("$safePackage-")
        ?: dir.listFiles()?.toList().orEmpty().filter { it.name.startsWith("$safePackage-") }

    private fun staleBannerFiles(
        snapshot: DirectorySnapshot?,
        dir: File,
        safePackage: String
    ): List<File> = snapshot?.bannerCandidates("$safePackage-")
        ?: dir.listFiles()?.toList().orEmpty().filter { it.name.startsWith("$safePackage-") }

    internal fun resolveTvBannerDrawable(
        packageManager: android.content.pm.PackageManager,
        packageName: String,
        preferredActivityName: String? = null
    ): android.graphics.drawable.Drawable? {
        val leanbackComponent = runCatching {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)?.component
        }.getOrNull()
        if (leanbackComponent != null) {
            runCatching { packageManager.getActivityBanner(leanbackComponent) }
                .getOrNull()
                ?.let { return it }
        }
        if (!preferredActivityName.isNullOrBlank()) {
            runCatching {
                packageManager.getActivityBanner(
                    android.content.ComponentName(packageName, preferredActivityName)
                )
            }.getOrNull()?.let { return it }
        }
        return runCatching { packageManager.getApplicationBanner(packageName) }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
