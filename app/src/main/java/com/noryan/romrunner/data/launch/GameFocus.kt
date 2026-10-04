package com.noryan.romrunner.data.launch

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.noryan.romrunner.FocusKickActivity

/**
 * Gets controller input back to a game that was just launched.
 *
 * The AYN Thor has two screens, and Android sends gamepad keys only to the display that currently has
 * input focus. A moment after a game opens on one screen, the Thor's own bottom-screen launcher
 * (`SecondaryDisplayLauncher`) takes that focus back, so Back/Select (and every other button) goes to
 * the launcher instead of the emulator until the screen is tapped. A normal app can't set the focused
 * display directly, but focus follows the top resumed activity, so after the launcher has done this we
 * briefly open an invisible activity on the game's screen. Doing so is allowed even though RomRunner is
 * no longer in front, because Android lets an app start activities for a few seconds after it last
 * showed one.
 */
object GameFocus {
    private const val TAG = "GameFocus"

    /** When to re-assert focus, in ms after the launch. Each pass pauses the game for a fraction of a second. */
    private val KICK_DELAYS_MS = longArrayOf(1500L)

    private val handler = Handler(Looper.getMainLooper())

    /** Call right after starting a game's activity from [context]. */
    fun reassertAfterLaunch(context: Context) {
        val app = context.applicationContext
        val displayId = (context as? Activity)?.display?.displayId ?: Display.DEFAULT_DISPLAY
        handler.removeCallbacksAndMessages(null)
        KICK_DELAYS_MS.forEach { delay ->
            handler.postDelayed({ kick(app, displayId) }, delay)
        }
    }

    private fun kick(context: Context, displayId: Int) {
        try {
            val intent = Intent(context, FocusKickActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.w(TAG, "couldn't re-assert focus: ${e.message}")
        }
    }
}
