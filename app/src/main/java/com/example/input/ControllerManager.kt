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

    private var lastJoystickNavTime = 0L
    private val joystickThrottleMs = 220L
    private val joystickDeadzone = 0.45f

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

        return when (event.keyCode) {
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
        }
    }

    /**
     * Handle analog stick (Left Stick / Hat) motion events for navigation
     */
    fun handleMotionEvent(event: MotionEvent): ControllerAction? {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return null
        }

        val now = System.currentTimeMillis()
        if (now - lastJoystickNavTime < joystickThrottleMs) return null

        val x = event.getAxisValue(MotionEvent.AXIS_X).let { if (abs(it) > joystickDeadzone) it else 0f }
        val y = event.getAxisValue(MotionEvent.AXIS_Y).let { if (abs(it) > joystickDeadzone) it else 0f }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val navAction = when {
            hatY < -0.5f || y < -joystickDeadzone -> ControllerAction.UP
            hatY > 0.5f || y > joystickDeadzone -> ControllerAction.DOWN
            hatX < -0.5f || x < -joystickDeadzone -> ControllerAction.LEFT
            hatX > 0.5f || x > joystickDeadzone -> ControllerAction.RIGHT
            else -> null
        }

        if (navAction != null) {
            lastJoystickNavTime = now
        }
        return navAction
    }
}
