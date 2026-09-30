package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import com.noryan.romrunner.data.input.ControllerMapping
import com.noryan.romrunner.data.input.ControllerMappingApplier
import com.noryan.romrunner.data.model.Game
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File

/**
 * Starts the embedded PrimeHack (Dolphin) core's own EmulationActivity in-process for a
 * GameCube/Wii ROM, instead of handing off to a separate installed app.
 *
 * Unlike Azahar, no real-path resolution is needed: Dolphin's own file layer (see
 * Common/FileUtil.cpp's `content://` branches, and AndroidCommon.cpp/ContentHandler.java) already
 * reads directly from a SAF content:// Uri, so [game]'s raw fileUri is passed straight through —
 * closer to how PS2EmbeddedLauncher hands ARMSX2 a raw Uri than to Azahar's path translation.
 *
 * PrimeHack's own onboarding (game-folder scan, controller setup wizard) is never shown: native
 * directory setup happens via DolphinApplication.initializeForEmbedding (called once from
 * RomRunnerApp, since RomRunnerApp extends a DIFFERENT embedded core's Application class and
 * PrimeHack's own onCreate() never runs — see that file); [applyDefaultsIfNeeded] writes the
 * Wiimote control scheme, hides the on-screen touch overlay, and enables compile-shaders-before-
 * starting directly into the global Config ini files, so every GameCube/Wii title gets the same
 * setup with no per-game step — unlike PS2/Azahar, there's no mandatory external BIOS/keys file
 * to import first.
 */
object PrimeHackEmbeddedLauncher {
    const val PLATFORM_NAME = "GameCube / Wii"

    private const val PREFS_NAME = "primehack_embedded_launcher"
    // Bumped from "defaults_applied_v2": added ShaderCompilationMode = AsynchronousUberShaders,
    // so installs that already ran under the old key need to reapply once under the new one.
    private const val KEY_DEFAULTS_APPLIED = "defaults_applied_v3"

