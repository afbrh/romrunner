package com.noryan.romrunner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.noryan.romrunner.ui.navigation.RomRunnerNavHost
import com.noryan.romrunner.ui.secondscreen.DualScreenController
import com.noryan.romrunner.ui.theme.RomRunnerTheme

class MainActivity : ComponentActivity() {
    private lateinit var dualScreenController: DualScreenController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate() — it applies Theme.RomRunner.Splash's brand splash,
        // then hands off to postSplashScreenTheme (Theme.RomRunner) once the first frame draws.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideStatusBar()
        // A controller (D-pad/joystick) doesn't touch the screen, so the system's normal
        // inactivity timeout still fires during play/browsing and blanks the display — once
        // asleep, the window loses input focus and every key/motion event is dropped before it
        // ever reaches the app, which looks exactly like "the controller stopped working."
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val repository = (application as RomRunnerApp).repository
        dualScreenController = DualScreenController(this) { repository.getDualScreenSupportEnabled() }
        setContent {
            RomRunnerTheme {
                RomRunnerNavHost(repository = repository, onDualScreenSupportChanged = dualScreenController::refresh)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dualScreenController.attach()
    }

    override fun onStop() {
        dualScreenController.detach()
        super.onStop()
    }

    // Keeps the status bar hidden for RomRunner's own library/settings UI. Swipe-to-reveal
    // (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) rather than a hard lock, so a pull-down from the
    // top edge still works temporarily (notifications, quick settings) without permanently
    // undoing this — it re-hides once dismissed.
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-apply when focus returns (e.g. after a transient swipe-reveal, or coming back from
        // another activity/app-switcher) since the system can leave the bar shown otherwise.
        if (hasFocus) hideStatusBar()
    }

    // Both the physical D-pad and the analog thumbstick on the AYN Thor already arrive as real
    // KEYCODE_DPAD_* KeyEvents (confirmed via on-device logging — the stick's own AXIS_X/AXIS_Y
    // motion is accompanied by a genuine KeyEvent from the OS's own gamepad-to-key mapping, not
    // just raw axis motion), which Compose's default focus system already moves between
    // rows/toggles on its own. An earlier version of this file additionally synthesized its own
    // DPAD KeyEvents from stick motion on the assumption the stick only sent axis motion — that
    // was wrong for this hardware and caused every stick nudge to double-navigate (one real
    // KeyEvent plus one synthetic one), which is why navigation looked broken/erratic. Removed;
    // no motion-event handling is needed here at all.
}
