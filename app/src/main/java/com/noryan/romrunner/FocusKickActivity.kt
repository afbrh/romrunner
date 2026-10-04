package com.noryan.romrunner

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * An invisible activity that exists only to become the top resumed activity on the game's screen for a
 * moment, which makes Android move input focus there. See GameFocus for why this is needed.
 */
class FocusKickActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, FINISH_AFTER_MS)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val FINISH_AFTER_MS = 120L
    }
}
