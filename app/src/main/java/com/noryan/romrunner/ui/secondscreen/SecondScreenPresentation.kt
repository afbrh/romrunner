package com.noryan.romrunner.ui.secondscreen

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noryan.romrunner.ui.theme.RomRunnerTheme

/**
 * Hosts [SecondScreenLogo] on a connected second display. A Presentation's window doesn't inherit
 * the hosting Activity's ViewTree owners automatically (unlike a normal View in the Activity's own
 * window), so they're set explicitly here — modeled on watermelonds-src's own
 * ExternalInfoPresentation, which solves this exact problem for a ComposeView hosted the same way.
 *
 * The owners are this presentation's own, always RESUMED while it's showing, rather than the
 * Activity's: the logo has to keep animating (sliding into the bottom edge) after RomRunner has
 * stopped because a game took over, and Compose pauses its frame clock when its lifecycle owner stops.
 */
class SecondScreenPresentation(
    activity: ComponentActivity,
    display: Display
) : Presentation(activity, display) {

    private val owner = PresentationOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never steal touches or controller focus from whatever is underneath (a game's own second screen).
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                RomRunnerTheme(darkTheme = true) {
                    val gameRunning by SecondScreenState.gameRunning.collectAsState()
                    val title by SecondScreenState.title.collectAsState()
                    val emulator by SecondScreenState.emulator.collectAsState()
                    SecondScreenLogo(gameRunning = gameRunning, gameTitle = title, emulatorName = emulator)
                }
            }
        }
        setContentView(composeView)
        setOnDismissListener { owner.destroy() }
    }

    private class PresentationOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
