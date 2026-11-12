package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the device wallpaper show behind the Apps view. The window background must
        // be transparent for FLAG_SHOW_WALLPAPER to be visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        enableEdgeToEdge()

        setContent {
            val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
            MyApplicationTheme(style = themeStyle) {
                NexusApp(viewModel = viewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Dashboard Controller Navigation
        val action = viewModel.controllerManager.handleKeyEvent(event)
        if (action != null) {
            if (viewModel.onControllerAction(action)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val action = viewModel.controllerManager.handleMotionEvent(event)
        if (action != null) {
            if (viewModel.onControllerAction(action)) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

@Composable
fun NexusApp(viewModel: LauncherViewModel) {
    DashboardScreen(
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize()
    )
}
