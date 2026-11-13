package com.example

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional launcher override modelled on LauncherHijack Mantis and Projectivy Launcher.
 * When another home app is the system default, pressing Home opens that launcher; this
 * service intercepts the Home action and brings Nexus back to the foreground without
 * requiring default-home permissions:
 *
 *  - [onAccessibilityEvent] watches `TYPE_WINDOW_STATE_CHANGED` for any installed
 *    `CATEGORY_HOME` launcher (plus known stock OEM/TV launchers) while ignoring Recents
 *    and system dialogs.
 *  - [onKeyEvent] intercepts `KEYCODE_HOME` on remotes/controllers when key filtering is
 *    delivered to accessibility services.
 *  - A dynamic [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] receiver handles `homekey` broadcasts
 *    on older Android builds.
 *  - Launches [MainActivity] via [PendingIntent.send] with API 34/35+ Background Activity
 *    Launch opt-ins (falling back to [startActivity]) to avoid Android's 5-second
 *    `stopAppSwitches()` delay after pressing Home, with a single short verification retry
 *    if the system launcher remains active after transition animation.
 */
class HomeOverrideService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var lastLauncherRefreshAt = 0L
    private var lastPromotionAt = 0L
    private var lastObservedPackage: String? = null
    private var homeReceiverRegistered = false
    private var packageReceiverRegistered = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val verifyPromotionRunnable = Runnable {
        if (isConnected && lastObservedPackage in launcherPackages && !isSelfDefaultLauncher()) {
            launchNexusHome()
        }
    }

    private val homeKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            @Suppress("DEPRECATION")
            val reason = intent.getStringExtra(EXTRA_REASON)
            if (reason == REASON_HOME_KEY) promoteIfNeeded(allowRetry = true)
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            launcherPackages = resolveLauncherPackages()
            lastLauncherRefreshAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        refreshLauncherPackages(force = true)
        registerHomeReceiver()
        registerPackageChangeReceiver()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        mainHandler.removeCallbacks(verifyPromotionRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        mainHandler.removeCallbacks(verifyPromotionRunnable)
        unregisterHomeReceiver()
        unregisterPackageChangeReceiver()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastObservedPackage = pkg

        if (pkg == packageName) {
            mainHandler.removeCallbacks(verifyPromotionRunnable)
            return
        }

        if (pkg in IGNORED_SYSTEM_PACKAGES) {
            mainHandler.removeCallbacks(verifyPromotionRunnable)
            return
        }

        refreshLauncherPackages(force = false)
        if (pkg !in launcherPackages) {
            mainHandler.removeCallbacks(verifyPromotionRunnable)
            return
        }

        val className = event.className?.toString().orEmpty()
        if (shouldIgnoreLauncherWindowClass(className)) {
            mainHandler.removeCallbacks(verifyPromotionRunnable)
            return
        }

        promoteIfNeeded(allowRetry = true)
    }

    /**
     * Hardware/remote Home button. Consumes short KEYCODE_HOME presses when Nexus is not
     * already the system default launcher so the stock home screen does not flash first.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (isSelfDefaultLauncher()) return false
        if (event.repeatCount > 0 || (event.flags and KeyEvent.FLAG_LONG_PRESS) != 0) {
            return false
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> {
                if (!event.isCanceled) {
                    promoteIfNeeded(allowRetry = true)
                }
                true
            }
            else -> false
        }
    }

    override fun onInterrupt() = Unit

    private fun promoteIfNeeded(allowRetry: Boolean) {
        if (isSelfDefaultLauncher()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPromotionAt < PROMOTION_DEBOUNCE_MS) return
        lastPromotionAt = now

        launchNexusHome()

        if (allowRetry) {
            mainHandler.removeCallbacks(verifyPromotionRunnable)
            mainHandler.postDelayed(verifyPromotionRunnable, RETRY_VERIFY_DELAY_MS)
        }
    }

    private fun launchNexusHome() {
        val intent = buildHomeLaunchIntent(this)
        val launchedViaPendingIntent = runCatching {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_HOME,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val options = buildBackgroundStartOptionsBundle()
            if (options != null) {
                pendingIntent.send(this, 0, null, null, null, null, options)
            } else {
                pendingIntent.send()
            }
            true
        }.getOrDefault(false)

        if (!launchedViaPendingIntent) {
            runCatching { startActivity(intent) }
        }
    }

    private fun buildBackgroundStartOptionsBundle() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    pendingIntentCreatorBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
            }.toBundle()
        } else {
            null
        }

    private fun refreshLauncherPackages(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (force || launcherPackages.isEmpty() || now - lastLauncherRefreshAt >= LAUNCHER_REFRESH_MS) {
            launcherPackages = resolveLauncherPackages()
            lastLauncherRefreshAt = now
        }
    }

    private fun registerHomeReceiver() {
        if (homeReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(homeKeyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(homeKeyReceiver, filter)
        }
        homeReceiverRegistered = true
    }

    private fun unregisterHomeReceiver() {
        if (!homeReceiverRegistered) return
        runCatching { unregisterReceiver(homeKeyReceiver) }
        homeReceiverRegistered = false
    }

    private fun registerPackageChangeReceiver() {
        if (packageReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
        packageReceiverRegistered = true
    }

    private fun unregisterPackageChangeReceiver() {
        if (!packageReceiverRegistered) return
        runCatching { unregisterReceiver(packageChangeReceiver) }
        packageReceiverRegistered = false
    }

    private fun isSelfDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun resolveLauncherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val discovered = infos.mapNotNull { it.activityInfo?.packageName }
        return (discovered + KNOWN_STOCK_LAUNCHER_PACKAGES)
            .filter { it != packageName && it !in IGNORED_SYSTEM_PACKAGES }
            .toSet()
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        private const val EXTRA_REASON = "reason"
        private const val REASON_HOME_KEY = "homekey"
        private const val REQUEST_CODE_HOME = 4001
        private const val PROMOTION_DEBOUNCE_MS = 250L
        private const val RETRY_VERIFY_DELAY_MS = 350L
        private const val LAUNCHER_REFRESH_MS = 30_000L

        private val IGNORED_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.intentresolver",
            "com.android.settings",
            "com.android.systemui"
        )

        private val KNOWN_STOCK_LAUNCHER_PACKAGES = setOf(
            "com.amazon.firelauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.huawei.android.launcher",
            "com.sony.dtv.osat.launcher",
            "com.tcl.cyberui"
        )

        internal fun buildHomeLaunchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        internal fun shouldIgnoreLauncherWindowClass(className: String): Boolean =
            className.contains("Recents", ignoreCase = true) ||
                className.contains("FallbackHome", ignoreCase = true) ||
                className.contains("Wallpaper", ignoreCase = true)
    }
}