    fun launch(context: Context, game: Game, romsRootUri: String, controllerMapping: ControllerMapping) {
        applyDefaultsIfNeeded(context)
        applyControllerMapping(context, controllerMapping)
        applyPerGameCheatDefaults(context, game)

        val intent = Intent(context, EmulationActivity::class.java)
        intent.putExtra(EmulationActivity.EXTRA_SELECTED_GAMES, arrayOf(game.fileUri))
        intent.putExtra(EmulationActivity.EXTRA_RIIVOLUTION, false)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun applyDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULTS_APPLIED, false)) return
        if (!DirectoryInitialization.areDolphinDirectoriesReady()) return

        val configDir = File(DirectoryInitialization.getUserDirectory(), "Config")
        configDir.mkdirs()

        // Wiimote controls: the AYN Thor-tuned "odin.ini" profile, bundled as an asset (not
        // hardcoded here) so it's easy to inspect/replace on its own. Its [Profile] header is
        // stripped and the remaining key=value lines become the body of [Wiimote1], replaced
        // wholesale (not a key-by-key patch) so our bindings fully own that section — every other
        // section (Wiimote2/3/4, BalanceBoard) native init already wrote is left untouched.
        val odinProfileBody = context.assets.open("odin.ini").bufferedReader().use { it.readText() }
            .lineSequence()
            .filterNot { it.trim() == "[Profile]" }
            .joinToString("\n")
            .trim()
        replaceSection(File(configDir, "WiimoteNew.ini"), "Wiimote1", odinProfileBody)

        // Hide the on-screen touch overlay by default — a physical controller is expected.
        setIniValue(File(configDir, "Dolphin.ini"), "Android", "ShowInputOverlay", "False")

        // Compile shaders before starting a game, instead of stuttering mid-gameplay while
        // shaders JIT-compile on first use. This only covers shaders already in the on-disk
        // cache from a previous session, though — a title's first-ever visit to some area/effect
        // (e.g. Metroid Prime's morph ball transition) still compiles cold. ShaderCompilationMode
        // = 2 (AsynchronousUberShaders) covers that case too: a generic fallback shader renders
        // immediately while the real one compiles on a background thread, instead of blocking
        // synchronously — this is what actually stopped a real device-observed crash, where that
        // cold-compile burst spiked memory/CPU hard enough to get the whole process OOM-killed.
        setIniValue(File(configDir, "GFX.ini"), "Settings", "WaitForShadersBeforeStarting", "True")
        setIniValue(File(configDir, "GFX.ini"), "Settings", "ShaderCompilationMode", "2")

        prefs.edit().putBoolean(KEY_DEFAULTS_APPLIED, true).apply()
    }

    /** Applies the user's current Controller Mapping to real GameCube-disc titles (GCPadNew.ini's
     *  [GCPad1] section) on every launch — see ControllerMappingApplier.applyToPrimeHack's own
     *  doc comment for why Wii titles' WiimoteNew.ini is deliberately left untouched. */
    private fun applyControllerMapping(context: Context, controllerMapping: ControllerMapping) {
        if (!DirectoryInitialization.areDolphinDirectoriesReady()) return
        val configDir = File(DirectoryInitialization.getUserDirectory(), "Config")
        configDir.mkdirs()
        ControllerMappingApplier.applyToPrimeHack(configDir, controllerMapping)
    }

    /**
     * Enables specific bundled Gecko codes by default for one specific title, by title-keyword
     * match — same pattern GameLaunchOverrides.kt already uses elsewhere in this project. Writes
     * a user-level `GameSettings/<gameId>.ini` override containing just a `[Gecko_Enabled]`
     * listing; Dolphin's own GeckoCodeConfig::LoadCodes reads the `[Gecko]` code catalog from the
     * bundled `Sys/GameSettings/<gameId>.ini` and this override together (global ini first, then
     * local), matching enabled-list entries against whichever codes were already catalogued — so
     * this file doesn't need to repeat the actual code content, just the two names to enable
     * (confirmed against GMSE01's bundled ini: `$Widescreen [gamemasterplc]` and
     * `$60FPS [gamemasterplc]`, matched here by the part before `[` per
     * CheatCodes.h's ReadEnabledOrDisabled, i.e. without the "[gamemasterplc]" suffix).
     *
     * `GameSettings` is a sibling of `Config` directly under Dolphin's user directory, NOT nested
     * inside `Config` — confirmed against ConfigManager.cpp's SConfig::LoadLocalGameIni(), which
     * reads from `File::GetUserPath(D_GAMESETTINGS_IDX)` (== `<UserDir>/GameSettings/`), and
     * against the real on-device layout (a `GameSettings/` folder already exists there, holding
     * PCSX2's own per-game inis). An earlier version of this function wrote to
     * `Config/GameSettings/` instead, which Dolphin never reads — this didn't actually enable
     * anything.
     *
     * Also sets the global `EnableCheats` switch (`Dolphin.ini`'s `[Core]` section) to `True` —
     * confirmed against Config/MainSettings.cpp (`MAIN_ENABLE_CHEATS` defaults to `false`) and
     * GeckoCode.cpp (every actual code-application path is gated on `Config::AreCheatsEnabled()`
     * first). Without this, a per-game `[Gecko_Enabled]` listing is silently inert regardless of
     * being correctly configured — confirmed on-device as the reason neither code took effect
     * even after the path fix above.
     *
     * The Widescreen Gecko code only patches the game's own internal camera/HUD projection math
     * for a 16:9 view — it doesn't change what shape buffer Dolphin actually renders into, which
     * is a separate video setting. Without also forcing that, the output stays letterboxed at
     * 4:3 regardless of the code being active (confirmed on-device: still boxed after the two
     * fixes above). `[Video_Settings] AspectRatio = 1` in this same per-game ini sets it
     * (confirmed against ConfigLoaders/GameConfigLoader.cpp's section-name mapping — `Video_Settings`
     * in a per-game ini maps to GFX.ini's own `[Settings]`, matching GFX_ASPECT_RATIO's key — and
     * VideoConfig.h's AspectMode enum, where `1` = ForceWide).
     */
    private fun applyPerGameCheatDefaults(context: Context, game: Game) {
        if (!DirectoryInitialization.areDolphinDirectoriesReady()) return
        if (!game.title.lowercase().contains("super mario sunshine")) return

        val configDir = File(DirectoryInitialization.getUserDirectory(), "Config")
        configDir.mkdirs()
        setIniValue(File(configDir, "Dolphin.ini"), "Core", "EnableCheats", "True")

        val gameSettingsDir = File(DirectoryInitialization.getUserDirectory(), "GameSettings")
        gameSettingsDir.mkdirs()
        val gameIni = File(gameSettingsDir, "GMSE01.ini")
        enableGeckoCodes(gameIni, listOf("Widescreen", "60FPS"))
        setIniValue(gameIni, "Video_Settings", "AspectRatio", "1")
    }

    /** Adds `$name` lines to `[Gecko_Enabled]` in [file] for each of [codeNames], creating the
     *  file/section as needed. Idempotent — a name already listed is left as-is, not duplicated. */
    private fun enableGeckoCodes(file: File, codeNames: List<String>) {
        val lines = (if (file.exists()) file.readText() else "").lines().toMutableList()
        val sectionHeader = "[Gecko_Enabled]"
        var sectionStart = lines.indexOfFirst { it.trim() == sectionHeader }
        if (sectionStart == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add(sectionHeader)
            sectionStart = lines.size - 1
        }
        var sectionEnd = lines.size
        for (i in sectionStart + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                sectionEnd = i
                break
            }
        }
        val existingNames = (sectionStart + 1 until sectionEnd).mapNotNull { i ->
            lines[i].trim().removePrefix("$").takeIf { lines[i].trim().startsWith("$") }
        }
        var insertAt = sectionEnd
        for (name in codeNames) {
            if (name !in existingNames) {
                lines.add(insertAt, "$$name")
                insertAt++
            }
        }
        file.writeText(lines.joinToString("\n"))
    }

    /** Replaces (or adds) a whole `[sectionName]` section in [file] with [body] wholesale. */
    private fun replaceSection(file: File, sectionName: String, body: String) {
        val existing = if (file.exists()) file.readText() else ""
        val withoutSection = existing.replace(Regex("""\[$sectionName\][^\[]*"""), "")
        file.writeText(withoutSection.trimEnd() + "\n\n[$sectionName]\n$body\n")
    }

    /**
     * Sets a single `key = value` inside `[sectionName]` of [file], creating the file/section as
     * needed. Every other key in that section, and every other section, is left untouched — unlike
     * [replaceSection], since these files (Dolphin.ini, GFX.ini) already carry native-init-written
     * settings we don't want to clobber.
     */
    private fun setIniValue(file: File, sectionName: String, key: String, value: String) {
        val lines = (if (file.exists()) file.readText() else "").lines().toMutableList()
        val sectionHeader = "[$sectionName]"
        val sectionStart = lines.indexOfFirst { it.trim() == sectionHeader }
        if (sectionStart == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add(sectionHeader)
            lines.add("$key = $value")
        } else {
            var sectionEnd = lines.size
            for (i in sectionStart + 1 until lines.size) {
                if (lines[i].trim().startsWith("[")) {
                    sectionEnd = i
                    break
                }
            }
            val keyIndex = (sectionStart + 1 until sectionEnd).firstOrNull {
                val trimmed = lines[it].trim()
                trimmed.startsWith("$key ") || trimmed.startsWith("$key=")
            }
            if (keyIndex != null) {
                lines[keyIndex] = "$key = $value"
            } else {
                lines.add(sectionEnd, "$key = $value")
            }
        }
        file.writeText(lines.joinToString("\n"))
    }
}
