package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.example.ui.screens.GameSessionScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                NexusApp(viewModel = viewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Forward to Gamepad Bridge if in streaming session
        if (viewModel.activeSession.value != null) {
            viewModel.gamepadBridge.onNativeKeyEvent(event)
            // Allow system back button to exit game
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event)
            }
        } else {
            // Dashboard Controller Navigation
            val action = viewModel.controllerManager.handleKeyEvent(event)
            if (action != null) {
                if (viewModel.onControllerAction(action)) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (viewModel.activeSession.value != null) {
            viewModel.gamepadBridge.onNativeMotionEvent(event)
        } else {
            val action = viewModel.controllerManager.handleMotionEvent(event)
            if (action != null) {
                if (viewModel.onControllerAction(action)) {
                    return true
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

@Composable
fun NexusApp(viewModel: LauncherViewModel) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

    if (activeSession != null) {
        GameSessionScreen(
            session = activeSession!!,
            viewModel = viewModel,
            onExitSession = { viewModel.closeActiveSession() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        DashboardScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
