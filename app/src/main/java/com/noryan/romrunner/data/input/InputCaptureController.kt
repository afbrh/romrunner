package com.noryan.romrunner.data.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Bridges the Controller Mapping screen's "press the input now" capture flow to
 * [com.noryan.romrunner.MainActivity]'s [android.app.Activity.dispatchKeyEvent]/
 * [android.app.Activity.dispatchGenericMotionEvent] overrides — a plain singleton (not a Compose
 * state holder) so the Activity can reach it with no Compose coupling. When nothing is pending,
 * every call here is a no-op returning false immediately, so normal input dispatch (including
 * LibraryScreen's own hardcoded L1/R1 tab-cycle onKeyEvent) is completely undisturbed.
 */
object InputCaptureController {
    private const val STICK_AXIS_THRESHOLD = 0.5f

    private val _pending = MutableStateFlow<StandardInput?>(null)
    val pending: StateFlow<StandardInput?> = _pending

    /** Emits when a non-stick slot is captured as a digital keypress. */
    private val _keyResult = MutableSharedFlow<PhysicalBinding.Key>(extraBufferCapacity = 1)
    val keyResult: SharedFlow<PhysicalBinding.Key> = _keyResult

    /** Emits when a non-stick slot (e.g. an L2/R2 trigger with no digital keycode of its own) is
     *  captured as an analog axis crossing [STICK_AXIS_THRESHOLD]. */
    private val _axisResult = MutableSharedFlow<PhysicalBinding.Axis>(extraBufferCapacity = 1)
    val axisResult: SharedFlow<PhysicalBinding.Axis> = _axisResult

    /** Emits when a LEFT_STICK/RIGHT_STICK slot is captured. */
    private val _stickResult = MutableSharedFlow<StickBinding>(extraBufferCapacity = 1)
    val stickResult: SharedFlow<StickBinding> = _stickResult

    /** Tracks the biggest-moving axis across successive motion events during a stick capture,
     *  since a single event rarely has just one axis at exactly zero. */
    private var bestAxis: Int? = null
    private var bestMagnitude = 0f

    fun beginCapture(slot: StandardInput) {
        bestAxis = null
        bestMagnitude = 0f
        _pending.value = slot
    }

    fun cancelCapture() {
        _pending.value = null
    }

    /** Called from MainActivity.dispatchKeyEvent. Returns true if the event was consumed (i.e. a
     *  capture is in progress) so the caller should NOT pass it on to super.dispatchKeyEvent. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val slot = _pending.value ?: return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false // lets a capture dialog be dismissed
        if (slot.group == InputGroup.STICK) return true // sticks are captured via motion, not keys
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        _pending.value = null
        _keyResult.tryEmit(PhysicalBinding.Key(event.keyCode))
        return true
    }

    /** Called from MainActivity.dispatchGenericMotionEvent. Returns true if consumed. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val slot = _pending.value ?: return false
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val device = event.device ?: return true

        if (slot.group != InputGroup.STICK) {
            val axis = device.motionRanges
                .filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .map { it.axis }
                .firstOrNull { abs(event.getAxisValue(it)) > STICK_AXIS_THRESHOLD }
                ?: return true
            _pending.value = null
            _axisResult.tryEmit(PhysicalBinding.Axis(axis = axis, positiveDirection = event.getAxisValue(axis) > 0))
            return true
        }

        // Stick capture: track whichever axis is furthest from center across events, then pair it
        // with its usual X/Y partner (X<->Y, Z<->RZ, RX<->RY) once it clearly dominates.
        for (range in device.motionRanges) {
            if (range.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) continue
            val value = abs(event.getAxisValue(range.axis))
            if (value > bestMagnitude) {
                bestMagnitude = value
                bestAxis = range.axis
            }
        }
        val axis = bestAxis
        if (axis != null && bestMagnitude > STICK_AXIS_THRESHOLD) {
            val (xAxis, yAxis) = axisPairFor(axis)
            _pending.value = null
            _stickResult.tryEmit(StickBinding(xAxis = xAxis, yAxis = yAxis))
        }
        return true
    }

    private fun axisPairFor(axis: Int): Pair<Int, Int> = when (axis) {
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y -> MotionEvent.AXIS_X to MotionEvent.AXIS_Y
        MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ -> MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ
        MotionEvent.AXIS_RX, MotionEvent.AXIS_RY -> MotionEvent.AXIS_RX to MotionEvent.AXIS_RY
        else -> axis to axis
    }
}
