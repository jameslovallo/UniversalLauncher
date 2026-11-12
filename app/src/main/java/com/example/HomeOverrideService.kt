package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Optional launcher hijack, modelled on LauncherHijack / accessibility-based TV
 * launchers. When another home app is the system default, pressing Home opens that app;
 * this service notices the window change and brings Nexus forward instead. It never
 * reads window content and is inactive while Nexus is the default launcher.
 *
 * Gesture navigation can keep the system from ever showing another launcher, which is
 * why this is only a fallback for devices that will not let the user pick Nexus.
 */
class HomeOverrideService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var lastPromotionAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackages = resolveLauncherPackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (launcherPackages.isEmpty()) launcherPackages = resolveLauncherPackages()
        if (packageName !in launcherPackages) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPromotionAt < 1_200L) return
        lastPromotionAt = now

        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    override fun onInterrupt() = Unit

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
        return infos.mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName }
            .toSet()
    }
}
