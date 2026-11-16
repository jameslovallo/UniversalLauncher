package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Returns true when the app is running on an Android TV / Leanback device.
 */
fun isTelevisionConfiguration(configuration: Configuration, context: Context? = null): Boolean {
    if ((configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }
    if (context != null) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return true
        }
    }
    return false
}

@Composable
fun isTelevisionMode(): Boolean {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration, context) {
        isTelevisionConfiguration(configuration, context)
    }
}

@Composable
fun shouldShowFocusEffects(controllerConnected: Boolean): Boolean {
    val isTv = isTelevisionMode()
    val inputMode = LocalInputModeManager.current.inputMode
    return isTv || controllerConnected || inputMode == InputMode.Keyboard
}

private fun isSelectKey(key: Key): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Clickable + long-clickable modifier with deterministic D-pad / TV remote Select long-press
 * handling. Short press of Select invokes [onClick] on key-up; holding Select past
 * `longPressTimeoutMillis` (or receiving a long-press key repeat) invokes [onLongClick] once
 * and swallows the trailing key-up so the item is not also launched.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClickableWithLongPress(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    hasIndicationParam: Boolean = false
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val haptics = LocalHapticFeedback.current
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)

    var selectDownJob by remember { mutableStateOf<Job?>(null) }
    var longPressTriggered by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            selectDownJob?.cancel()
            selectDownJob = null
        }
    }

    val clickableMod = if (hasIndicationParam) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            onClick = { latestOnClick() },
            onLongClick = { latestOnLongClick() }
        )
    } else {
        Modifier.combinedClickable(
            onClick = { latestOnClick() },
            onLongClick = { latestOnLongClick() }
        )
    }

    this
        .onFocusChanged { state ->
            if (!state.isFocused) {
                selectDownJob?.cancel()
                selectDownJob = null
                longPressTriggered = false
            }
        }
        .onPreviewKeyEvent { keyEvent ->
            if (!isSelectKey(keyEvent.key)) return@onPreviewKeyEvent false
            val native = keyEvent.nativeKeyEvent
            when (keyEvent.type) {
                KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        selectDownJob?.cancel()
                        longPressTriggered = false
                        selectDownJob = scope.launch {
                            delay(viewConfiguration.longPressTimeoutMillis)
                            longPressTriggered = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestOnLongClick()
                        }
                    } else if (!longPressTriggered &&
                        (native.isLongPress || (native.flags and KeyEvent.FLAG_LONG_PRESS) != 0)
                    ) {
                        selectDownJob?.cancel()
                        selectDownJob = null
                        longPressTriggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestOnLongClick()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    selectDownJob?.cancel()
                    selectDownJob = null
                    val wasLongPress = longPressTriggered
                    longPressTriggered = false
                    if (!wasLongPress) {
                        latestOnClick()
                    }
                    true
                }
                else -> false
            }
        }
        .then(clickableMod)
}

/**
 * Draws a crisp primary focus ring around non-card items (rows, chips, buttons) when focused.
 */
@Composable
fun Modifier.tvFocusBorder(
    interactionSource: MutableInteractionSource,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedWidth: Dp = 2.dp,
    unfocusedColor: Color = Color.Transparent,
    unfocusedWidth: Dp = 0.dp
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    return if (isFocused) {
        this.border(focusedWidth, MaterialTheme.colorScheme.primary, shape)
    } else if (unfocusedWidth > 0.dp) {
        this.border(unfocusedWidth, unfocusedColor, shape)
    } else {
        this
    }
}
