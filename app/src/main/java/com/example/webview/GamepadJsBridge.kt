package com.example.webview

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class GamepadJsBridge {
    // Standard 17 gamepad buttons (W3C standard mapping)
    // 0: A, 1: B, 2: X, 3: Y, 4: LB, 5: RB, 6: LT, 7: RT, 8: Select, 9: Start,
    // 10: LS_Click, 11: RS_Click, 12: D-Up, 13: D-Down, 14: D-Left, 15: D-Right, 16: Guide
    private val buttonStates = FloatArray(17) { 0f }
    // 4 standard axes: 0: LS_X, 1: LS_Y, 2: RS_X, 3: RS_Y
    private val axisStates = FloatArray(4) { 0f }
    private val isConnected = AtomicBoolean(true)

    @JavascriptInterface
    fun getGamepadData(): String {
        val root = JSONObject()
        root.put("connected", isConnected.get())
        root.put("id", "Standard Gamepad (Nexus Controller Bridge)")
        root.put("index", 0)
        root.put("mapping", "standard")
        root.put("timestamp", System.currentTimeMillis())

        val buttons = JSONArray()
        synchronized(buttonStates) {
            for (value in buttonStates) {
                val b = JSONObject()
                b.put("pressed", value > 0.1f)
                b.put("value", value)
                buttons.put(b)
            }
        }
        root.put("buttons", buttons)

        val axes = JSONArray()
        synchronized(axisStates) {
            for (value in axisStates) {
                axes.put(value.toDouble())
            }
        }
        root.put("axes", axes)

        return root.toString()
    }

    fun onNativeKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val value = if (isDown) 1.0f else 0.0f

        val buttonIndex = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> 0
            KeyEvent.KEYCODE_BUTTON_B -> 1
            KeyEvent.KEYCODE_BUTTON_X -> 2
            KeyEvent.KEYCODE_BUTTON_Y -> 3
            KeyEvent.KEYCODE_BUTTON_L1 -> 4
            KeyEvent.KEYCODE_BUTTON_R1 -> 5
            KeyEvent.KEYCODE_BUTTON_L2 -> 6
            KeyEvent.KEYCODE_BUTTON_R2 -> 7
            KeyEvent.KEYCODE_BUTTON_SELECT -> 8
            KeyEvent.KEYCODE_BUTTON_START -> 9
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 10
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 11
            KeyEvent.KEYCODE_DPAD_UP -> 12
            KeyEvent.KEYCODE_DPAD_DOWN -> 13
            KeyEvent.KEYCODE_DPAD_LEFT -> 14
            KeyEvent.KEYCODE_DPAD_RIGHT -> 15
            KeyEvent.KEYCODE_BUTTON_MODE -> 16
            else -> -1
        }

        if (buttonIndex in 0..16) {
            synchronized(buttonStates) {
                buttonStates[buttonIndex] = value
            }
            return true
        }
        return false
    }

    fun onNativeMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return false
        }

        synchronized(axisStates) {
            axisStates[0] = event.getAxisValue(MotionEvent.AXIS_X)
            axisStates[1] = event.getAxisValue(MotionEvent.AXIS_Y)
            axisStates[2] = event.getAxisValue(MotionEvent.AXIS_Z)
            axisStates[3] = event.getAxisValue(MotionEvent.AXIS_RZ)
        }

        // Handle analog triggers
        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        synchronized(buttonStates) {
            if (lt > 0f) buttonStates[6] = lt
            if (rt > 0f) buttonStates[7] = rt
            buttonStates[12] = if (hatY < -0.5f) 1.0f else buttonStates[12]
            buttonStates[13] = if (hatY > 0.5f) 1.0f else buttonStates[13]
            buttonStates[14] = if (hatX < -0.5f) 1.0f else buttonStates[14]
            buttonStates[15] = if (hatX > 0.5f) 1.0f else buttonStates[15]
        }
        return true
    }

    fun updateVirtualButton(index: Int, pressed: Boolean, value: Float = if (pressed) 1.0f else 0.0f) {
        if (index in 0..16) {
            synchronized(buttonStates) {
                buttonStates[index] = value
            }
        }
    }

    fun updateVirtualAxis(axisIndex: Int, value: Float) {
        if (axisIndex in 0..3) {
            synchronized(axisStates) {
                axisStates[axisIndex] = value
            }
        }
    }

    companion object {
        val GAMEPAD_POLYFILL_JS = """
            (function() {
                if (window.__gamepadBridgeInjected) return;
                window.__gamepadBridgeInjected = true;
                console.log("[Nexus] Initializing HTML5 Gamepad Bridge for GeForce NOW...");

                var fakeGamepad = {
                    id: "Xbox 360 Controller (Nexus Virtual Gamepad)",
                    index: 0,
                    connected: true,
                    timestamp: performance.now(),
                    mapping: "standard",
                    axes: [0, 0, 0, 0],
                    buttons: Array.from({length: 17}, function() {
                        return { pressed: false, touched: false, value: 0 };
                    })
                };

                function syncGamepad() {
                    if (window.__androidGamepadBridge && window.__androidGamepadBridge.getGamepadData) {
                        try {
                            var jsonStr = window.__androidGamepadBridge.getGamepadData();
                            var data = JSON.parse(jsonStr);
                            fakeGamepad.connected = data.connected;
                            fakeGamepad.timestamp = performance.now();
                            if (data.axes && data.axes.length >= 4) {
                                fakeGamepad.axes = data.axes;
                            }
                            if (data.buttons && data.buttons.length >= 17) {
                                fakeGamepad.buttons = data.buttons.map(function(b) {
                                    return {
                                        pressed: b.pressed,
                                        touched: b.pressed,
                                        value: b.value
                                    };
                                });
                            }
                        } catch(e) {}
                    }
                    requestAnimationFrame(syncGamepad);
                }
                requestAnimationFrame(syncGamepad);

                var originalGetGamepads = navigator.getGamepads ? navigator.getGamepads.bind(navigator) : null;
                navigator.getGamepads = function() {
                    var nativeList = originalGetGamepads ? originalGetGamepads() : [];
                    if (nativeList && nativeList.length > 0 && nativeList[0]) {
                        return nativeList;
                    }
                    return [fakeGamepad, null, null, null];
                };

                // Dispatch initial gamepadconnected event
                window.setTimeout(function() {
                    var event = new Event("gamepadconnected");
                    event.gamepad = fakeGamepad;
                    window.dispatchEvent(event);
                    console.log("[Nexus] Dispatched gamepadconnected event for GFN");
                }, 800);
            })();
        """.trimIndent()
    }
}
