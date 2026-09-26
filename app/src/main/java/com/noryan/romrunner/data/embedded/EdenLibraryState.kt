package com.noryan.romrunner.data.embedded

import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform

/**
 * Keeps shared code free of any reference to Eden (the embedded Nintendo Switch core) — real
 * Eden types only exist on the "full" flavor's classpath, not "lite"'s (see
 * romrunner.switchEmbedded in app/build.gradle.kts). [EdenIntegration.rememberState] returns the
 * real implementation on "full" and a no-op on "lite".
 */
interface EdenLibraryState {
    /** Returns true if this state fully handled the launch (caller should not fall through). */
    fun attemptLaunch(platform: Platform, game: Game): Boolean
}
