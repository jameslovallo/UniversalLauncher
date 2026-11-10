package com.example.webview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun TouchGamepadOverlay(
    gamepadBridge: GamepadJsBridge,
    modifier: Modifier = Modifier,
    alpha: Float = 0.65f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Shoulder Buttons (L1, L2, R1, R2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadButton("L1", alpha, 4, gamepadBridge, width = 64, height = 36)
                GamepadButton("L2", alpha, 6, gamepadBridge, width = 64, height = 36)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GamepadButton("SELECT", alpha, 8, gamepadBridge, width = 70, height = 32)
                GamepadButton("START", alpha, 9, gamepadBridge, width = 70, height = 32)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadButton("R2", alpha, 7, gamepadBridge, width = 64, height = 36)
                GamepadButton("R1", alpha, 5, gamepadBridge, width = 64, height = 36)
            }
        }

        // Bottom Left: D-Pad and Left Thumbstick
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VirtualJoystick(
                label = "LS",
                alpha = alpha,
                axisXIndex = 0,
                axisYIndex = 1,
                clickButtonIndex = 10,
                gamepadBridge = gamepadBridge
            )
            VirtualDpad(gamepadBridge = gamepadBridge, alpha = alpha)
        }

        // Bottom Right: Right Thumbstick and ABXY Cluster
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VirtualJoystick(
                label = "RS",
                alpha = alpha,
                axisXIndex = 2,
                axisYIndex = 3,
                clickButtonIndex = 11,
                gamepadBridge = gamepadBridge
            )
            AbxyCluster(gamepadBridge = gamepadBridge, alpha = alpha)
        }
    }
}

@Composable
private fun GamepadButton(
    label: String,
    alpha: Float,
    buttonIndex: Int,
    gamepadBridge: GamepadJsBridge,
    width: Int = 48,
    height: Int = 48,
    accentColor: Color = Color.White
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPressed) accentColor.copy(alpha = alpha * 0.9f)
                else Color.Black.copy(alpha = alpha * 0.5f)
            )
            .border(
                width = 1.5.dp,
                color = if (isPressed) accentColor else Color.White.copy(alpha = alpha * 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(buttonIndex) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        gamepadBridge.updateVirtualButton(buttonIndex, true)
                        tryAwaitRelease()
                        isPressed = false
                        gamepadBridge.updateVirtualButton(buttonIndex, false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) Color.Black else Color.White
        )
    }
}

@Composable
private fun AbxyCluster(
    gamepadBridge: GamepadJsBridge,
    alpha: Float
) {
    Box(modifier = Modifier.size(130.dp)) {
        // Y button (top, button 3)
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GamepadButton("Y", alpha, 3, gamepadBridge, 42, 42, Color(0xFFFBBF24))
        }
        // X button (left, button 2)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            GamepadButton("X", alpha, 2, gamepadBridge, 42, 42, Color(0xFF60A5FA))
        }
        // B button (right, button 1)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadButton("B", alpha, 1, gamepadBridge, 42, 42, Color(0xFFF87171))
        }
        // A button (bottom, button 0)
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadButton("A", alpha, 0, gamepadBridge, 42, 42, Color(0xFF34D399))
        }
    }
}

@Composable
private fun VirtualDpad(
    gamepadBridge: GamepadJsBridge,
    alpha: Float
) {
    Box(modifier = Modifier.size(120.dp)) {
        // Up (button 12)
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GamepadButton("▲", alpha, 12, gamepadBridge, 38, 38)
        }
        // Left (button 14)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            GamepadButton("◀", alpha, 14, gamepadBridge, 38, 38)
        }
        // Right (button 15)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadButton("▶", alpha, 15, gamepadBridge, 38, 38)
        }
        // Down (button 13)
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadButton("▼", alpha, 13, gamepadBridge, 38, 38)
        }
    }
}

@Composable
private fun VirtualJoystick(
    label: String,
    alpha: Float,
    axisXIndex: Int,
    axisYIndex: Int,
    clickButtonIndex: Int,
    gamepadBridge: GamepadJsBridge
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val maxRadius = 45f

    Box(
        modifier = Modifier
            .size(105.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = alpha * 0.4f))
            .border(2.dp, Color.White.copy(alpha = alpha * 0.6f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = {
                        offsetX = 0f
                        offsetY = 0f
                        gamepadBridge.updateVirtualAxis(axisXIndex, 0f)
                        gamepadBridge.updateVirtualAxis(axisYIndex, 0f)
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                        gamepadBridge.updateVirtualAxis(axisXIndex, 0f)
                        gamepadBridge.updateVirtualAxis(axisYIndex, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = offsetX + dragAmount.x
                        val newY = offsetY + dragAmount.y
                        val distance = sqrt(newX * newX + newY * newY)

                        if (distance <= maxRadius) {
                            offsetX = newX
                            offsetY = newY
                        } else {
                            val angle = Math.atan2(newY.toDouble(), newX.toDouble())
                            offsetX = (maxRadius * cos(angle)).toFloat()
                            offsetY = (maxRadius * sin(angle)).toFloat()
                        }

                        // Normalize to -1.0 .. 1.0
                        gamepadBridge.updateVirtualAxis(axisXIndex, offsetX / maxRadius)
                        gamepadBridge.updateVirtualAxis(axisYIndex, offsetY / maxRadius)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Thumb knob
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha * 0.85f))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}
