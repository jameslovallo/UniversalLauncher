package com.example.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class ControllerAction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    CONFIRM_A,
    BACK_B,
    CUSTOMIZE_X,
    FAVORITE_Y,
    PREV_TAB_LB,
    NEXT_TAB_RB,
    MENU_START,
    QUICK_SELECT
}

data class ControllerState(
    val isConnected: Boolean = false,
    val controllerName: String = "No Controller Connected",
    val controllerCount: Int = 0
)

class ControllerManager(context: Context) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val _controllerState = MutableStateFlow(ControllerState())
    val controllerState: StateFlow<ControllerState> = _controllerState.asStateFlow()

    private var lastNavigationTime = 0L
    private val navigationThrottleMs = 130L
    private val joystickDeadzone = 0.55f
    private val joystickReleaseZone = 0.35f

    // The direction the stick is currently deflected towards. A direction is emitted once
    // per deflection; the stick must return near neutral before it can fire again. This
    // stops a noisy analog stick from stepping several items for one push.
    private var activeDirection: ControllerAction? = null

    init {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        refreshControllers()
    }

    fun refreshControllers() {
        val deviceIds = inputManager.inputDeviceIds
        var count = 0
        var primaryName = "No Controller"

        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            if (isGamepad || isJoystick) {
                count++
                if (count == 1) {
                    primaryName = device.name ?: "Game Controller"
                }
            }
        }

        _controllerState.value = ControllerState(
            isConnected = count > 0,
            controllerName = if (count > 0) primaryName else "Touch / Mouse Mode",
            controllerCount = count
        )
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshControllers()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshControllers()
    override fun onInputDeviceChanged(deviceId: Int) = refreshControllers()

    /**
     * Map native Android KeyEvent into high-level ControllerAction
     */
    fun handleKeyEvent(event: KeyEvent): ControllerAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null

        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> ControllerAction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ControllerAction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> ControllerAction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerAction.RIGHT

            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> ControllerAction.CONFIRM_A

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_ESCAPE -> ControllerAction.BACK_B

            KeyEvent.KEYCODE_BUTTON_X -> ControllerAction.CUSTOMIZE_X
            KeyEvent.KEYCODE_BUTTON_Y -> ControllerAction.FAVORITE_Y

            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_L2 -> ControllerAction.PREV_TAB_LB

            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_R2 -> ControllerAction.NEXT_TAB_RB

            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU -> ControllerAction.MENU_START

            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE -> ControllerAction.QUICK_SELECT

            else -> null
        } ?: return null

        val isNavigation = action == ControllerAction.UP || action == ControllerAction.DOWN ||
            action == ControllerAction.LEFT || action == ControllerAction.RIGHT
        if (isNavigation) {
            val now = System.currentTimeMillis()
            if (now - lastNavigationTime < navigationThrottleMs) {
                return null
            }
            lastNavigationTime = now
        }
        return action
    }

    /**
     * Handle analog stick (Left Stick / Hat) motion events for navigation.
     *
     * Uses hysteresis (a larger engage threshold than the release threshold) and emits
     * only when the stick moves into a new direction, so one push steps one item.
     */
    fun handleMotionEvent(event: MotionEvent): ControllerAction? {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return null
        }

        val rawX = event.getAxisValue(MotionEvent.AXIS_X)
        val rawY = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val x = if (abs(hatX) > 0.5f) hatX else rawX
        val y = if (abs(hatY) > 0.5f) hatY else rawY
        val absX = abs(x)
        val absY = abs(y)
        val magnitude = maxOf(absX, absY)

        if (magnitude < joystickReleaseZone) {
            // Break hysteresis once the stick is back near neutral.
            activeDirection = null
            return null
        }
        if (magnitude < joystickDeadzone) {
            // Inside the hysteresis band: keep the current direction latched.
            return null
        }
        // One step per deflection. Wait for the stick to return near neutral before
        // another direction can fire, even if the angle drifts while held.
        if (activeDirection != null) return null

        val direction = if (absX >= absY) {
            if (x > 0f) ControllerAction.RIGHT else ControllerAction.LEFT
        } else {
            if (y > 0f) ControllerAction.DOWN else ControllerAction.UP
        }

        val now = System.currentTimeMillis()
        if (now - lastNavigationTime < navigationThrottleMs) return null

        activeDirection = direction
        lastNavigationTime = now
        return direction
    }
}
