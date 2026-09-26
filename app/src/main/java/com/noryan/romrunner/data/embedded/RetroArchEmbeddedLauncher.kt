package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import com.noryan.romrunner.data.input.ControllerMappingApplier
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.repository.LibraryRepository
import java.io.File

/**
 * Starts the embedded RetroArch core's own RetroActivityFuture in-process for a ROM, instead of
 * handing off to a separate installed app. Unlike the other three embedded cores (one standalone
 * app per platform), RetroArch is a single multi-system frontend that dynamically loads a
 * libretro core `.so` per platform — so this one launcher covers six platforms, picking the right
 * bundled core per [Game]'s platform.
 *
 * The five cores are bundled as prebuilt assets (assets/retroarch_cores, five .so files, downloaded from
 * buildbot.libretro.com's official nightly Android builds rather than built from source — see the
 * embedding plan) and copied into RetroArch's own cores directory
 * (`<dataDir>/cores/<name>_libretro_android.so`, confirmed via CoreSideloadActivity.java and
 * RetroActivityCommon.getCorePath() in retroarch-src) on first use per core. RetroActivityFuture
 * is then launched directly with the `ROM`/`LIBRETRO` intent extras it already supports for
 * external frontends (confirmed via RetroArch's own CoreSideloadActivity, which builds the exact
 * same Intent shape after a manual core sideload) — this skips RetroArch's own XMB/RGUI menu
 * entirely, going straight into the game.
 */
object RetroArchEmbeddedLauncher {
    private const val RETRO_ACTIVITY_FUTURE_CLASS =
        "com.retroarch.browser.retroactivity.RetroActivityFuture"

    private val CORE_FOR_PLATFORM = mapOf(
        "Nintendo 64" to "mupen64plus_next_gles3_libretro_android.so",
        "SNES" to "snes9x_libretro_android.so",
        "NES" to "nestopia_libretro_android.so",
        "GameBoy (Color + Advance)" to "mgba_libretro_android.so",
        "PlayStation" to "swanstation_libretro_android.so",
    )

    val PLATFORM_NAMES: Set<String> get() = CORE_FOR_PLATFORM.keys

    fun launch(context: Context, game: Game, platformName: String, repository: LibraryRepository) {
        val coreFileName = CORE_FOR_PLATFORM[platformName] ?: return
        val corePath = ensureCoreInstalled(context, coreFileName)
        val configPath = ensureDefaultConfigInstalled(context)
        ControllerMappingApplier.applyToRetroArch(File(configPath), repository.getControllerMapping())

        val intent = Intent().apply {
            setClassName(context, RETRO_ACTIVITY_FUTURE_CLASS)
            putExtra("ROM", game.fileUri)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Copies [coreFileName] from bundled assets into RetroArch's own cores directory the first
     *  time it's needed, and returns its absolute path there. */
    private fun ensureCoreInstalled(context: Context, coreFileName: String): String {
        val coresDir = File(context.applicationInfo.dataDir, "cores").apply { mkdirs() }
        val destination = File(coresDir, coreFileName)
        if (!destination.exists()) {
            context.assets.open("retroarch_cores/$coreFileName").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destination.absolutePath
    }

    /** Copies RomRunner's default RetroArch config (gamepad menu-toggle/quit binding — see
     *  assets/retroarch_defaults.cfg) into app-external storage once, so it's still there for
     *  RetroArch itself to read/save into (config_save_on_exit) on every later launch. */
    private fun ensureDefaultConfigInstalled(context: Context): String {
        val destination = File(context.getExternalFilesDir(null), "retroarch_romrunner.cfg")
        if (!destination.exists()) {
            context.assets.open("retroarch_defaults.cfg").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destination.absolutePath
    }
}
