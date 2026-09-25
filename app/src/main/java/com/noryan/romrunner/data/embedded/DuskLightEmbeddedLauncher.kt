package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.scanner.SafPathUtils
import dev.twilitrealm.dusk.DuskActivity
import java.io.File

/**
 * Starts the embedded DuskLight core's own DuskActivity in-process for exactly one title —
 * Twilight Princess — instead of the generic GameCube/Wii (PrimeHack) embedded core, and instead
 * of the old external-app override this replaces (see GameLaunchOverrides.kt's removed entry).
 * DuskLight isn't a general-purpose GameCube emulator (it's a from-scratch, decompilation-based
 * native reimplementation of this one title), so it's routed by title match, checked in
 * LibraryScreen.attemptLaunch() BEFORE the PrimeHack branch.
 *
 * libmain.so is bundled as a plain asset (assets/dusklight/libmain.so), not packaged via jniLibs —
 * confirmed (via dusklight-src's own BorealisAndroid.cmake, which hardcodes
 * `set_target_properties(target PROPERTIES OUTPUT_NAME main)`) to share its exact output filename
 * with PrimeHack's own Dolphin-core libmain.so. Letting both into AGP's jniLibs merge would
 * silently let one core's .so satisfy the other's System.loadLibrary("main") call with completely
 * wrong native code — see the patch in dusklight-src's borealis-application.gradle that keeps this
 * module's own build from ever wiring libmain.so into its jniLibs output.
 *
 * [ensureNativeLibInstalled] copies the asset to a fixed app-private path
 * (<dataDir>/dusklight/libmain.so) on first use, BEFORE starting DuskActivity — DuskActivity.java
 * is patched (see dusklight-src/GAMESHELF_INTEGRATION.md) to System.load() that exact path
 * explicitly in its own onCreate(), instead of the standard Borealis/SDL
 * getLibraries()+System.loadLibrary("main") mechanism, which would otherwise search
 * nativeLibraryDir and silently resolve to PrimeHack's own libmain.so there.
 */
object DuskLightEmbeddedLauncher {

    private const val TITLE_KEYWORD = "twilight princess"
    private const val ROM_ARG_FLAG = "--dvd"

    fun matches(gameTitle: String): Boolean = gameTitle.lowercase().contains(TITLE_KEYWORD)

    fun launch(context: Context, game: Game) {
        ensureNativeLibInstalled(context)

        val realPath = SafPathUtils.realPathFromDocumentUri(Uri.parse(game.fileUri))
        if (realPath == null) {
            Toast.makeText(
                context,
                "Can't locate ${game.title} on disk (only works for folders on internal storage, not SD cards).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(context, DuskActivity::class.java).apply {
            putExtra("dusk_argv", arrayOf("--backend", "vulkan", ROM_ARG_FLAG, realPath))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun ensureNativeLibInstalled(context: Context) {
        val destination = File(context.applicationInfo.dataDir, "dusklight/libmain.so")
        if (destination.exists()) return
        destination.parentFile?.mkdirs()
        context.assets.open("dusklight/libmain.so").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        destination.setExecutable(true)
    }
}
