package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.noryan.romrunner.data.input.ControllerMappingApplier
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.scanner.SafPathUtils
import java.io.File
import org.citra.citra_emu.activities.EmulationActivity
import org.citra.citra_emu.utils.DirectoryInitialization
import org.citra.citra_emu.utils.EmulationMenuSettings
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.model.Game as CitraGame

/**
 * Starts the embedded Azahar core's own EmulationActivity in-process for a 3DS ROM, instead of
 * handing off to a separate installed app. Since this now compiles as part of RomRunner itself
 * (see azahar-src/GAMESHELF_INTEGRATION.md), there's no separate package to check for or launch —
 * and no cross-app SAF permission grant needed, since it's the same app/UID accessing the file.
 */
object AzaharEmbeddedLauncher {

    const val PLATFORM_NAME = "Nintendo 3DS"

    private const val PREFS_NAME = "azahar_embedded_launcher"
    // Bumped from "defaults_applied": now also fixes a bad frame-limiter default in config.ini —
    // installs that already ran under the old key need to reapply once under the new one.
    private const val KEY_DEFAULTS_APPLIED = "defaults_applied_v2"

    /**
     * One-time preferred defaults for this launcher's setup: OpenGL over Vulkan, 4x internal
     * resolution (~1080p upscale from the 3DS's native 400x240/240x400), and the on-screen touch
     * overlay hidden (a physical controller is expected). Written directly into Azahar's own
     * config.ini rather than through its Kotlin Settings model, since that model exists mainly to
     * back its Settings UI and round-trips through the same file anyway — this is simpler and
     * doesn't require the native library already loaded. Applied once (flagged in our own prefs)
     * so later manual changes in Azahar's own settings menu aren't overwritten on every launch.
     */
    private fun applyDefaultSettingsIfNeeded(context: Context, romsRootUri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULTS_APPLIED, false)) return

        val rootRealPath = SafPathUtils.realPathFromDocumentUri(Uri.parse(romsRootUri))
        if (rootRealPath != null) {
            val configFile = File(rootRealPath, "config/config.ini")
            if (configFile.exists()) {
                var text = configFile.readText()
                text = setIniValue(text, "graphics_api", "1") // 1 = OpenGL (2 = Vulkan is the default)
                text = setIniValue(text, "resolution_factor", "4") // ~1080p upscale
                // Azahar's own first-run config generation writes "use_frame_limit = false" and
                // an empty "frame_limit = " here (contradicting its own comment describing On/100
                // as the defaults) — without this, the frame limiter is fully disabled and games
                // run as fast as the hardware allows instead of at their native speed. Confirmed
                // via native-side diagnostic logging: Settings::GetFrameLimit() read back as 0.
                text = setIniValue(text, "use_frame_limit", "true")
                text = setIniValue(text, "frame_limit", "100")
                configFile.writeText(text)
            }
        }

        EmulationMenuSettings.showOverlay = false

        prefs.edit().putBoolean(KEY_DEFAULTS_APPLIED, true).apply()
    }

    private fun setIniValue(iniText: String, key: String, value: String): String {
        val regex = Regex("(?m)^$key\\s*=.*$")
        return if (regex.containsMatchIn(iniText)) {
            regex.replace(iniText, "$key = $value")
        } else {
            iniText.trimEnd('\n') + "\n$key = $value\n"
        }
    }

    /**
     * Azahar normally asks the user (via its own setup screen) to pick a folder for its
     * config/saves/shader cache. RomRunner already holds full read/write SAF permission on the
     * ROMs root folder, so we point Azahar at that same folder instead — the user never sees
     * Azahar's own picker or setup flow. Cheap/idempotent: safe to call before every launch.
     * (This does mean Azahar will create its usual nand/sdmc/config/shader-cache subfolders
     * inside the ROMs root folder — the same thing that happens if a user manually points
     * standalone Azahar at that folder themselves.)
     */
    private fun ensureDirectoryReady(romsRootUri: String) {
        if (DirectoryInitialization.areCitraDirectoriesReady()) return
        PermissionsHandler.setCitraDirectory(romsRootUri)
        DirectoryInitialization.start()
    }

    fun launch(context: Context, game: Game, romsRootUri: String, repository: LibraryRepository) {
        ensureDirectoryReady(romsRootUri)
        applyDefaultSettingsIfNeeded(context, romsRootUri)
        ControllerMappingApplier.applyToAzahar(context, repository.getControllerMapping())

        // Azahar's native loader opens files with plain C++ file I/O, which can't read a
        // content:// Uri directly — it needs a real filesystem path. Only resolvable for folders
        // on the primary shared storage volume (see SafPathUtils); if that fails there's no good
        // fallback, since the native loader has no other way to read it.
        //
        // The "!" prefix matters: TranslateFilePath (common/android_utils.cpp) otherwise treats
        // any path without it as *relative to Azahar's own user directory* and prepends that,
        // producing a nonexistent path. "!" tells it "this is already an absolute native path,
        // use it as-is."
        val realPath = SafPathUtils.realPathFromDocumentUri(Uri.parse(game.fileUri))
        if (realPath == null) {
            Toast.makeText(
                context,
                "Can't locate ${game.title} on disk (only works for folders on internal storage, not SD cards).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val citraGame = CitraGame(
            valid = true,
            title = game.title,
            path = "!$realPath",
            filename = game.fileName
        )
        val intent = Intent(context, EmulationActivity::class.java).apply {
            putExtra("game", citraGame)
        }
        context.startActivity(intent)
    }
}
