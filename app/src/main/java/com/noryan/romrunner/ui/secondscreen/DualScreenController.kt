package com.noryan.romrunner.ui.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Claims a connected second display (e.g. the AYN Thor's own second screen — confirmed on-device
 * to expose it as a real Display with FLAG_PRESENTATION, via `adb shell dumpsys display`) and fills
 * it with [SecondScreenLogo], gated by [isEnabled] (the "Dual-Screen Support" Settings toggle).
 * Modeled on watermelonds-src's own ExternalInfoDisplayController + SecondaryDisplaySelector.
 */
class DualScreenController(
    private val activity: ComponentActivity,
    private val isEnabled: () -> Boolean
) {
    private var presentation: SecondScreenPresentation? = null

    private val displayManager: DisplayManager
        get() = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    fun attach() {
        displayManager.registerDisplayListener(displayListener, null)
        refresh()
    }

    fun detach() {
        displayManager.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        presentation = null
    }

    /** Re-checks [isEnabled] and the connected-display list. Called on every display add/remove,
     *  and must also be called manually whenever the toggle itself changes — Settings lives in the
     *  same single-Activity Compose tree, so there's no Activity-resume event to piggyback on. */
    fun refresh() {
        val currentDisplay = ContextCompat.getDisplayOrDefault(activity)
        val secondDisplay = if (isEnabled()) {
            activity.getSystemService<DisplayManager>()
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                ?.firstOrNull { it.displayId != currentDisplay.displayId && it.name !in ExcludedDisplayNames }
        } else {
            null
        }

        if (secondDisplay == null) {
            presentation?.dismiss()
            presentation = null
            return
        }

        if (presentation?.display?.displayId != secondDisplay.displayId) {
            presentation?.dismiss()
            presentation = SecondScreenPresentation(activity, secondDisplay).also {
                runCatching { it.show() }
            }
        }
    }

    private companion object {
        // Same exclusion list as watermelonds-src's SecondaryDisplaySelector.
        val ExcludedDisplayNames = listOf(
            "HiddenDisplay", // a placeholder display present on some devices
            "WebRTC_ScreenCapture" // used by apps that record the screen (e.g. Discord)
        )
    }
}
