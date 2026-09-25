package com.noryan.romrunner.data.embedded

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository

/**
 * "lite" flavor: Eden is not on this build's classpath at all (see romrunner.switchEmbedded in
 * app/build.gradle.kts). Every member is a no-op so behavior stays identical to RomRunner before
 * Eden existed — Nintendo Switch titles fall through to the external-app flow
 * (DefaultPlatforms.kt's launchPackage = "dev.eden.eden_emulator") exactly as before.
 */
object EdenIntegration {
    val platformName: String? = null

    fun initialize(app: Application) {
    }

    suspend fun applyLaunchRoutingDefaults(repository: LibraryRepository) {
    }

    @Composable
    fun rememberState(context: Context, romsRootUri: String?, markPlayed: (Game) -> Unit): EdenLibraryState =
        NoOpEdenLibraryState
}

private object NoOpEdenLibraryState : EdenLibraryState {
    override fun attemptLaunch(platform: Platform, game: Game): Boolean = false

    @Composable
    override fun Dialogs() {
    }
}
