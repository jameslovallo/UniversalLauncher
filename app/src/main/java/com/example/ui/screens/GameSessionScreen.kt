package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ActiveGameSession
import com.example.ui.viewmodel.LauncherViewModel
import com.example.webview.GeForceNowWebView
import com.example.webview.TouchGamepadOverlay

@Composable
fun GameSessionScreen(
    session: ActiveGameSession,
    viewModel: LauncherViewModel,
    onExitSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHud by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var webTitle by remember { mutableStateOf(session.game.title) }

    val showTouchControls by viewModel.showTouchControlsInSession.collectAsStateWithLifecycle()

    BackHandler {
        showExitDialog = true
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Fullscreen In-App WebView with HTML5 Gamepad injection
        GeForceNowWebView(
            url = session.playUrl,
            gamepadBridge = viewModel.gamepadBridge,
            onTitleChanged = { title ->
                if (title.isNotBlank()) webTitle = title
            },
            modifier = Modifier.fillMaxSize()
        )

        // On-Screen Virtual Touch Gamepad (if enabled)
        if (showTouchControls) {
            TouchGamepadOverlay(
                gamepadBridge = viewModel.gamepadBridge,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f
            )
        }

        // Floating Quick Menu Trigger Button (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            IconButton(
                onClick = { showHud = !showHud },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                    .testTag("session_menu_toggle")
            ) {
                Icon(
                    imageVector = if (showHud) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = "Toggle Game HUD",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Animated In-Game Console HUD Overlay
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Game title badge
                Column {
                    Text(
                        text = session.game.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "GeForce NOW Cloud Stream",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Toggle Touch Controls
                IconButton(
                    onClick = { viewModel.toggleTouchControlsInSession() },
                    modifier = Modifier.size(34.dp).testTag("toggle_touch_gamepad_button")
                ) {
                    Icon(
                        imageVector = if (showTouchControls) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Virtual Controller",
                        tint = if (showTouchControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Exit Session Button
                Button(
                    onClick = { showExitDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("session_exit_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exit to Dashboard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Exit confirmation dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = {
                    Text(
                        text = "Exit Game Session?",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Return to Nexus Launcher dashboard? Your cloud stream session on GeForce NOW will be closed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            onExitSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Exit Game", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showExitDialog = false }) {
                        Text("Keep Playing", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
