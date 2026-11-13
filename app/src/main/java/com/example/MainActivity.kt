package com.example

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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

    override fun onDestroy() {
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
        // Controller buttons Nexus handles directly: bumpers switch tabs, X opens the
        // focused item's context menu, Back opens the Add modal, and Start opens Settings.
        val action = viewModel.controllerManager.handleKeyEvent(event)
        if (action != null) {
            if (viewModel.onControllerAction(action)) {
                return true
            }
        }

        // Controller Button A handling: A acts as a click (mapped to DPAD_CENTER) both
        // for a focused library item and for the selected option in an open menu/dialog.
        // The context menu is opened with the X button (see ControllerManager), so a
        // press of A never opens a context menu or waits for a long-press.
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
