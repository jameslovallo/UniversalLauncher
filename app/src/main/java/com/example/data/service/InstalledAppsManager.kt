package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    /**
     * Query all launchable apps on the device.
     */
    suspend fun getInstalledApps(): List<GameItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0L)
        } else {
            0
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcherIntent, flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, flags as Int)
        }

        val myPackageName = context.packageName

        resolveInfos.filter { it.activityInfo.packageName != myPackageName }.map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(packageManager).toString()
            val activityName = resolveInfo.activityInfo.name
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val isGameApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
            }

            // Build android resource URI for app icon
            val iconUri = "android.resource://$pkg/${resolveInfo.activityInfo.iconResource}"

            GameItem(
                id = "app_$pkg",
                title = label,
                type = GameType.INSTALLED_APP,
                isGame = isGameApp,
                packageName = pkg,
                activityName = activityName,
                iconUrl = iconUri,
                genre = if (isGameApp) "Installed Game" else "Installed Application",
                publisher = pkg,
                description = "Installed Android app ($label)",
                tags = if (isGameApp) "Installed,Game,Android" else "Installed,Android,App"
            )
        }.sortedBy { it.title.lowercase() }
    }

    /**
     * Launch an installed app by package name.
     */
    fun launchApp(packageName: String): Boolean = try {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * (publisher, shortcut id) for every shortcut pinned to this launcher, or null when
     * the pinned list cannot be read.
     */
    fun getPinnedShortcutKeys(): Set<Pair<String, String>>? =
        PinnedShortcutImporter.pinnedShortcutKeys(context)

    /** Open the Play Store games section, falling back to the web store. */
    fun openPlayStore(): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=games")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.android.vending")
        }
        return try {
            context.startActivity(market)
            true
        } catch (_: Exception) {
            openExternally("https://play.google.com/store/apps/category/GAME")
        }
    }

    /**
     * Open a URL in a real browser (or the service's native app).
     *
     * GeForce NOW refuses to stream inside an embedded WebView: its web client only
     * allows full browsers, and Android WebView ships no Widevine CDM for protected
     * media. Handing the deep link to Chrome keeps streaming working.
     *
     * Preference order: native GeForce NOW app -> Chrome -> any browser.
     */
    fun openExternally(url: String): Boolean {
        val uri = Uri.parse(url)
        for (targetPackage in listOf("com.nvidia.geforcenow", "com.android.chrome", null)) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targetPackage != null) setPackage(targetPackage)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Target is not installed or cannot handle the link; try the next one.
            }
        }
        return false
    }

    /**
     * Launch a pinned shortcut through the launcher APIs. This preserves the publisher's
     * original intent, including extras that cannot be serialised into a URI.
     */
    fun launchPinnedShortcut(packageName: String, shortcutId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                ?: return false
            launcherApps.startShortcut(
                packageName,
                shortcutId,
                null,
                null,
                android.os.Process.myUserHandle()
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launch a game from a persisted `intent:` URI.
     *
     * Moonlight-style shortcuts are stored as serialized intents because their launch
     * target is an exported activity (`ShortcutTrampoline`) rather than the launcher
     * activity. Persisting the intent keeps the tile working after this app is no longer
     * the default launcher, unlike pinned shortcuts.
     */
    fun launchIntentUri(uriString: String): Boolean {
        return try {
            val intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Open the device's clock app (used by the favorites clock). */
    fun openClock(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Open the device's calendar app at today (used by the favorites date). */
    fun openCalendar(): Boolean {
        return try {
            val today = System.currentTimeMillis().toString()
            val uri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(today)
                .build()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Expand the system notification shade. Uses the platform StatusBarManager exposed by
     * the launcher role; silently no-ops on devices that restrict it.
     */
    fun expandNotificationShade() {
        try {
            val statusBarManager = context.getSystemService("statusbar") ?: return
            statusBarManager.javaClass
                .getMethod("expandNotificationsPanel")
                .invoke(statusBarManager)
        } catch (_: Exception) {
        }
    }

    /**
     * Launch the system uninstall flow for an installed package.
     *
     * Apps targeting API 28+ must hold [android.Manifest.permission.REQUEST_DELETE_PACKAGES]
     * to use the uninstall intents; without it the intent fails with a SecurityException.
     * The app-info page is used as a last resort so the user can still uninstall.
     */
    fun uninstallApp(packageName: String): Boolean {
        val packageUri = Uri.parse("package:$packageName")
        @Suppress("DEPRECATION")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        val deleteIntent = Intent(Intent.ACTION_DELETE, packageUri)
        for (base in listOf(uninstallIntent, deleteIntent)) {
            val intent = Intent(base).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try the next uninstall action, then fall back to app info.
            }
        }
        openAppInfo(packageName)
        return false
    }

    /**
     * Open the system app-info screen for an installed package.
     */
    fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Open system settings to configure default Home/Launcher app.
     */
    fun openHomeSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
