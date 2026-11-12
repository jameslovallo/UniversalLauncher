package com.example

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    private val bHoldHandler = Handler(Looper.getMainLooper())
    private var isHoldingB = false
    private var bHoldTriggered = false
    private val bHoldRunnable = Runnable {
        bHoldTriggered = true
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.openContextMenuForFocusedItem()
    }

    /** Reads the library sync file again after the device is unlocked. */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                viewModel.onDeviceUnlocked()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the device wallpaper show behind the Apps view. The window background must
        // be transparent for FLAG_SHOW_WALLPAPER to be visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        enableEdgeToEdge()

        registerUserPresentReceiver()

        setContent {
            val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
            MyApplicationTheme(style = themeStyle) {
                NexusApp(viewModel = viewModel)
            }
        }
    }

    private fun registerUserPresentReceiver() {
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(userPresentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(userPresentReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        bHoldHandler.removeCallbacks(bHoldRunnable)
        isHoldingB = false
        bHoldTriggered = false
    }

    override fun onDestroy() {
        bHoldHandler.removeCallbacks(bHoldRunnable)
        runCatching { unregisterReceiver(userPresentReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // When the optional home-override service is active, Nexus behaves like the home
        // app itself, so keep it out of the Task Switcher / Recents list.
        updateRecentsVisibility()
    }

    private fun updateRecentsVisibility() {
        val hide = viewModel.isHomeOverrideEnabled()
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        runCatching {
            activityManager.appTasks.forEach { task ->
                task.setExcludeFromRecents(hide)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Controller bumpers switch between the Apps and Games screens.
        val action = viewModel.controllerManager.handleKeyEvent(event)
        if (action != null) {
            if (viewModel.onControllerAction(action)) {
                return true
            }
        }
        // Map controller Button A to DPAD_CENTER so Compose's clickable handles it as a click
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            val dpadCenterEvent = KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                KeyEvent.KEYCODE_DPAD_CENTER,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source,
            )
            if (super.dispatchKeyEvent(dpadCenterEvent)) {
                return true
            }
        }

        // Controller Button B handling:
        // - Holding B opens the currently focused app or game's context menu.
        // - When any context menu/modal is open, pressing B acts exactly like the native Android Back gesture.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (viewModel.isAnyMenuOrModalOpen()) {
                // If a menu was just opened by holding B, ignore the subsequent ACTION_UP from that hold
                if (bHoldTriggered && (event.action == KeyEvent.ACTION_UP)) {
                    bHoldTriggered = false
                    isHoldingB = false
                    return true
                }
                // When open, pressing B acts exactly like native Back
                val backEvent = KeyEvent(
                    event.downTime,
                    event.eventTime,
                    event.action,
                    KeyEvent.KEYCODE_BACK,
                    event.repeatCount,
                    event.metaState,
                    event.deviceId,
                    event.scanCode,
                    event.flags,
                    event.source,
                )
                if (super.dispatchKeyEvent(backEvent)) {
                    return true
                }
                if (event.action == KeyEvent.ACTION_UP) {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
                return true
            } else {
                // When no context menu or modal is open:
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            isHoldingB = true
                            bHoldTriggered = false
                            bHoldHandler.removeCallbacks(bHoldRunnable)
                            bHoldHandler.postDelayed(
                                bHoldRunnable,
                                ViewConfiguration.getLongPressTimeout().toLong(),
                            )
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        bHoldHandler.removeCallbacks(bHoldRunnable)
                        if (isHoldingB) {
                            isHoldingB = false
                            if (bHoldTriggered) {
                                bHoldTriggered = false
                                return true
                            } else {
                                // Short tap when no menu open: native back behavior
                                val backEvent = KeyEvent(
                                    event.downTime,
                                    event.eventTime,
                                    event.action,
                                    KeyEvent.KEYCODE_BACK,
                                    event.repeatCount,
                                    event.metaState,
                                    event.deviceId,
                                    event.scanCode,
                                    event.flags,
                                    event.source,
                                )
                                if (super.dispatchKeyEvent(backEvent)) {
                                    return true
                                }
                                onBackPressedDispatcher.onBackPressed()
                                return true
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun NexusApp(viewModel: LauncherViewModel) {
    DashboardScreen(
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize()
    )
}
