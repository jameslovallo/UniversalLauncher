package com.example

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional launcher hijack, modelled on LauncherHijack / accessibility-based TV
 * launchers. When another home app is the system default, pressing Home opens that app;
 * this service catches the Home action through three independent paths and brings Nexus
 * forward instead:
 *
 *  - [onKeyEvent] for the hardware/software Home key (requires key-event filtering),
 *  - [onAccessibilityEvent] for the window change when another launcher opens,
 *  - a dynamic [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] receiver for the `homekey` reason,
 *    which is how most devices report Home presses that bypass key filtering.
 *
 * It never reads window content and is inactive while Nexus is the default launcher.
 */
class HomeOverrideService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var lastPromotionAt = 0L
    private var receiverRegistered = false

    private val homeKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            @Suppress("DEPRECATION")
            val reason = intent.getStringExtra(EXTRA_REASON)
            if (reason == REASON_HOME_KEY) promoteIfNeeded()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackages = resolveLauncherPackages()
        registerHomeReceiver()
    }

    override fun onDestroy() {
        unregisterHomeReceiver()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (launcherPackages.isEmpty()) launcherPackages = resolveLauncherPackages()
        if (packageName !in launcherPackages) return
        promoteIfNeeded()
    }

    /**
     * Hardware Home button. Accessibility key filtering is only delivered when the
     * service declares `canRequestFilterKeyEvents` and `flagRequestFilterKeyEvents`.
     * Some vendor builds still route Home straight to the system launcher, so the
     * broadcast receiver above covers that case.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action != KeyEvent.ACTION_UP) return false
        // Never swallow Home when Nexus is already the system home app; the system
        // handles it normally in that case.
        if (isSelfDefaultLauncher()) return false
        promoteIfNeeded()
        return true
    }

    override fun onInterrupt() = Unit

    private fun promoteIfNeeded() {
        // Nothing to do when Nexus already is the system home app.
        if (isSelfDefaultLauncher()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPromotionAt < PROMOTION_DEBOUNCE_MS) return
        lastPromotionAt = now

        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        )
    }

    private fun registerHomeReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(homeKeyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(homeKeyReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterHomeReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(homeKeyReceiver) }
        receiverRegistered = false
    }

    private fun isSelfDefaultLauncher(): Boolean {
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
        return infos.mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName }
            .toSet()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val REASON_HOME_KEY = "homekey"
        private const val PROMOTION_DEBOUNCE_MS = 1_200L
    }
}
