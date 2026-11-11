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


    // Analog thumbstick & D-pad state
    private var activeDirection: ControllerAction? = null
    private var stickEngagedTime = 0L
    private var lastRepeatTime = 0L

    // Deadzones: 0.35f engage deadzone is responsive and standard across Android gamepads;
    // release threshold at 0.20f gives clean hysteresis without requiring stick centering.
    private val joystickDeadzone = 0.35f
    private val joystickReleaseZone = 0.20f

    // Repeat timing: initial step is immediate; holding stick triggers first repeat after
    // initialDelayMs (300ms) and subsequent repeats every repeatIntervalMs (110ms).
    private val initialDelayMs = 300L
    private val repeatIntervalMs = 110L

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
            // Support D-pad auto-repeat smoothly using repeat interval
            if (event.repeatCount > 0) {
                if (now - lastRepeatTime < repeatIntervalMs) {
                    return null
                }
                lastRepeatTime = now
            } else {
                lastRepeatTime = now
            }
        }
        return action
    }

    /**
     * Handle analog stick (Left Stick / Hat) motion events with console-grade navigation:
     * - Immediate advance on first tilt
     * - Smooth, continuous repeat when held past 300ms at 110ms cadence
     * - Radial deadzone and clean hysteresis to avoid stuck or dropped inputs
     */
    fun handleMotionEvent(event: MotionEvent): ControllerAction? {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return null
        }

        // Try primary stick axes (AXIS_X, AXIS_Y) or fallback to D-pad hat axes
        val rawX = event.getAxisValue(MotionEvent.AXIS_X)
        val rawY = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val x = if (abs(hatX) > 0.2f) hatX else rawX
        val y = if (abs(hatY) > 0.2f) hatY else rawY
        val absX = abs(x)
        val absY = abs(y)
        val magnitude = kotlin.math.hypot(absX, absY)

        val now = System.currentTimeMillis()

        // Stick returned near center: clear direction latch
        if (magnitude < joystickReleaseZone) {
            activeDirection = null
            stickEngagedTime = 0L
            return null
        }

        // Inside deadzone / neutral boundary
        if (magnitude < joystickDeadzone) {
            return null
        }

        // Determine dominant direction
        val direction = if (absX >= absY) {
            if (x > 0f) ControllerAction.RIGHT else ControllerAction.LEFT
        } else {
            if (y > 0f) ControllerAction.DOWN else ControllerAction.UP
        }

        // New deflection direction: fire immediately
        if (direction != activeDirection) {
            activeDirection = direction
            stickEngagedTime = now
            lastRepeatTime = now
            return direction
        }

        // Same direction held: fire auto-repeat if held beyond initial delay
        val heldDuration = now - stickEngagedTime
        if (heldDuration >= initialDelayMs && (now - lastRepeatTime) >= repeatIntervalMs) {
            lastRepeatTime = now
            return direction
        }

        return null
    }
}
