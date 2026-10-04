package com.noryan.romrunner.data.launch

import android.view.InputDevice

/** The handheld's own built-in gamepad (or, failing that, any connected physical one). */
object Gamepad {

    fun primary(): InputDevice? {
        val pads = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter {
            !it.isVirtual && (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        }
        return pads.firstOrNull { it.name.equals("Odin Controller", ignoreCase = true) || it.name.contains("Retroid", ignoreCase = true) }
            ?: pads.firstOrNull()
    }
}
