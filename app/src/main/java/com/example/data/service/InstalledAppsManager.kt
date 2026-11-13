package com.example.data.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.HomeOverrideService
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app that can be used as the weather shortcut target. */
data class WeatherApp(
    val packageName: String,
    val label: String
)

class InstalledAppsManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    private fun queryLauncherActivities(): List<ResolveInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
    }

    /**
     * Query all launchable apps on the device.
     */
    suspend fun getInstalledApps(): List<GameItem> = withContext(Dispatchers.IO) {
        val resolveInfos = queryLauncherActivities()
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

            // Render the app icon to a cached PNG. The package resource URI cannot be
            // decoded by Coil for adaptive icons, which left tiles blank.
            val versionStamp = packageVersionStamp(pkg)
            val iconUri = AppIconStore.iconUrl(context, pkg, versionStamp)

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
     * Launchable weather apps, used by the favorites weather shortcut and its Settings
     * picker. There is no public weather intent in the Android SDK, so the list is built
     * from known weather packages plus packages/labels that identify themselves as weather.
     */
    suspend fun getWeatherApps(): List<WeatherApp> = withContext(Dispatchers.IO) {
        val myPackageName = context.packageName
        queryLauncherActivities()
            .asSequence()
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == myPackageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString()
                val normalizedPackage = pkg.lowercase()
                val normalizedLabel = label.lowercase()
                val looksLikeWeather =
                    pkg in WEATHER_PACKAGES ||
                        WEATHER_PACKAGE_HINTS.any { normalizedPackage.contains(it) } ||
                        WEATHER_LABEL_HINTS.any { normalizedLabel.contains(it) }
                if (!looksLikeWeather) null else WeatherApp(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
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

    /**
     * Launch a Moonlight game shortcut on a device where the shortcut is not pinned.
     *
     * Moonlight builds shortcut ids as `<PC UUID><App ID>` and its exported
     * `ShortcutTrampoline` activity accepts the PC UUID and app id as string extras. The
     * serialized pinned-shortcut intent cannot always be relied on after it has travelled
     * through an export file, so rebuilding the intent from the id keeps imported
     * shortcuts opening the game instead of Moonlight's home screen.
     */
    fun launchMoonlightShortcut(
        packageName: String,
        shortcutId: String,
        appName: String? = null
    ): Boolean {
        val intent = buildMoonlightShortcutIntent(packageName, shortcutId, appName)
            ?: return false
        return try {
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
     * Returns true when [packageName] is protected by the OS or device policy and cannot be
     * uninstalled by the user according to standard Android APIs:
     * - User/parental restrictions (`DISALLOW_UNINSTALL_APPS` or `DISALLOW_APPS_CONTROL`)
     * - Active device administrators (`DevicePolicyManager.getActiveAdmins`)
     * - Pre-installed system/persistent flags (`FLAG_SYSTEM`, `FLAG_UPDATED_SYSTEM_APP`,
     *   `FLAG_PERSISTENT`), which cover Fire OS / OEM built-in apps.
     */
    fun isProtectedApp(packageName: String): Boolean {
        if (packageName == context.packageName) return true

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val userRestricted = runCatching {
            userManager?.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS) == true ||
                userManager?.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL) == true
        }.getOrDefault(false)
        if (userRestricted) return true

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isActiveAdmin = runCatching {
            dpm?.activeAdmins?.any { it.packageName == packageName } == true
        }.getOrDefault(false)
        if (isActiveAdmin) return true

        val appInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        } catch (_: Exception) {
            return false
        }

        return isProtectedApplicationFlags(appInfo.flags)
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

    /** Last update time of a package, used to invalidate its cached icon. */
    private fun packageVersionStamp(packageName: String): Long = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0L)
            ).lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }
    } catch (_: Exception) {
        0L
    }

    /** True when this app is the system's current default home/launcher app. */
    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** True when the optional home-override accessibility service is enabled. */
    fun isHomeOverrideEnabled(): Boolean {
        if (HomeOverrideService.isConnected) return true
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager ?: return false
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo?.serviceInfo
            if (service != null) {
                service.packageName == context.packageName &&
                    service.name == HomeOverrideService::class.java.name
            } else {
                info.id?.startsWith("${context.packageName}/") == true &&
                    info.id.contains(HomeOverrideService::class.java.simpleName)
            }
        }
    }

    /** Open the system accessibility settings so the user can enable the service. */
    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}

/**
 * Rebuilds the intent Moonlight's `ShortcutTrampoline` needs to launch one PC game.
 *
 * Moonlight builds shortcut ids as `<PC UUID><App ID>` and puts the same values on the
 * pinned shortcut's intent. Only the id travels through the sync file, so the extras are
 * rebuilt here. Returns null when the id is not a Moonlight game shortcut (an id for a PC
 * itself is just the bare UUID, and that opens Moonlight's app list).
 */
internal fun buildMoonlightShortcutIntent(
    packageName: String,
    shortcutId: String,
    appName: String? = null
): Intent? {
    if (shortcutId.length <= UUID_LENGTH) return null
    val uuid = shortcutId.substring(0, UUID_LENGTH)
    if (!UUID_PATTERN.matches(uuid)) return null
    val appId = shortcutId.substring(UUID_LENGTH)
    if (appId.toIntOrNull() == null) return null

    return Intent(Intent.ACTION_DEFAULT).apply {
        component = ComponentName(packageName, MOONLIGHT_TRAMPOLINE)
        putExtra("UUID", uuid)
        // The app name is optional while AppId is present, but Moonlight keeps it in the
        // game session for the title overlay and status notifications.
        appName?.takeIf { it.isNotBlank() }?.let { putExtra("AppName", it) }
        putExtra("AppId", appId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Known launchable weather apps across common Android vendors. */
private val WEATHER_PACKAGES = setOf(
    "com.google.android.apps.weather",
    "com.sec.android.app.weather",
    "com.weather.Weather",
    "com.accuweather.android",
    "com.wunderground.android.weather",
    "com.yahoo.mobile.client.android.weather",
    "com.miui.weather2",
    "com.coloros.weather2",
    "com.oneplus.weather",
    "com.huawei.android.totemweather",
    "com.motorola.weather",
    "com.asus.weather"
)

private val WEATHER_PACKAGE_HINTS = listOf("weather", "meteo", "forecast", "accuweather")
private val WEATHER_LABEL_HINTS = listOf("weather", "meteo", "forecast", "accuweather")

/** Moonlight launches pinned games through this activity. */
private const val MOONLIGHT_TRAMPOLINE = "com.limelight.ShortcutTrampoline"

private const val UUID_LENGTH = 36

private val UUID_PATTERN = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

internal fun isProtectedApplicationFlags(flags: Int): Boolean =
    (flags and PROTECTED_APP_FLAGS) != 0

private const val PROTECTED_APP_FLAGS =
    ApplicationInfo.FLAG_SYSTEM or
        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or
        ApplicationInfo.FLAG_PERSISTENT
