package com.noryan.romrunner.data.embedded

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "full" flavor: the real Eden integration. See EdenEmbeddedLauncher.kt for the core launch/
 * import logic (ported from eden-src/gameshelf-integration-snapshot/) — this file wires that up
 * behind the flavor-agnostic EdenLibraryState seam so shared code (RomRunnerApp, LibraryScreen,
 * BuiltInPlatforms) never references Eden types directly.
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

    /** Which one-time Eden import step is still needed before a game can launch — checked in
     *  order, keys before firmware, since Eden's own native installer expects keys to exist first. */
    private sealed class PendingImport(val game: Game) {
        class Keys(game: Game) : PendingImport(game)
        class Firmware(game: Game) : PendingImport(game)
    }

    @Composable
    fun rememberState(context: Context, romsRootUri: String?, markPlayed: (Game) -> Unit): EdenLibraryState {
        val scope = rememberCoroutineScope()
        var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

        fun launchOrPrompt(game: Game) {
            if (!EdenEmbeddedLauncher.isKeysImported(context)) {
                // Before bothering the user, look for a file already named prod.keys — a fixed,
                // predictable filename (unlike firmware's arbitrarily-named zip) that's often just
                // sitting on the device already from a previous Switch-emulation setup.
                scope.launch {
                    val candidates = withContext(Dispatchers.IO) {
                        EdenEmbeddedLauncher.findKeysCandidates(context, romsRootUri)
                    }
                    val onlyMatch = candidates.singleOrNull()
                    val imported = onlyMatch != null &&
                        withContext(Dispatchers.IO) { onlyMatch.import(context) }
                    if (imported) {
                        launchOrPrompt(game)
                    } else {
                        // None found, more than one found, or the only match failed to import —
                        // fall back to asking the user for the file.
                        pendingImport = PendingImport.Keys(game)
                    }
                }
                return
            }
            if (!EdenEmbeddedLauncher.isFirmwareImported(context)) {
                pendingImport = PendingImport.Firmware(game)
                return
            }
            EdenEmbeddedLauncher.launch(context, game)
            markPlayed(game)
        }

        val keysPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            val game = (pendingImport as? PendingImport.Keys)?.game
            pendingImport = null
            if (uri == null || game == null) return@rememberLauncherForActivityResult
            if (EdenEmbeddedLauncher.importKeys(context, uri)) {
                launchOrPrompt(game)
            } else {
                Toast.makeText(context, "Couldn't import that keys file.", Toast.LENGTH_LONG).show()
            }
        }
        val firmwarePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            val game = (pendingImport as? PendingImport.Firmware)?.game
            pendingImport = null
            if (uri == null || game == null) return@rememberLauncherForActivityResult
            if (EdenEmbeddedLauncher.importFirmware(context, uri)) {
                launchOrPrompt(game)
            } else {
                Toast.makeText(context, "Couldn't import that firmware file.", Toast.LENGTH_LONG).show()
            }
        }

        return object : EdenLibraryState {
            override fun attemptLaunch(platform: Platform, game: Game): Boolean {
                if (platform.name != EdenEmbeddedLauncher.PLATFORM_NAME || !platform.useBuiltIn) return false
                launchOrPrompt(game)
                return true
            }

            @Composable
            override fun Dialogs() {
                when (val pending = pendingImport) {
                    is PendingImport.Keys -> ImportEdenFileDialog(
                        title = "Import Switch Keys",
                        body = "Nintendo Switch emulation needs prod.keys dumped from your own " +
                            "console. Choose it once — RomRunner keeps a private copy and won't " +
                            "ask again.",
                        onDismiss = { pendingImport = null },
                        onChooseFile = { keysPicker.launch(arrayOf("*/*")) }
                    )
                    is PendingImport.Firmware -> ImportEdenFileDialog(
                        title = "Import Switch Firmware",
                        body = "Nintendo Switch emulation also needs firmware dumped from your " +
                            "own console, as a single zip of its system files. Choose it once — " +
                            "RomRunner keeps a private copy and won't ask again.",
                        onDismiss = { pendingImport = null },
                        onChooseFile = { firmwarePicker.launch(arrayOf("*/*")) }
                    )
                    null -> Unit
                }
            }
        }
    }
}

/** Generic version of LibraryScreen's ImportBiosDialog, for Eden's two required imports (keys,
 *  then firmware). */
@Composable
private fun ImportEdenFileDialog(title: String, body: String, onDismiss: () -> Unit, onChooseFile: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onChooseFile) { Text("Choose File") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
