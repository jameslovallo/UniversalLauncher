package com.example.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only controller navigation Nexus supports: the left and right bumpers switch
 * between the Apps and Games screens. Everything else is touch/mouse driven.
 */
enum class ControllerAction {
    PREV_TAB_LB,
    NEXT_TAB_RB
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

    /** Maps the controller bumpers to tab actions; other buttons are left to the system. */
    fun handleKeyEvent(event: KeyEvent): ControllerAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        // Holding a bumper should not keep re-selecting the tab.
        if (event.repeatCount > 0) return null

        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerAction.PREV_TAB_LB
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerAction.NEXT_TAB_RB
            else -> null
        }
    }
}
