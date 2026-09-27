package com.noryan.romrunner.ui.secondscreen

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noryan.romrunner.ui.theme.RomRunnerTheme

/**
 * Hosts [SecondScreenLogo] on a connected second display. A Presentation's window doesn't inherit
 * the hosting Activity's ViewTree owners automatically (unlike a normal View in the Activity's own
 * window), so they're set explicitly here — modeled on watermelonds-src's own
 * ExternalInfoPresentation, which solves this exact problem for a ComposeView hosted the same way.
 */
class SecondScreenPresentation(
    private val activity: ComponentActivity,
    display: Display
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                RomRunnerTheme(darkTheme = true) {
                    SecondScreenLogo()
                }
            }
        }
        setContentView(composeView)
    }
}
