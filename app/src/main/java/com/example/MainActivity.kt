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
        // Controller bumpers switch between the Apps and Games screens.
        val action = viewModel.controllerManager.handleKeyEvent(event)
        if (action != null) {
            if (viewModel.onControllerAction(action)) {
                return true
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
