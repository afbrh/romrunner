package com.noryan.romrunner.data.embedded

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository

/**
 * "full" flavor: the real Eden integration. See EdenEmbeddedLauncher.kt for the core launch/
 * import logic (ported from eden-src/gameshelf-integration-snapshot/) — this file wires that up
 * behind the flavor-agnostic EdenLibraryState seam so shared code (RomRunnerApp, LibraryScreen,
 * BuiltInPlatforms, BiosKeysImporter) never references Eden types directly.
 */
object EdenIntegration {
    val platformName: String = EdenEmbeddedLauncher.PLATFORM_NAME

    fun initialize(app: Application) {
        // No eager init here: Eden's own directory/native setup happens lazily on first touch,
        // inside EdenEmbeddedLauncher.ensureDirectoryReady() — matches the already-verified
        // pattern from the prior integration session rather than introducing an untested eager
        // ordering change relative to Azahar/PrimeHack/Cemu's own initializeForEmbedding calls.
    }

    suspend fun applyLaunchRoutingDefaults(repository: LibraryRepository) {
        // Nintendo Switch no longer routes through an external app: LibraryScreen sends it
        // straight to the embedded Eden core (see EdenEmbeddedLauncher) instead of an external
        // dev.eden.eden_emulator install.
        repository.clearLaunchPackage(EdenEmbeddedLauncher.PLATFORM_NAME)
    }

    /** Used by BiosKeysImporter when it finds a prod.keys file in the user's BIOS/Keys folder. */
    fun importKeysFromUri(context: Context, uri: Uri): Boolean = EdenEmbeddedLauncher.importKeys(context, uri)

    /** Used by BiosKeysImporter when it finds a firmware zip in the user's BIOS/Keys folder. */
    fun importFirmwareFromUri(context: Context, uri: Uri): Boolean = EdenEmbeddedLauncher.importFirmware(context, uri)

    fun rememberState(
        context: Context,
        romsRootUri: String?,
        repository: LibraryRepository,
        markPlayed: (Game) -> Unit
    ): EdenLibraryState =
        object : EdenLibraryState {
            override fun attemptLaunch(platform: Platform, game: Game): Boolean {
                if (platform.name != EdenEmbeddedLauncher.PLATFORM_NAME || !platform.useBuiltIn) return false
                if (!EdenEmbeddedLauncher.isKeysImported(context) || !EdenEmbeddedLauncher.isFirmwareImported(context)) {
                    Toast.makeText(
                        context,
                        "Set your BIOS/Keys folder in Settings first (needs prod.keys and firmware).",
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
                EdenEmbeddedLauncher.launch(context, game, repository)
                markPlayed(game)
                return true
            }
        }
}
