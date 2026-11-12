package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.example.data.model.GameItem
import com.example.data.model.GameType
import java.io.File
import java.io.FileOutputStream

/** Stock Moonlight and its common forks. Their pinned shortcuts are game tiles. */
val MOONLIGHT_PACKAGES = listOf(
    "com.limelight",
    "com.limelight.noir",
    "com.limelight.unofficial",
    "com.limelight.noirdebug",
    "com.limelight.root"
)

/**
 * Turns system pinned shortcuts into library entries.
 *
 * Shortcuts pinned by other apps (for example Moonlight's "Add to home screen") arrive
 * through [com.example.PinShortcutActivity]. Shortcuts published by Moonlight, or by any
 * app the system classifies as a game, become game tiles; everything else becomes an app
 * shortcut. Launching uses [LauncherApps.startShortcut] so the publisher's original intent
 * (including extras) is preserved.
 */
object PinnedShortcutImporter {
    /** All ids created here start with this so stale entries can be reconciled safely. */
    const val ID_PREFIX = "pinned_"

    @RequiresApi(Build.VERSION_CODES.O)
    fun fromPinnedShortcut(context: Context, shortcut: ShortcutInfo): GameItem {
        val packageManager = context.packageManager
        val publisher = shortcut.getPackage()

        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(publisher, 0)
            ).toString()
        }.getOrDefault(publisher)

        val isGameApp = runCatching {
            packageManager.getApplicationInfo(publisher, 0).category ==
                ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
        val isGame = publisher in MOONLIGHT_PACKAGES || isGameApp

        val title = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: appLabel

        val launchIntent = runCatching {
            shortcut.intent?.toUri(Intent.URI_INTENT_SCHEME)
        }.getOrNull()

        return GameItem(
            id = "$ID_PREFIX${publisher}_${shortcut.id}",
            title = title,
            type = GameType.SHORTCUT,
            isGame = isGame,
            packageName = publisher,
            // For pinned shortcuts this field holds the shortcut id, which is what
            // LauncherApps.startShortcut needs to launch it.
            activityName = shortcut.id,
            launchUrl = launchIntent,
            iconUrl = saveShortcutIcon(context, publisher, shortcut),
            // Marks the row as created by a pin request on this device. Imported copies
            // carry isPinned = false so cleanup and launch logic can tell them apart.
            isPinned = true,
            genre = if (isGame) "Game Shortcut" else "App Shortcut",
            publisher = appLabel,
            description = "Pinned shortcut from $appLabel",
            tags = if (isGame) "Shortcut,Game,Pinned" else "Shortcut,App,Pinned"
        )
    }

    /**
     * Every shortcut currently pinned to this launcher, as (publisher, shortcut id).
     * Returns null when the launcher is not allowed to read its pinned shortcuts (for
     * example while another launcher is the default one), so callers can skip clean-up
     * instead of deleting everything.
     */
    fun pinnedShortcutKeys(context: Context): Set<Pair<String, String>>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            // Only the current launcher may enumerate pinned shortcuts. Bail out rather
            // than reporting an empty set while another launcher is active.
            if (launcherApps == null || !launcherApps.hasShortcutHostPermission()) {
                return null
            }
            // ShortcutManager only exposes the calling package's own shortcuts, so
            // launchers must go through LauncherApps to see everything pinned to them.
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            launcherApps.getShortcuts(query, Process.myUserHandle())
                .orEmpty()
                .mapNotNull { info -> info.getPackage()?.let { it to info.id } }
                .toSet()
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveShortcutIcon(
        context: Context,
        publisher: String,
        shortcut: ShortcutInfo
    ): String? = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return null
        val drawable = launcherApps.getShortcutIconDrawable(
            shortcut,
            context.resources.displayMetrics.densityDpi
        ) ?: return null

        val size = maxOf(192, maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)

        val dir = File(context.filesDir, "shortcuts").apply { mkdirs() }
        val file = File(
            dir,
            "${sanitize(publisher)}_${sanitize(shortcut.id)}.png"
        )
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
