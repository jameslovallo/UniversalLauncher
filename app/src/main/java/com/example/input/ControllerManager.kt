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
 * Controller buttons Nexus handles itself:
 * - The left and right bumpers switch between the Apps and Games screens.
 * - X opens the context menu for the focused app or game.
 * - Back (Select/View) opens the Add modal, Start (Menu) opens Settings.
 * Directional navigation and A (click) are left to Compose's focus system.
 */
enum class ControllerAction {
    PREV_TAB_LB,
    NEXT_TAB_RB,
    OPEN_CONTEXT_MENU_X,
    OPEN_ADD_MODAL_BACK,
    OPEN_SETTINGS_START
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

    /** Maps the controller buttons Nexus handles; other buttons are left to the system. */
    fun handleKeyEvent(event: KeyEvent): ControllerAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        // Holding a button should not keep re-triggering the same action.
        if (event.repeatCount > 0) return null

        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerAction.PREV_TAB_LB
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerAction.NEXT_TAB_RB
            KeyEvent.KEYCODE_BUTTON_X -> ControllerAction.OPEN_CONTEXT_MENU_X
            KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerAction.OPEN_ADD_MODAL_BACK
            KeyEvent.KEYCODE_BUTTON_START -> ControllerAction.OPEN_SETTINGS_START
            else -> null
        }
    }
}
