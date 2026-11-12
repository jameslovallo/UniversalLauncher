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

    private val aHoldHandler = Handler(Looper.getMainLooper())
    private var isHoldingA = false
    private var aHoldTriggered = false
    private val aHoldRunnable = Runnable {
        aHoldTriggered = true
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
        aHoldHandler.removeCallbacks(aHoldRunnable)
        isHoldingA = false
        aHoldTriggered = false
    }

    override fun onDestroy() {
        aHoldHandler.removeCallbacks(aHoldRunnable)
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

        // Controller Button A handling:
        // - Holding A opens the currently focused app or game's context menu.
        // - Short pressing A acts as click (mapped to DPAD_CENTER).
        // - When a menu or modal is open, pressing A clicks the selected option immediately.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (viewModel.isAnyMenuOrModalOpen()) {
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
                return true
            }

            // When no menu or modal is open:
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        isHoldingA = true
                        aHoldTriggered = false
                        aHoldHandler.removeCallbacks(aHoldRunnable)
                        aHoldHandler.postDelayed(
                            aHoldRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong(),
                        )
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    aHoldHandler.removeCallbacks(aHoldRunnable)
                    if (isHoldingA) {
                        isHoldingA = false
                        if (aHoldTriggered) {
                            aHoldTriggered = false
                            return true
                        }
                        // Short tap: dispatch click to focused item
                        val downEvent = KeyEvent(
                            event.downTime,
                            event.eventTime,
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            0,
                            event.metaState,
                            event.deviceId,
                            event.scanCode,
                            event.flags,
                            event.source,
                        )
                        val upEvent = KeyEvent(
                            event.downTime,
                            event.eventTime,
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            0,
                            event.metaState,
                            event.deviceId,
                            event.scanCode,
                            event.flags,
                            event.source,
                        )
                        super.dispatchKeyEvent(downEvent)
                        super.dispatchKeyEvent(upEvent)
                        return true
                    }
                }
            }
            return true
        }

        // Controller Button B handling:
        // - B always acts exactly like the native Android Back gesture.
        // - Holding B does nothing special.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
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
