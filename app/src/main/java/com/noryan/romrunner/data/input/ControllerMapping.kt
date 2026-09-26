package com.noryan.romrunner.data.input

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * One of the 18 canonical, position-labeled inputs every embedded core gets mapped to. Face
 * buttons are named by DIAMOND POSITION, not by letter, since Xbox/Switch/PlayStation controllers
 * disagree on which letter sits where.
 */
enum class StandardInput(val label: String, val group: InputGroup) {
    DPAD_UP("D-Pad Up", InputGroup.DPAD),
    DPAD_DOWN("D-Pad Down", InputGroup.DPAD),
    DPAD_LEFT("D-Pad Left", InputGroup.DPAD),
    DPAD_RIGHT("D-Pad Right", InputGroup.DPAD),
    FACE_TOP("Top Button", InputGroup.FACE),
    FACE_BOTTOM("Bottom Button", InputGroup.FACE),
    FACE_LEFT("Left Button", InputGroup.FACE),
    FACE_RIGHT("Right Button", InputGroup.FACE),
    L1("L1", InputGroup.SHOULDER),
    R1("R1", InputGroup.SHOULDER),
    L2("L2", InputGroup.TRIGGER),
    R2("R2", InputGroup.TRIGGER),
    L3("L3 (Left Stick Click)", InputGroup.STICK_CLICK),
    R3("R3 (Right Stick Click)", InputGroup.STICK_CLICK),
    LEFT_STICK("Left Stick", InputGroup.STICK),
    RIGHT_STICK("Right Stick", InputGroup.STICK),
    SELECT("Select", InputGroup.MENU),
    START("Start", InputGroup.MENU),
}

enum class InputGroup(val heading: String) {
    DPAD("D-Pad"),
    FACE("Face Buttons"),
    SHOULDER("Shoulders"),
    TRIGGER("Triggers"),
    STICK_CLICK("Stick Clicks"),
    STICK("Sticks"),
    MENU("Menu"),
}

/** What a non-stick [StandardInput] slot is currently bound to. */
sealed class PhysicalBinding {
    data class Key(val keyCode: Int) : PhysicalBinding()

    /** [axis] is a MotionEvent.AXIS_* constant, captured as a digital press past a threshold
     *  (e.g. an analog trigger used as a button). */
    data class Axis(val axis: Int, val positiveDirection: Boolean) : PhysicalBinding()
}

/** An analog stick binding: which two axes feed it, and whether either is inverted. */
data class StickBinding(
    val xAxis: Int,
    val yAxis: Int,
    val invertX: Boolean = false,
    val invertY: Boolean = false
)

/**
 * The user's full, effective controller mapping. [buttons] covers every [StandardInput] except
 * [StandardInput.LEFT_STICK]/[StandardInput.RIGHT_STICK], which live in [leftStick]/[rightStick]
 * since a stick binding needs an axis pair rather than a single key/axis value.
 */
data class ControllerMapping(
    val buttons: Map<StandardInput, PhysicalBinding>,
    val leftStick: StickBinding,
    val rightStick: StickBinding,
) {
    companion object {
        /**
         * AYN Thor defaults. Verified against [com.noryan.romrunner.data.embedded.PS2EmbeddedLauncher]'s
         * existing pad.map.cross=B / triangle=X / square=Y / circle=A remap (PS2 button positions:
         * cross=bottom, triangle=top, square=left, circle=right) — i.e. on the AYN Thor's physical
         * pad, bottom=BUTTON_B, top=BUTTON_X, left=BUTTON_Y, right=BUTTON_A — plus the standard
         * Android SOURCE_GAMEPAD/SOURCE_JOYSTICK convention (left stick=X/Y, right stick=Z/RZ)
         * already assumed independently by every embedded core.
         */
        val AYN_THOR_DEFAULT = ControllerMapping(
            buttons = mapOf(
                StandardInput.DPAD_UP to PhysicalBinding.Key(KeyEvent.KEYCODE_DPAD_UP),
                StandardInput.DPAD_DOWN to PhysicalBinding.Key(KeyEvent.KEYCODE_DPAD_DOWN),
                StandardInput.DPAD_LEFT to PhysicalBinding.Key(KeyEvent.KEYCODE_DPAD_LEFT),
                StandardInput.DPAD_RIGHT to PhysicalBinding.Key(KeyEvent.KEYCODE_DPAD_RIGHT),
                StandardInput.FACE_BOTTOM to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_B),
                StandardInput.FACE_TOP to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_X),
                StandardInput.FACE_LEFT to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_Y),
                StandardInput.FACE_RIGHT to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_A),
                StandardInput.L1 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_L1),
                StandardInput.R1 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_R1),
                StandardInput.L2 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_L2),
                StandardInput.R2 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_R2),
                StandardInput.L3 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                StandardInput.R3 to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                StandardInput.SELECT to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
                StandardInput.START to PhysicalBinding.Key(KeyEvent.KEYCODE_BUTTON_START),
            ),
            leftStick = StickBinding(xAxis = MotionEvent.AXIS_X, yAxis = MotionEvent.AXIS_Y),
            rightStick = StickBinding(xAxis = MotionEvent.AXIS_Z, yAxis = MotionEvent.AXIS_RZ),
        )
    }
}
